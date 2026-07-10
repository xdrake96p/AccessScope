package dev.accessscope.scanner.export.pdf

import android.graphics.BitmapFactory
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.EvidenceKind
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.ReportHelper
import kotlin.math.roundToInt

internal object PdfViolationRenderer {

    /**
     * Disegna una card singola per una violazione con barra laterale colorata per severità.
     *
     * @param ctx Contesto di rendering PDF.
     * @param v Violazione da visualizzare.
     */
    fun drawViolationCard(ctx: PdfContext, v: AccessibilityViolation, drawImage: Boolean = true) {
        val type = v.type
        val lines = ReportHelper.violationDetailLines(v)
        var cardHeight = 36f + lines.size * 13f
        val imagePath = v.evidenceImagePath
        val hasImage = drawImage && !imagePath.isNullOrBlank() && java.io.File(imagePath).exists()
        if (hasImage) cardHeight += 188f
        ctx.ensureSpace(cardHeight + 12f)
        val severityColor = severityColor(type.severity)
        ctx.fillRect(48f, ctx.y - 6f, 6f, cardHeight, severityColor)
        ctx.fillRect(54f, ctx.y - 6f, CONTENT_W - 14f, cardHeight, 0xFFFAFAFA.toInt())

        val sevEmoji = severityEmoji(type.severity)
        ctx.drawText("$sevEmoji ${type.displayName}", 64f, ctx.y + 10f, ctx.bodyBoldPaint, COLOR_TEXT)
        ctx.drawText("${v.confidence.times(100).roundToInt()}%", PAGE_W - 56f, ctx.y + 10f, ctx.metaPaint, COLOR_MUTED)
        ctx.y += 22f
        ctx.drawWrapped(v.simpleExplanation, 64f, ctx.y, CONTENT_W - 32f, ctx.bodyPaint, COLOR_TEXT)
        ctx.y += 16f
        lines.forEach { line ->
            ctx.drawWrapped(line, 64f, ctx.y, CONTENT_W - 32f, ctx.metaPaint, COLOR_MUTED)
            ctx.y += 4f
        }
        if (hasImage) {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap != null) {
                ctx.y += 8f
                val maxW = CONTENT_W - 32f
                val maxH = 168f
                val scale = minOf(maxW / bitmap.width, maxH / bitmap.height, 1f)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                ctx.drawBitmap(bitmap, 64f, ctx.y, w, h)
                ctx.y += h + 6f
                val caption = if (v.evidenceKind == EvidenceKind.SYNTHETIC_SECURE) {
                    "Evidenza sintetica — schermata con FLAG_SECURE · ${v.screenTitle}"
                } else {
                    "Evidenza: ${v.screenTitle}${v.bounds?.let { " · $it" } ?: ""}"
                }
                ctx.drawText(caption, 64f, ctx.y, ctx.metaPaint, COLOR_MUTED)
                ctx.y += 14f
                bitmap.recycle()
            }
        }
        ctx.y += 12f
    }

    /**
     * Restituisce il colore ARGB associato a un livello di severità per la UI del PDF.
     *
     * @param s Livello di gravità.
     * @return Valore colore intero ARGB.
     */
    fun severityColor(s: ViolationSeverity) = when (s) {
        ViolationSeverity.CRITICAL -> 0xFFC62828.toInt()
        ViolationSeverity.SERIOUS -> 0xFFE65100.toInt()
        ViolationSeverity.MODERATE -> 0xFFF9A825.toInt()
        ViolationSeverity.MINOR -> 0xFF9E9E9E.toInt()
    }

    /**
     * Restituisce l'emoji associata a un livello di severità nel PDF.
     *
     * @param s Livello di gravità.
     * @return Emoji Unicode corrispondente.
     */
    fun severityEmoji(s: ViolationSeverity) = when (s) {
        ViolationSeverity.CRITICAL -> "🔴"
        ViolationSeverity.SERIOUS -> "🟠"
        ViolationSeverity.MODERATE -> "🟡"
        ViolationSeverity.MINOR -> "⚪"
    }
}
