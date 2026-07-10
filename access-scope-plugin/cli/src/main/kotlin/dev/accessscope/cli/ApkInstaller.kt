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
    fun getInstalledVersionCode(): Int? {
        val adb = Adb(deviceSerial)
        val output = adb.runOrNull("shell", "dumpsys", "package", AppConstants.PACKAGE_NAME) ?: return null
        val match = Regex("""versionCode=(\d+)""").find(output) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun installLatest(force: Boolean = false): InstallResult {
        val manifest = releaseClient.fetchLatestManifest()
        val installed = getInstalledVersionCode()
        if (!force && installed != null && installed >= manifest.versionCode) {
            return InstallResult(
                installed = true,
                updated = false,
                versionCode = installed,
                versionName = manifest.versionName,
                message = "AccessScope already up to date (v${manifest.versionName}, code $installed)",
            )
        }
        val apk = releaseClient.downloadApk(manifest)
        val adb = Adb(deviceSerial)
        adb.run("install", "-r", apk.absolutePath)
        return InstallResult(
            installed = true,
            updated = installed == null || installed < manifest.versionCode,
            versionCode = manifest.versionCode,
            versionName = manifest.versionName,
            message = "Installed AccessScope ${manifest.versionName} (code ${manifest.versionCode})",
        )
    }
}

data class InstallResult(
    val installed: Boolean,
    val updated: Boolean,
    val versionCode: Int,
    val versionName: String,
    val message: String,
)

object AppLauncher {
    fun launch(deviceSerial: String) {
        val adb = Adb(deviceSerial)
        adb.run(
            "shell", "am", "start",
            "-n", AppConstants.MAIN_ACTIVITY,
        )
    }
}
