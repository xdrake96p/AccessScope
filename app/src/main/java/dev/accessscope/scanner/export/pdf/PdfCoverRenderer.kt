package dev.accessscope.scanner.export.pdf

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.report.ReportHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PdfCoverRenderer {

    /**
     * Disegna la pagina di copertina con riepilogo sintetico della scansione.
     *
     * @param ctx Contesto di rendering PDF corrente.
     * @param packages Pacchetti delle app analizzate.
     * @param screens Numero di schermate uniche.
     * @param analyses Numero di analisi eseguite.
     * @param scopeLabel Etichetta dell'ambito di scansione.
     * @param filtered Violazioni filtrate per confidenza.
     * @param talkBack Numero di note screen reader.
     * @param passedChecks Totale controlli superati.
     */
    fun drawCover(
        ctx: PdfContext,
        packages: Set<String>,
        screens: Int,
        analyses: Int,
        scopeLabel: String,
        filtered: List<AccessibilityViolation>,
        talkBack: Int,
        passedChecks: Int,
    ) {
        ctx.fillRect(0f, 0f, PAGE_W, 120f, COLOR_BRAND_DARK)
        ctx.drawText("AccessScope", 40f, 52f, ctx.titlePaint, COLOR_WHITE)
        ctx.drawText("Report accessibilità", 40f, 78f, ctx.subtitlePaint, 0xE0FFFFFF.toInt())

        var y = 150f
        ctx.drawText("In sintesi", 40f, y, ctx.headingPaint, COLOR_BRAND_DARK)
        y += 28f

        val date = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ITALY).format(Date())
        val score = ReportHelper.computeScore(filtered, screens)
        val scoreLabel = ReportHelper.scoreLabel(score)

        listOf(
            "📅  Data scansione: $date",
            "📱  App controllate: ${packages.size}",
            "🎯  Ambiti analizzati: $scopeLabel",
            "🖥️  Schermate uniche: $screens",
            "🔄  Analisi eseguite: $analyses",
            "⚠️  Problemi trovati: ${filtered.size}",
            "✅  Controlli superati: $passedChecks",
            "🔊  Note screen reader: $talkBack",
            "⭐  Punteggio stimato: $score/100 ($scoreLabel)",
        ).forEach { line ->
            ctx.drawText(line, 48f, y, ctx.bodyPaint, COLOR_TEXT)
            y += 22f
        }

        y += 8f
        ctx.drawText("Riepilogo per gravità", 40f, y, ctx.bodyBoldPaint, COLOR_BRAND_DARK)
        y += 20f
        ReportHelper.SEVERITY_ORDER.forEach { severity ->
            val count = filtered.count { it.type.severity == severity }
            ctx.drawText(
                "${ReportHelper.severityEmoji(severity)} ${ReportHelper.severityGroupTitle(severity)}: $count",
                56f, y, ctx.bodyPaint, PdfViolationRenderer.severityColor(severity),
            )
            y += 18f
        }

        y += 16f
        ctx.drawWrapped(
            "Questo report elenca problemi e controlli superati durante l'uso delle app selezionate. " +
                "Ogni problema include misure, riferimento WCAG e un suggerimento di correzione.",
            40f, y, CONTENT_W, ctx.bodyPaint, COLOR_TEXT,
        )
        ctx.y = y + 40f
    }
}
