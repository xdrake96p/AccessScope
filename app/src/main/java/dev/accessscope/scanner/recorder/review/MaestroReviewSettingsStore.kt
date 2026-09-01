/**
 * Impostazioni revisione Maestro AI (API key Google AI Studio).
 */
package dev.accessscope.scanner.recorder.review

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persistenza sicura API key Gemini Flash (BYOK gratuito Google AI Studio).
 */
class MaestroReviewSettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy { createPrefs() }

    /** API key Google AI Studio; vuota se non configurata. */
    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_API, value.trim()).apply()
        }

    /** Ultimo modello Gemini usato con successo (per riprovare per primo). */
    var lastWorkingModel: String?
        get() = prefs.getString(KEY_LAST_MODEL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_LAST_MODEL, value?.trim().orEmpty()).apply()
        }

    /** Preferenza modello: [MODEL_AUTO], gemini-3.5-flash, gemini-3.5-flash-lite. */
    var preferredModel: String
        get() = prefs.getString(KEY_PREFERRED_MODEL, MODEL_AUTO) ?: MODEL_AUTO
        set(value) {
            prefs.edit().putString(KEY_PREFERRED_MODEL, value.trim()).apply()
        }

    /** `true` se c'è una key non vuota. */
    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    private fun createPrefs(): SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        appContext.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    companion object {
        const val MODEL_AUTO = "auto"
        private const val PREFS_NAME = "maestro_ai_review"
        private const val PREFS_NAME_FALLBACK = "maestro_ai_review_plain"
        private const val KEY_API = "gemini_api_key"
        private const val KEY_LAST_MODEL = "gemini_last_model"
        private const val KEY_PREFERRED_MODEL = "gemini_preferred_model"
    }
}
