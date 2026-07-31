/**
 * Download / copia YAML Maestro in cartella Download pubblica.
 *
 * Competenza I/O file utente — separata da FlowStore (storage interno app).
 */
package dev.accessscope.scanner.ui.maestro

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.accessscope.scanner.recorder.FlowStore
import dev.accessscope.scanner.recorder.SavedFlow
import java.io.File
import java.io.FileOutputStream

/**
 * Esito di un download YAML.
 *
 * @property displayPath Percorso o nome mostrato all’utente.
 * @property usedShareFallback `true` se si è aperto il share sheet invece di Downloads.
 */
data class YamlDownloadResult(
    val displayPath: String,
    val usedShareFallback: Boolean = false,
)

/**
 * Helper per esportare il YAML di un flusso verso Download o Share.
 */
object YamlDownloadHelper {

    /**
     * Scrive il YAML in Downloads (`AccessScope/`) via MediaStore (API 29+) o file pubblico.
     * Se fallisce, apre lo share Intent come fallback.
     *
     * @param context Context Android.
     * @param flowStore Store flussi.
     * @param flow Metadati flusso.
     * @return Risultato con path/nome, o `null` se YAML assente.
     */
    fun downloadOrShare(
        context: Context,
        flowStore: FlowStore,
        flow: SavedFlow,
    ): YamlDownloadResult? {
        val yaml = flowStore.readYaml(flow) ?: return null
        val fileName = sanitizeFileName(flow.name.ifBlank { flow.id }) + ".yaml"
        val saved = runCatching { saveToDownloads(context, fileName, yaml) }.getOrNull()
        if (saved != null) {
            return YamlDownloadResult(displayPath = saved)
        }
        // Fallback: share via FileProvider sul file interno.
        val file = flowStore.yamlFile(flow)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/yaml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Condividi YAML Maestro"))
        return YamlDownloadResult(displayPath = file.name, usedShareFallback = true)
    }

    /**
     * Salva testo in Downloads/AccessScope.
     *
     * @return Path relativo leggibile (es. `Download/AccessScope/foo.yaml`).
     */
    private fun saveToDownloads(context: Context, fileName: String, content: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/yaml")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AccessScope")
            }
            val resolver = context.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("openOutputStream failed")
            return "Download/AccessScope/$fileName"
        }
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AccessScope",
        )
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        FileOutputStream(outFile).use { it.write(content.toByteArray(Charsets.UTF_8))
        }
        return outFile.absolutePath
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "flow" }.take(64)
}
