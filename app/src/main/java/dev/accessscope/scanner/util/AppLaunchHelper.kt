/**
 * Utility per l'avvio di applicazioni target durante la scansione.
 */
package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent

/**
 * Helper per aprire le app selezionate dall'utente tramite intent di launcher.
 */
object AppLaunchHelper {

    /**
     * Apre la prima app selezionata che dispone di un activity di launcher.
     *
     * Itera i package nell'ordine fornito e avvia la prima disponibile.
     *
     * @param context Contesto Android per [Context.startActivity].
     * @param packages Insieme ordinato di package da provare in sequenza.
     * @return Package dell'app avviata con successo, oppure `null` se nessuna è apribile.
     */
    fun launchFirstAvailable(context: Context, packages: Set<String>): String? {
        val pm = context.packageManager
        for (packageName in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: continue
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return packageName
        }
        return null
    }
}
