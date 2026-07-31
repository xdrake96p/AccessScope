package dev.accessscope.cli

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class ResultFetcher(private val deviceSerial: String) {
    private val gson = Gson()
    private val adb = Adb(deviceSerial)

    fun fetchStatus(): JsonObject = queryJson("content://${AppConstants.RESULTS_AUTHORITY}/status")

    fun fetchLatestSession(packageName: String): JsonObject =
        queryJson("content://${AppConstants.RESULTS_AUTHORITY}/latest?package=$packageName")

    fun fetchSession(sessionId: String): JsonObject =
        queryJson("content://${AppConstants.RESULTS_AUTHORITY}/session/$sessionId")

    fun fetchResults(
        packageName: String?,
        wait: Boolean,
        timeoutMinutes: Long,
    ): String {
        val pkg = packageName ?: resolvePackageFromStatus()
        if (wait) {
            waitForScanComplete(timeoutMinutes)
        }
        val session = fetchLatestSession(pkg)
        if (session.has("error")) {
            return gson.toJson(session)
        }
        return gson.toJson(buildScanResultResponse(session))
    }

    private fun waitForScanComplete(timeoutMinutes: Long) {
        val deadline = System.currentTimeMillis() + timeoutMinutes * 60_000
        var wasScanning = false
        while (System.currentTimeMillis() < deadline) {
            val status = fetchStatus()
            val scanning = status.get("isScanning")?.asBoolean ?: false
            if (scanning) {
                wasScanning = true
            } else if (wasScanning) {
                Thread.sleep(1500)
                return
            }
            Thread.sleep(2000)
        }
        if (wasScanning) {
            error("Timed out waiting for scan to complete after ${timeoutMinutes}m")
        }
    }

    private fun resolvePackageFromStatus(): String {
        val status = fetchStatus()
        val packages = status.getAsJsonArray("selectedPackages")
        if (packages != null && packages.size() > 0) {
            return packages[0].asString
        }
        error("Package name required. Pass --package or start a scan with a target app selected.")
    }

    private fun queryJson(uri: String): JsonObject {
        val output = runCatching {
            adb.run("shell", "content", "query", "--uri", uri).output
        }.getOrElse { error ->
            val fallback = fallbackReadLatest(uri)
            if (fallback != null) return fallback
            throw error
        }
        val jsonText = parseContentQueryOutput(output)
        return JsonParser.parseString(jsonText).asJsonObject
    }

    private fun parseContentQueryOutput(output: String): String {
        val rowPrefix = "result="
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val value = when {
                    line.contains(rowPrefix) -> line.substringAfter(rowPrefix)
                    line.startsWith("Row:") && line.contains("result=") ->
                        line.substringAfter("result=")
                    else -> null
                }
                if (!value.isNullOrBlank()) return value
            }
        return output
    }

    private fun fallbackReadLatest(uri: String): JsonObject? {
        if (!uri.contains("/latest")) return null
        val packageName = Regex("package=([^&\\s]+)").find(uri)?.groupValues?.get(1) ?: return null
        val sanitized = packageName.replace(Regex("""[^\w.-]"""), "_")
        val indexPath = "/data/data/${AppConstants.PACKAGE_NAME}/files/scan_history/by_package/$sanitized.json"
        val indexJson = adb.runOrNull("exec-out", "run-as", AppConstants.PACKAGE_NAME, "cat", indexPath)
            ?: return null
        val arr = JsonParser.parseString(indexJson).asJsonArray
        if (arr.size() == 0) return null
        val sessionId = arr[arr.size() - 1].asString
        val sessionPath = "/data/data/${AppConstants.PACKAGE_NAME}/files/scan_history/sessions/$sessionId.json"
        val sessionJson = adb.runOrNull("exec-out", "run-as", AppConstants.PACKAGE_NAME, "cat", sessionPath)
            ?: return null
        return JsonParser.parseString(sessionJson).asJsonObject
    }

    private fun buildScanResultResponse(session: JsonObject): JsonObject {
        val violations = session.getAsJsonArray("violations") ?: JsonParser.parseString("[]").asJsonArray
        val summary = JsonObject().apply {
            addProperty("critical", countSeverity(violations, "CRITICAL"))
            addProperty("serious", countSeverity(violations, "SERIOUS"))
            addProperty("moderate", countSeverity(violations, "MODERATE"))
            addProperty("minor", countSeverity(violations, "MINOR"))
        }
        return JsonObject().apply {
            addProperty("sessionId", session.get("id")?.asString)
            addProperty("score", session.get("score")?.asInt ?: 0)
            add("targetPackages", session.getAsJsonArray("targetPackages"))
            add("violations", violations)
            add("summary", summary)
            addProperty(
                "completedAt",
                java.time.Instant.ofEpochMilli(session.get("completedAtMs")?.asLong ?: 0).toString(),
            )
            session.get("pdfPath")?.asString?.let { addProperty("pdfPath", it) }
            // Prima mancavano nel bridge IDE: senza, il report del plugin non poteva mostrare
            // i finding TalkBack né il percorso di navigazione, molto più povero di quello on-device.
            session.getAsJsonArray("screenReaderFindings")?.let { add("screenReaderFindings", it) }
            session.getAsJsonArray("visitedScreens")?.let { add("visitedScreens", it) }
        }
    }

    private fun countSeverity(violations: com.google.gson.JsonArray, severityName: String): Int {
        var count = 0
        for (element in violations) {
            val severity = element.asJsonObject.get("severity")?.asString
                ?: inferSeverity(element.asJsonObject.get("type")?.asString.orEmpty())
            if (severity.equals(severityName, ignoreCase = true)) count++
        }
        return count
    }

    private fun inferSeverity(violationType: String): String = when {
        violationType.contains("MISSING_LABEL", ignoreCase = true) -> "CRITICAL"
        violationType.contains("CONTRAST", ignoreCase = true) -> "SERIOUS"
        violationType.contains("TOUCH", ignoreCase = true) -> "SERIOUS"
        violationType.contains("HEADING", ignoreCase = true) -> "MODERATE"
        else -> "MODERATE"
    }
}

object SetupCheck {
    fun run(deviceSerial: String): String {
        val gson = Gson()
        DeviceGuard.requireReadyDevice(deviceSerial)
        // Un plugin IDE datato non deve mai far mentire questo check sullo stato reale del
        // device: prima, un manifest irraggiungibile o un gate di versione facevano tornare
        // tutto `false` a prescindere da cosa fosse davvero acceso sul device. Ora è solo un
        // avviso informativo allegato al risultato reale.
        val versionWarning = runCatching {
            PluginVersionChecker.compatibilityWarning(ReleaseClient().fetchLatestManifest())
        }.getOrNull()
        val status = runCatching { ResultFetcher(deviceSerial).fetchStatus() }
            .getOrElse { failure ->
                val installed = ApkInstaller(deviceSerial).isInstalled()
                val checks = JsonObject().apply {
                    addProperty("accessibilityEnabled", false)
                    addProperty("overlayEnabled", false)
                    addProperty("appInstalled", installed)
                    addProperty("ready", false)
                    addProperty(
                        "hint",
                        if (!installed) {
                            "Run Install / Update to install AccessScope on the device."
                        } else {
                            "AccessScope is installed but not responding. Reinstall or open the app once on the device."
                        },
                    )
                    addProperty("error", failure.message ?: "Unable to read AccessScope status")
                    versionWarning?.let { addProperty("versionWarning", it) }
                }
                return gson.toJson(checks)
            }

        val checks = JsonObject().apply {
            addProperty("accessibilityEnabled", status.get("accessibilityEnabled")?.asBoolean == true)
            addProperty("overlayEnabled", status.get("overlayEnabled")?.asBoolean == true)
            addProperty("appInstalled", status.get("versionCode")?.asLong ?: 0 > 0)
            addProperty("ready", false)
        }
        val ready = checks.get("accessibilityEnabled").asBoolean &&
            checks.get("overlayEnabled").asBoolean &&
            checks.get("appInstalled").asBoolean
        checks.addProperty("ready", ready)
        if (!checks.get("appInstalled").asBoolean) {
            checks.addProperty("hint", "Run Install / Update to install AccessScope on the device.")
        } else if (!checks.get("accessibilityEnabled").asBoolean) {
            checks.addProperty("hint", "Enable AccessScope in Settings > Accessibility.")
        } else if (!checks.get("overlayEnabled").asBoolean) {
            checks.addProperty("hint", "Grant overlay permission in Settings > Apps > AccessScope.")
        }
        versionWarning?.let { checks.addProperty("versionWarning", it) }
        return gson.toJson(checks)
    }
}
