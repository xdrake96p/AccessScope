/**
 * Persistenza JSON della cronologia scansioni (max 20 sessioni per app).
 */
package dev.accessscope.scanner.util

import android.content.Context
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.EvidenceKind
import dev.accessscope.scanner.data.ScreenProtectionReason
import dev.accessscope.scanner.data.VisitedScreen
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Store file-based per archiviare e recuperare sessioni di scansione completate.
 *
 * @param context Contesto Android; usa [Context.getApplicationContext] e [Context.filesDir].
 */
class ScanHistoryStore(context: Context) {

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "scan_history")
    private val sessionsDir = File(rootDir, "sessions")
    private val byPackageDir = File(rootDir, "by_package")

    init {
        sessionsDir.mkdirs()
        byPackageDir.mkdirs()
    }

    /**
     * Archivia una sessione e la associa a ciascun package in [ArchivedScanSession.targetPackages].
     *
     * Mantiene al massimo [MAX_SESSIONS_PER_PACKAGE] sessioni per package (FIFO).
     *
     * @param session Sessione da persistere.
     */
    fun archive(session: ArchivedScanSession) {
        writeSession(session)
        session.targetPackages.forEach { pkg ->
            appendToPackageIndex(pkg, session.id)
        }
    }

    /**
     * Aggiorna il percorso PDF di una sessione già archiviata.
     *
     * @param sessionId ID della sessione.
     * @param pdfPath Percorso del file PDF generato.
     */
    fun updateSessionPdfPath(sessionId: String, pdfPath: String) {
        val session = getSession(sessionId) ?: return
        writeSession(session.copy(pdfPath = pdfPath))
    }

    /**
     * Restituisce l'ultima sessione archiviata per un package.
     *
     * @param packageName Package Android.
     * @return Sessione più recente o `null`.
     */
    fun getLatest(packageName: String): ArchivedScanSession? {
        val ids = readPackageIndex(packageName)
        val latestId = ids.lastOrNull() ?: return null
        return getSession(latestId)
    }

    /**
     * Restituisce la penultima sessione archiviata per un package.
     *
     * @param packageName Package Android.
     * @return Seconda sessione più recente o `null`.
     */
    fun getPrevious(packageName: String): ArchivedScanSession? {
        val ids = readPackageIndex(packageName)
        if (ids.size < 2) return null
        return getSession(ids[ids.size - 2])
    }

    /**
     * Elenco cronologico delle sessioni per package (più recente per ultima).
     *
     * @param packageName Package Android.
     * @param limit Numero massimo di sessioni da restituire.
     */
    fun getHistory(packageName: String, limit: Int = MAX_SESSIONS_PER_PACKAGE): List<ArchivedScanSession> {
        val ids = readPackageIndex(packageName).takeLast(limit)
        return ids.mapNotNull { getSession(it) }
    }

    /**
     * Carica una sessione per ID.
     *
     * @param sessionId Identificatore univoco della sessione.
     */
    fun getSession(sessionId: String): ArchivedScanSession? {
        val file = sessionFile(sessionId)
        if (!file.exists()) return null
        return runCatching { parseSession(JSONObject(file.readText())) }.getOrNull()
    }

    /** Tutti gli ID sessione ancora presenti su disco (per retention evidenze). */
    fun allSessionIds(): Set<String> =
        sessionsDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()

    /**
     * Costruisce una sessione archiviata dallo snapshot corrente prima dello stop.
     */
    fun buildArchivedSession(
        targetPackages: Set<String>,
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
        uniqueScreens: Int,
        scanAnalyses: Int,
        scanScopeLabel: String,
        score: Int,
        pdfPath: String? = null,
        sessionId: String? = null,
        visitedScreens: List<VisitedScreen> = emptyList(),
    ): ArchivedScanSession {
        val keys = violations.map { it.dedupeKey }.toSet()
        return ArchivedScanSession(
            id = sessionId ?: UUID.randomUUID().toString(),
            completedAtMs = System.currentTimeMillis(),
            targetPackages = targetPackages,
            violations = violations,
            screenReaderFindings = screenReaderFindings,
            uniqueScreens = uniqueScreens,
            scanAnalyses = scanAnalyses,
            scanScopeLabel = scanScopeLabel,
            score = score,
            pdfPath = pdfPath,
            violationKeys = keys,
            visitedScreens = visitedScreens,
        )
    }

    private fun appendToPackageIndex(packageName: String, sessionId: String) {
        val ids = readPackageIndex(packageName).toMutableList()
        ids.remove(sessionId)
        ids.add(sessionId)
        while (ids.size > MAX_SESSIONS_PER_PACKAGE) {
            val removed = ids.removeAt(0)
            if (!isSessionReferenced(removed, excludingPackage = packageName)) {
                sessionFile(removed).delete()
            }
        }
        writePackageIndex(packageName, ids)
    }

    private fun packageIndexFile(packageName: String) =
        File(byPackageDir, "${sanitize(packageName)}.json")

    private fun sessionFile(sessionId: String) = File(sessionsDir, "$sessionId.json")

    private fun sanitize(name: String) = name.replace(Regex("""[^\w.-]"""), "_")

    private fun isSessionReferenced(sessionId: String, excludingPackage: String? = null): Boolean {
        return byPackageDir.listFiles().orEmpty().any { file ->
            if (excludingPackage != null && file == packageIndexFile(excludingPackage)) return@any false
            readPackageIndexFromFile(file).contains(sessionId)
        }
    }

    private fun readPackageIndexFromFile(file: File): List<String> {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }

    private fun readPackageIndex(packageName: String): List<String> =
        readPackageIndexFromFile(packageIndexFile(packageName))

    private fun writePackageIndex(packageName: String, ids: List<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        packageIndexFile(packageName).writeText(arr.toString())
    }

    private fun writeSession(session: ArchivedScanSession) {
        sessionFile(session.id).writeText(serializeSession(session).toString())
    }

    /** Serializza una sessione archiviata in JSON per export/provider IDE. */
    fun sessionToJson(session: ArchivedScanSession): JSONObject = serializeSession(session)

    private fun serializeSession(session: ArchivedScanSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("completedAtMs", session.completedAtMs)
        put("targetPackages", JSONArray(session.targetPackages.toList()))
        put("violations", JSONArray(session.violations.map { serializeViolation(it) }))
        put("screenReaderFindings", JSONArray(session.screenReaderFindings.map { serializeFinding(it) }))
        put("uniqueScreens", session.uniqueScreens)
        put("scanAnalyses", session.scanAnalyses)
        put("scanScopeLabel", session.scanScopeLabel)
        put("score", session.score)
        put("pdfPath", session.pdfPath)
        put("violationKeys", JSONArray(session.violationKeys.toList()))
        put("visitedScreens", JSONArray(session.visitedScreens.map { serializeVisitedScreen(it) }))
    }

    private fun parseSession(json: JSONObject): ArchivedScanSession {
        val violationsArr = json.getJSONArray("violations")
        val findingsArr = json.getJSONArray("screenReaderFindings")
        val packagesArr = json.getJSONArray("targetPackages")
        val keysArr = json.getJSONArray("violationKeys")
        val visitedArr = json.optJSONArray("visitedScreens")
        return ArchivedScanSession(
            id = json.getString("id"),
            completedAtMs = json.getLong("completedAtMs"),
            targetPackages = buildSet {
                for (i in 0 until packagesArr.length()) add(packagesArr.getString(i))
            },
            violations = buildList {
                for (i in 0 until violationsArr.length()) add(parseViolation(violationsArr.getJSONObject(i)))
            },
            screenReaderFindings = buildList {
                for (i in 0 until findingsArr.length()) add(parseFinding(findingsArr.getJSONObject(i)))
            },
            uniqueScreens = json.getInt("uniqueScreens"),
            scanAnalyses = json.getInt("scanAnalyses"),
            scanScopeLabel = json.getString("scanScopeLabel"),
            score = json.getInt("score"),
            pdfPath = json.optString("pdfPath").takeIf { it.isNotBlank() },
            violationKeys = buildSet {
                for (i in 0 until keysArr.length()) add(keysArr.getString(i))
            },
            visitedScreens = if (visitedArr != null) {
                buildList {
                    for (i in 0 until visitedArr.length()) {
                        add(parseVisitedScreen(visitedArr.getJSONObject(i)))
                    }
                }
            } else {
                emptyList()
            },
        )
    }

    private fun serializeVisitedScreen(screen: VisitedScreen): JSONObject = JSONObject().apply {
        put("fingerprint", screen.fingerprint)
        put("title", screen.title)
        put("visitIndex", screen.visitIndex)
        put("protectionReason", screen.protectionReason.name)
    }

    private fun parseVisitedScreen(json: JSONObject): VisitedScreen = VisitedScreen(
        fingerprint = json.getString("fingerprint"),
        title = json.getString("title"),
        visitIndex = json.getInt("visitIndex"),
        protectionReason = json.optString("protectionReason").takeIf { it.isNotBlank() }
            ?.let { runCatching { ScreenProtectionReason.valueOf(it) }.getOrNull() }
            ?: ScreenProtectionReason.NONE,
    )

    private fun serializeViolation(v: AccessibilityViolation): JSONObject = JSONObject().apply {
        put("type", v.type.name)
        put("severity", v.type.severity.name)
        put("viewClassName", v.viewClassName)
        put("screenTitle", v.screenTitle)
        put("packageName", v.packageName)
        put("details", v.details)
        put("viewId", v.viewId)
        put("bounds", v.bounds)
        put("sectionTitle", v.sectionTitle)
        put("confidence", v.confidence.toDouble())
        put("timestampMs", v.timestampMs)
        put("screenFingerprint", v.screenFingerprint)
        put("elementLabel", v.elementLabel)
        put("measuredValue", v.measuredValue)
        put("requiredValue", v.requiredValue)
        put("remediation", v.remediation)
        put("boundsLeft", v.boundsLeft)
        put("boundsTop", v.boundsTop)
        put("boundsRight", v.boundsRight)
        put("boundsBottom", v.boundsBottom)
        put("screenEvidenceId", v.screenEvidenceId)
        put("evidenceImagePath", v.evidenceImagePath)
        put("foregroundColorHex", v.foregroundColorHex)
        put("backgroundColorHex", v.backgroundColorHex)
        put("evidenceKind", v.evidenceKind.name)
    }

    private fun parseViolation(json: JSONObject): AccessibilityViolation = AccessibilityViolation(
        type = ViolationType.valueOf(json.getString("type")),
        viewClassName = json.getString("viewClassName"),
        screenTitle = json.getString("screenTitle"),
        packageName = json.getString("packageName"),
        details = json.getString("details"),
        viewId = json.optString("viewId").takeIf { it.isNotBlank() },
        bounds = json.optString("bounds").takeIf { it.isNotBlank() },
        sectionTitle = json.optString("sectionTitle").takeIf { it.isNotBlank() },
        confidence = json.optDouble("confidence", 1.0).toFloat(),
        timestampMs = json.optLong("timestampMs", 0L),
        screenFingerprint = json.optString("screenFingerprint").takeIf { it.isNotBlank() },
        elementLabel = json.optString("elementLabel").takeIf { it.isNotBlank() },
        measuredValue = json.optString("measuredValue").takeIf { it.isNotBlank() },
        requiredValue = json.optString("requiredValue").takeIf { it.isNotBlank() },
        remediation = json.optString("remediation").takeIf { it.isNotBlank() },
        boundsLeft = json.optInt("boundsLeft").takeIf { json.has("boundsLeft") },
        boundsTop = json.optInt("boundsTop").takeIf { json.has("boundsTop") },
        boundsRight = json.optInt("boundsRight").takeIf { json.has("boundsRight") },
        boundsBottom = json.optInt("boundsBottom").takeIf { json.has("boundsBottom") },
        screenEvidenceId = json.optString("screenEvidenceId").takeIf { it.isNotBlank() },
        evidenceImagePath = json.optString("evidenceImagePath").takeIf { it.isNotBlank() },
        foregroundColorHex = json.optString("foregroundColorHex").takeIf { it.isNotBlank() },
        backgroundColorHex = json.optString("backgroundColorHex").takeIf { it.isNotBlank() },
        evidenceKind = json.optString("evidenceKind").takeIf { it.isNotBlank() }
            ?.let { runCatching { EvidenceKind.valueOf(it) }.getOrNull() }
            ?: EvidenceKind.SCREENSHOT,
    )

    private fun serializeFinding(f: ScreenReaderFinding): JSONObject = JSONObject().apply {
        put("packageName", f.packageName)
        put("screenTitle", f.screenTitle)
        put("nodeClassName", f.nodeClassName)
        put("announcedText", f.announcedText)
        put("issue", f.issue)
        put("viewId", f.viewId)
        put("sectionTitle", f.sectionTitle)
    }

    private fun parseFinding(json: JSONObject): ScreenReaderFinding = ScreenReaderFinding(
        packageName = json.getString("packageName"),
        screenTitle = json.getString("screenTitle"),
        nodeClassName = json.getString("nodeClassName"),
        announcedText = json.optString("announcedText").takeIf { it.isNotBlank() },
        issue = json.getString("issue"),
        viewId = json.optString("viewId").takeIf { it.isNotBlank() },
        sectionTitle = json.optString("sectionTitle").takeIf { it.isNotBlank() },
    )

    companion object {
        /** Numero massimo di sessioni conservate per ciascun package. */
        const val MAX_SESSIONS_PER_PACKAGE = 20
    }
}
