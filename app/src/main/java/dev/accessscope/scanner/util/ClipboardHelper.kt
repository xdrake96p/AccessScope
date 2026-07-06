/**
 * Utility per copiare testo negli appunti di sistema.
 */
package dev.accessscope.scanner.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Helper per operazioni sulla clipboard Android.
 */
object ClipboardHelper {

    /**
     * Copia [text] negli appunti con etichetta [label] per l'accessibilità.
     *
     * @param context Contesto Android.
     * @param label Etichetta descrittiva della clipboard (es. «Prompt AI AccessScope»).
     * @param text Contenuto da copiare.
     */
    fun copyText(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
