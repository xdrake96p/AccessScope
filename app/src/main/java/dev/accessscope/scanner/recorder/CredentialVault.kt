/**
 * Vault locale per placeholder Maestro `${PIN}` / `${PASSWORD}` (Play in-app).
 */
package dev.accessscope.scanner.recorder

import android.content.Context

/**
 * Memorizza PIN/password per package target in SharedPreferences (non esportati in YAML).
 *
 * @param context Contesto applicazione.
 */
class CredentialVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Restituisce il valore salvato per [kind] e [appId], o `null`.
     */
    fun get(appId: String, kind: Kind): String? =
        prefs.getString(key(appId, kind), null)?.takeIf { it.isNotBlank() }

    /**
     * Salva [value] per [kind] e [appId].
     */
    fun put(appId: String, kind: Kind, value: String) {
        if (value.isBlank()) return
        prefs.edit().putString(key(appId, kind), value).apply()
    }

    /**
     * Risolve un testo input: se è placeholder noto e c’è vault, restituisce il secret.
     *
     * @param appId Package target.
     * @param text Valore registrato / YAML.
     * @param isPassword Campo password.
     * @param viewId Id campo (per distinguere PIN).
     * @return Testo da digitare in Play.
     */
    fun resolveInput(appId: String, text: String, isPassword: Boolean, viewId: String?): String {
        val trimmed = text.trim()
        when {
            trimmed.equals(PLACEHOLDER_PIN, ignoreCase = true) ->
                return get(appId, Kind.Pin) ?: text
            trimmed.equals(PLACEHOLDER_PASSWORD, ignoreCase = true) ->
                return get(appId, Kind.Password) ?: text
            MaestroSelectorHeuristics.isPinLikeField(viewId) && (trimmed == "****" || trimmed.isBlank()) ->
                return get(appId, Kind.Pin) ?: text
            (isPassword || trimmed == "****") ->
                return get(appId, Kind.Password) ?: text
            else -> return text
        }
    }

    /** Tipo di secret. */
    enum class Kind { Pin, Password }

    companion object {
        const val PLACEHOLDER_PIN = "\${PIN}"
        const val PLACEHOLDER_PASSWORD = "\${PASSWORD}"
        private const val PREFS = "maestro_credentials"

        private fun key(appId: String, kind: Kind): String = "${appId}:${kind.name}"
    }
}
