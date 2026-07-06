/**
 * Persistenza della preferenza tema (chiaro / scuro / sistema).
 */
package dev.accessscope.scanner.util

import android.content.Context
import dev.accessscope.scanner.ui.theme.AppThemeMode

/**
 * Store [SharedPreferences] per la modalità tema scelta dall'utente.
 *
 * @param context Contesto Android; usa [Context.getApplicationContext].
 */
class ThemePreferencesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Legge la modalità tema salvata.
     *
     * @return [AppThemeMode.SYSTEM] se nessuna preferenza è stata salvata.
     */
    fun getThemeMode(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)!!)
    }.getOrDefault(AppThemeMode.SYSTEM)

    /**
     * Salva la modalità tema scelta dall'utente.
     *
     * @param mode Preferenza da persistere.
     */
    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "accessscope_theme"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
