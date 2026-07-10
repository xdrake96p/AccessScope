package dev.accessscope.cli

object InstallErrors {
    fun isSignatureMismatch(output: String): Boolean =
        output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
            output.contains("signatures do not match", ignoreCase = true) ||
            output.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES", ignoreCase = true)

    fun isVersionDowngrade(output: String): Boolean =
        output.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true)

    fun isInsufficientStorage(output: String): Boolean =
        output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true)

    fun isInstallRestricted(output: String): Boolean =
        output.contains("INSTALL_FAILED_USER_RESTRICTED", ignoreCase = true) ||
            output.contains("INSTALL_FAILED_BLOCKED", ignoreCase = true)

    fun isAlreadyInstalled(output: String): Boolean =
        output.contains("INSTALL_FAILED_ALREADY_EXISTS", ignoreCase = true)

    fun userMessage(output: String, hadToUninstall: Boolean = false): String {
        val detail = output.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() && !it.startsWith("Performing") }
            .orEmpty()

        return when {
            isSignatureMismatch(output) && hadToUninstall ->
                "Reinstall failed after removing the previous build. $detail"
            isSignatureMismatch(output) ->
                "A different AccessScope build is already installed (e.g. debug vs release). " +
                    "The installer will remove it and reinstall automatically."
            isVersionDowngrade(output) ->
                "Installed AccessScope is newer than the release APK. Uninstall the app on the device and retry."
            isInsufficientStorage(output) ->
                "Not enough free storage on the device. Free space and retry Install / Update."
            isInstallRestricted(output) ->
                "Install blocked by the device. Enable USB debugging and allow installs over USB in Developer options."
            isAlreadyInstalled(output) ->
                "AccessScope is already installed. Use Launch or retry Install / Update."
            detail.isNotBlank() -> detail
            else -> "APK install failed. Check device screen for prompts and retry."
        }
    }
}
