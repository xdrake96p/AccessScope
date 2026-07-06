package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object PdfHelper {

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
