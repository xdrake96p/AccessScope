/**
 * Utility per l'apertura dei report PDF generati da AccessScope.
 *
 * Risolve il percorso del file, crea un URI tramite [FileProvider]
 * e avvia un chooser per visualizzare il documento.
 */
package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper per aprire report PDF salvati su disco o in Download.
 */
object PdfHelper {

    /**
     * Apre un report PDF con un'app esterna tramite intent di visualizzazione.
     *
     * Se il file non esiste mostra un toast di errore; in assenza di app
     * compatibili notifica l'utente.
     *
     * @param context Contesto Android per toast e avvio activity.
     * @param path Percorso assoluto o relativo del file PDF.
     */
    fun openPdf(context: Context, path: String) {
        val file = resolveFile(path)
        if (file == null || !file.exists()) {
            Toast.makeText(context, "PDF non trovato: $path", Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(Intent.createChooser(intent, "Apri report PDF"))
        }.onFailure {
            Toast.makeText(context, "Nessuna app per aprire il PDF.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveFile(path: String): File? {
        if (path.startsWith("/")) return File(path)

        val fileName = path.substringAfterLast('/')
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        }
    }
}
