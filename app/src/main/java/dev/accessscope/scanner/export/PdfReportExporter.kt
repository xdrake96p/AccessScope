package dev.accessscope.scanner.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportExporter(private val context: Context) {

    fun export(
        targetPackages: Set<String>,
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
        scannedScreens: Int,
    ): Result<String> = runCatching {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - margin * 2

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF0D7377.toInt()
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF0A4F52.toInt()
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = 0xFF222222.toInt()
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = 0xFF666666.toInt()
        }

        fun newPageIfNeeded(required: Float) {
            if (y + required <= pageHeight - margin) return
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = margin
        }

        fun drawLine(text: String, paint: Paint, lineHeight: Float = 16f) {
            val lines = wrapText(text, paint, contentWidth)
            lines.forEach { line ->
                newPageIfNeeded(lineHeight)
                canvas.drawText(line, margin, y, paint)
                y += lineHeight
            }
        }

        drawLine("AccessScope — Report Accessibilità", titlePaint, 28f)
        y += 8f
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())
        drawLine("Data: $date", metaPaint)
        drawLine("App monitorate: ${targetPackages.joinToString()}", metaPaint)
        drawLine("Schermate analizzate: $scannedScreens", metaPaint)
        drawLine("Violazioni totali: ${violations.size}", metaPaint)
        drawLine("Rilevazioni screen reader: ${screenReaderFindings.size}", metaPaint)
        y += 12f

        drawLine("Controlli: etichette, contrasto, testo piccolo, touch target, spaziatura,", metaPaint, 14f)
        drawLine("heading, input, TalkBack simulato, immagini, link, modali, liste.", metaPaint, 14f)
        y += 8f

        val grouped = violations.groupBy { it.type }.toSortedMap(compareBy { it.ordinal })

        grouped.forEach { (type, items) ->
            newPageIfNeeded(40f)
            drawLine(
                "${type.displayName} [${type.severity.name}] (${type.wcagRef}) — ${items.size} occorrenze",
                headingPaint,
                20f,
            )
            y += 4f
            items.take(200).forEach { violation ->
                drawLine("• ${violation.viewClassName}", bodyPaint)
                drawLine("  Schermata: ${violation.screenTitle}", metaPaint, 13f)
                violation.viewId?.let { drawLine("  ID: $it", metaPaint, 13f) }
                violation.bounds?.let { drawLine("  Bounds: $it", metaPaint, 13f) }
                drawLine("  ${violation.details}", bodyPaint, 14f)
                y += 4f
            }
            if (items.size > 200) {
                drawLine("… altre ${items.size - 200} occorrenze non mostrate", metaPaint)
            }
            y += 8f
        }

        if (screenReaderFindings.isNotEmpty()) {
            newPageIfNeeded(40f)
            drawLine("Simulazione TalkBack / Screen Reader", headingPaint, 20f)
            y += 4f
            screenReaderFindings.take(100).forEach { finding ->
                drawLine("• ${finding.nodeClassName}: ${finding.issue}", bodyPaint, 14f)
                finding.announcedText?.let {
                    drawLine("  Annuncio simulato: \"$it\"", metaPaint, 13f)
                }
                y += 2f
            }
        }

        document.finishPage(page)

        val fileName = "AccessScope_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        val savedPath = savePdf(document, fileName)
        document.close()
        savedPath
    }

    private fun savePdf(document: PdfDocument, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Impossibile creare il file PDF in Download.")
            resolver.openOutputStream(uri)?.use { output ->
                document.writeTo(output)
            } ?: error("Impossibile scrivere il PDF.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Download/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            file.absolutePath
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf(text) }
    }
}
