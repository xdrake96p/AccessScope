/**
 * Persistenza delle impostazioni utente per la scansione di accessibilità.
 *
 * Gestisce l'avvio automatico delle app target e la configurazione
 * dell'ambito di scansione ([ScanScope]) tramite [android.content.SharedPreferences].
 */
package dev.accessscope.scanner.util

import android.content.Context
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea

/**
 * Store per le preferenze di scansione dell'utente.
 *
 * @param context Contesto Android; viene usato [Context.getApplicationContext] per evitare leak.
 */
class ScanSettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Indica se aprire automaticamente la prima app target all'avvio della scansione.
     */
    var autoLaunchEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    /**
     * Recupera l'ambito di scansione salvato dalle preferenze.
     *
     * @return [ScanScope.FULL] se nessuna preferenza è salvata o l'insieme risulta vuoto.
     */
    fun getScanScope(): ScanScope {
        val stored = prefs.getStringSet(KEY_ENABLED_AREAS, null)
        if (stored == null) return ScanScope.FULL
        val areas = stored.mapNotNull { name ->
            runCatching { ViolationArea.valueOf(name) }.getOrNull()
        }.toSet()
        return if (areas.isEmpty()) ScanScope.FULL else ScanScope(areas)
    }

    /**
     * Salva l'ambito di scansione nelle preferenze.
     *
     * @param scope [ScanScope] da persistere.
     */
    fun setScanScope(scope: ScanScope) {
        prefs.edit()
            .putStringSet(KEY_ENABLED_AREAS, scope.enabledAreas.map { it.name }.toSet())
            .apply()
    }

    /**
     * Imposta direttamente gli ambiti abilitati per la scansione.
     *
     * Non effettua alcuna modifica se [areas] è vuoto.
     *
     * @param areas Insieme di [ViolationArea] da abilitare.
     */
    fun setEnabledAreas(areas: Set<ViolationArea>) {
        if (areas.isEmpty()) return
        setScanScope(ScanScope(areas))
    }

    /**
     * Posizione orizzontale salvata dell'overlay di scansione (pixel da sinistra), o -1 per default.
     */
    var overlayPositionX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, -1)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_X, value).apply()

    /**
     * Posizione verticale salvata dell'overlay di scansione (pixel dall'alto), o -1 per default.
     */
    var overlayPositionY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, -1)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

    var reliabilityReportEnabled: Boolean
        get() = prefs.getBoolean(KEY_RELIABILITY_REPORT, false)
        set(value) = prefs.edit().putBoolean(KEY_RELIABILITY_REPORT, value).apply()

    companion object {
        private const val PREFS_NAME = "accessscope_settings"
        private const val KEY_AUTO_LAUNCH = "auto_launch_enabled"
        private const val KEY_RELIABILITY_REPORT = "reliability_report_enabled"
        private const val KEY_ENABLED_AREAS = "enabled_areas"
        private const val KEY_OVERLAY_X = "overlay_position_x"
        private const val KEY_OVERLAY_Y = "overlay_position_y"
    }
}
