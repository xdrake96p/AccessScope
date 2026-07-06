package dev.accessscope.scanner.util

import android.content.Context

class ScanSettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoLaunchEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    companion object {
        private const val PREFS_NAME = "accessscope_settings"
        private const val KEY_AUTO_LAUNCH = "auto_launch_enabled"
    }
}
