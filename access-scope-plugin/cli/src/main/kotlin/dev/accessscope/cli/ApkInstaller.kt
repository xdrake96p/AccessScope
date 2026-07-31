package dev.accessscope.cli

import java.io.File

object AppConstants {
    const val PACKAGE_NAME = "dev.accessscope.scanner"
    const val MAIN_ACTIVITY = "dev.accessscope.scanner/.MainActivity"
    const val RESULTS_AUTHORITY = "dev.accessscope.scanner.results"
}

class ApkInstaller(
    private val deviceSerial: String,
    private val releaseClient: ReleaseClient = ReleaseClient(),
) {
    private val adb = Adb(deviceSerial)

    fun isInstalled(): Boolean = getInstalledVersionCode() != null

    fun getInstalledVersionCode(): Int? {
        val output = adb.runOrNull("shell", "dumpsys", "package", AppConstants.PACKAGE_NAME) ?: return null
        val match = Regex("""versionCode=(\d+)""").find(output) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun uninstall(): Boolean {
        val result = adb.runResult("uninstall", AppConstants.PACKAGE_NAME)
        return result.exitCode == 0 || result.output.contains("Success", ignoreCase = true)
    }

    fun installLatest(force: Boolean = false): InstallResult {
        DeviceGuard.requireReadyDevice(deviceSerial)
        val manifest = releaseClient.fetchLatestManifest()
        val versionWarning = PluginVersionChecker.compatibilityWarning(manifest)
        val installed = getInstalledVersionCode()
        if (!force && installed != null && installed >= manifest.versionCode) {
            return InstallResult(
                installed = true,
                updated = false,
                versionCode = installed,
                versionName = manifest.versionName,
                message = withVersionWarning(
                    "AccessScope already up to date (v${manifest.versionName}, code $installed)",
                    versionWarning,
                ),
            )
        }

        val apk = releaseClient.downloadApk(manifest)
        if (force && installed != null) {
            uninstall()
        }
        val attempt = installApkRecovering(apk)
        return InstallResult(
            installed = true,
            updated = installed == null || installed < manifest.versionCode || attempt.reinstalled,
            versionCode = manifest.versionCode,
            versionName = manifest.versionName,
            message = withVersionWarning(attempt.message, versionWarning),
            reinstalled = attempt.reinstalled,
        )
    }

    private fun withVersionWarning(message: String, versionWarning: String?): String =
        if (versionWarning == null) message else "$versionWarning\n$message"

    private fun installApkRecovering(apk: File): InstallAttempt {
        val first = adb.runResult("install", "-r", apk.absolutePath)
        if (first.exitCode == 0) {
            return InstallAttempt(
                reinstalled = false,
                message = "Installed AccessScope ${apk.name}",
            )
        }

        val output = first.output
        when {
            InstallErrors.isSignatureMismatch(output) || InstallErrors.isVersionDowngrade(output) -> {
                uninstall()
                val second = adb.runResult("install", apk.absolutePath)
                if (second.exitCode == 0) {
                    return InstallAttempt(
                        reinstalled = true,
                        message = "Removed previous AccessScope build with incompatible signature and installed " +
                            "${apk.name}. Re-enable accessibility and overlay permissions if prompted.",
                    )
                }
                error(InstallErrors.userMessage(second.output, hadToUninstall = true))
            }
            InstallErrors.isAlreadyInstalled(output) -> {
                val retry = adb.runResult("install", "-r", "-d", apk.absolutePath)
                if (retry.exitCode == 0) {
                    return InstallAttempt(
                        reinstalled = false,
                        message = "Updated AccessScope ${apk.name}",
                    )
                }
                error(InstallErrors.userMessage(retry.output))
            }
            else -> error(InstallErrors.userMessage(output))
        }
    }
}

data class InstallResult(
    val installed: Boolean,
    val updated: Boolean,
    val versionCode: Int,
    val versionName: String,
    val message: String,
    val reinstalled: Boolean = false,
)

private data class InstallAttempt(
    val reinstalled: Boolean,
    val message: String,
)

object AppLauncher {
    fun launch(deviceSerial: String) {
        DeviceGuard.requireReadyDevice(deviceSerial)
        val adb = Adb(deviceSerial)
        if (!ApkInstaller(deviceSerial).isInstalled()) {
            error("AccessScope is not installed on this device. Run Install / Update first.")
        }
        adb.run(
            "shell", "am", "start",
            "-n", AppConstants.MAIN_ACTIVITY,
        )
    }
}

object DeviceGuard {
    fun requireReadyDevice(deviceSerial: String) {
        val device = DeviceManager.listDevices()
            .firstOrNull { it.serial == deviceSerial }
            ?: error("Device $deviceSerial not found. Refresh devices and select a connected device.")
        when (device.state) {
            "device" -> return
            "unauthorized" -> error(
                "Device ${device.model} is unauthorized. Accept the USB debugging prompt on the device.",
            )
            "offline" -> error("Device ${device.model} is offline. Reconnect USB or restart adb.")
            else -> error("Device ${device.model} is not ready (state: ${device.state}).")
        }
    }
}
