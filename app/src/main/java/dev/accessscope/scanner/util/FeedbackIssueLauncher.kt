/**
 * Avvia il flusso di invio feedback GitHub con allegato del report affidabilità `.md`.
 */
package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Apre la issue GitHub precompilata e propone di allegare il report Markdown di affidabilità.
 */
object FeedbackIssueLauncher {

    /**
     * @param issueUrl URL GitHub Issues precompilato.
     * @param reliabilityMdPath Percorso del report `.md` (assoluto o `Download/nome.md`).
     */
    fun launch(
        context: Context,
        issueUrl: String,
        reliabilityMdPath: String?,
    ) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            Toast.makeText(context, "Impossibile aprire il browser", Toast.LENGTH_LONG).show()
            return
        }

        val mdPath = reliabilityMdPath
        if (mdPath.isNullOrBlank()) {
            Toast.makeText(
                context,
                "Issue aperta. Nessun report affidabilità disponibile da allegare.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val file = resolveFile(mdPath)
        if (file == null || !file.exists()) {
            Toast.makeText(
                context,
                "Issue aperta. File report non trovato: $mdPath",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(
                Intent.EXTRA_TEXT,
                "Allega questo report alla issue GitHub appena aperta nel browser.",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(
                Intent.createChooser(shareIntent, "Allega report .md alla issue"),
            )
        }.onFailure {
            Toast.makeText(
                context,
                "Issue aperta. Impossibile condividere il report: ${file.name}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    internal fun resolveFile(path: String): File? {
        if (path.startsWith("/")) return File(path)
        val fileName = path.substringAfterLast('/')
        @Suppress("DEPRECATION")
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
    }

    internal fun fileExists(path: String): Boolean = resolveFile(path)?.exists() == true
}
