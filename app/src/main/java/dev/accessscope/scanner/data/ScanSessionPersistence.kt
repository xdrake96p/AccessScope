/**
 * Persistenza SharedPreferences per sessioni di scansione interrotte.
 */
package dev.accessscope.scanner.data

import dev.accessscope.scanner.ui.selection.AppSelectionPolicy

internal class ScanSessionPersistence(private val prefs: android.content.SharedPreferences) {

    fun hasPersistedScan(): Boolean = prefs.getBoolean(KEY_SCANNING, false)

    fun loadPersistedPackages(): Set<String> =
        AppSelectionPolicy.enforceMax(prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty())

    fun persistStart(packages: Set<String>, sessionId: String) {
        prefs.edit()
            .putBoolean(KEY_SCANNING, true)
            .putStringSet(KEY_PACKAGES, packages)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SCANNING)
            .remove(KEY_PACKAGES)
            .remove(KEY_SESSION_ID)
            .apply()
    }

    fun loadSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)

    companion object {
        const val PREFS_NAME = "access_scope_scan"
        private const val KEY_SCANNING = "is_scanning"
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_SESSION_ID = "session_id"
    }
}
