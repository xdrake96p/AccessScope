/**
 * Persistenza dello stato di completamento del tutorial iniziale.
 */
package dev.accessscope.scanner.util

import android.content.Context

/**
 * Store [SharedPreferences] per il flag "non mostrare più" dell'onboarding.
 *
 * @param context Contesto Android; usa [Context.getApplicationContext].
 */
class OnboardingStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Indica se l'utente ha completato il tutorial chiedendo di non riproporlo.
     *
     * @return `true` se il tutorial non va più mostrato all'avvio.
     */
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    /**
     * Salva lo stato di completamento del tutorial.
     *
     * @param completed `true` per non mostrare più il tutorial ai prossimi avvii.
     */
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    companion object {
        private const val PREFS_NAME = "accessscope_onboarding"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
