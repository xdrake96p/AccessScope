package dev.accessscope.scanner.util

import android.content.Context
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea

class ScanSettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoLaunchEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    fun getScanScope(): ScanScope {
        val stored = prefs.getStringSet(KEY_ENABLED_AREAS, null)
        if (stored == null) return ScanScope.FULL
        val areas = stored.mapNotNull { name ->
            runCatching { ViolationArea.valueOf(name) }.getOrNull()
        }.toSet()
        return if (areas.isEmpty()) ScanScope.FULL else ScanScope(areas)
    }

    fun setScanScope(scope: ScanScope) {
        prefs.edit()
            .putStringSet(KEY_ENABLED_AREAS, scope.enabledAreas.map { it.name }.toSet())
            .apply()
    }

    fun setEnabledAreas(areas: Set<ViolationArea>) {
        if (areas.isEmpty()) return
        setScanScope(ScanScope(areas))
    }

    companion object {
        private const val PREFS_NAME = "accessscope_settings"
        private const val KEY_AUTO_LAUNCH = "auto_launch_enabled"
        private const val KEY_ENABLED_AREAS = "enabled_areas"
    }
}
