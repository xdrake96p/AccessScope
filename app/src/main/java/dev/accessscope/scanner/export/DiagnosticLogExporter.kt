/**
 * Esporta i log diagnostici di AccessScope in Download e li condivide.
 */
package dev.accessscope.scanner.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.accessscope.scanner.util.AppFileLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Combina i log interni e li salva in Download come file `.txt`.
 */
class DiagnosticLogExporter(private val context: Context) {

    fun export(): Result<String> = runCatching {
        val content = buildLogBundle()
        if (content.isBlank()) error("Nessun log disponibile")
        saveText(content, fileName())
    }

    private fun buildLogBundle(): String = buildString {
        appendLine("# AccessScope — Log diagnostici")
        appendLine("# Generato: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALY).format(Date())}")
        appendLine()
        val files = AppFileLogger.allLogFiles()
        if (files.isEmpty()) {
            appendLine("(nessun file di log)")
            return@buildString
        }
        files.forEach { file ->
            appendLine("===== ${file.name} =====")
            appendLine(file.readText(Charsets.UTF_8))
            appendLine()
        }
    }

    private fun saveText(content: String, fileName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Impossibile creare file in Download")
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: error("Impossibile scrivere file")
            return "Download/$fileName"
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }

    private fun fileName() =
        "AccessScope_Logs_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"

    companion object {
        /** Apre un chooser per condividere il file esportato. */
        fun shareExportedFile(context: Context, path: String) {
            val file = resolveFile(path) ?: return
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Condividi log diagnostici"))
            }
        }

        private fun resolveFile(path: String): File? {
            if (path.startsWith("/")) return File(path)
            val fileName = path.substringAfterLast('/')
            @Suppress("DEPRECATION")
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        }
    }
}
