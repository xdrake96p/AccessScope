package dev.accessscope.scanner.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.ReportHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PdfReportExporter(private val context: Context) {

    fun export(
        targetPackages: Set<String>,
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
        scannedScreens: Int,
    ): Result<String> = runCatching {
        val filtered = ReportHelper.filterViolations(violations)
        val document = PdfDocument()
        val ctx = PdfContext(document)

        drawCover(ctx, targetPackages, scannedScreens, filtered.size, screenReaderFindings.size)
        drawSummary(ctx, filtered, screenReaderFindings)
        drawHowToRead(ctx)
        drawScreenSections(ctx, filtered, screenReaderFindings)

        drawGlossary(ctx)
        ctx.finish()
        savePdf(document, ctx.fileName())
    }

    private fun drawCover(
        ctx: PdfContext,
        packages: Set<String>,
        screens: Int,
        issues: Int,
        talkBack: Int,
    ) {
        ctx.fillRect(0f, 0f, PAGE_W, 120f, COLOR_BRAND_DARK)
        ctx.drawText("AccessScope", 40f, 52f, ctx.titlePaint, COLOR_WHITE)
        ctx.drawText("Report accessibilità", 40f, 78f, ctx.subtitlePaint, 0xE0FFFFFF.toInt())

        var y = 150f
        ctx.drawText("In sintesi", 40f, y, ctx.headingPaint, COLOR_BRAND_DARK)
        y += 28f

        val date = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ITALY).format(Date())
        val score = ReportHelper.computeScore(issues, screens)
        val scoreLabel = ReportHelper.scoreLabel(score)

        listOf(
            "📅  Data scansione: $date",
            "📱  App controllate: ${packages.size}",
            "🖥️  Schermate analizzate: $screens",
            "⚠️  Problemi trovati: $issues",
            "🔊  Note screen reader: $talkBack",
            "⭐  Punteggio stimato: $score/100 ($scoreLabel)",
        ).forEach { line ->
            ctx.drawText(line, 48f, y, ctx.bodyPaint, COLOR_TEXT)
            y += 22f
        }

        y += 16f
        ctx.drawWrapped(
            "Questo report elenca i problemi di accessibilità trovati mentre usavi le app selezionate. " +
                "Ogni problema spiega cosa non va e perché conta per chi usa TalkBack o ha difficoltà visive.",
            40f, y, CONTENT_W, ctx.bodyPaint, COLOR_TEXT,
        )
        ctx.y = y + 40f
    }

    private fun drawSummary(ctx: PdfContext, violations: List<AccessibilityViolation>, talkBack: List<ScreenReaderFinding>) {
        ctx.ensureSpace(80f)
        ctx.drawText("Panoramica per schermata", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 28f

        ReportHelper.screenTotals(violations).toSortedMap().forEach { (screen, total) ->
            ctx.ensureSpace(28f)
            ctx.drawText(screen, 48f, ctx.y + 4f, ctx.bodyBoldPaint, COLOR_TEXT)
            ctx.drawText("$total", PAGE_W - 60f, ctx.y + 4f, ctx.bodyBoldPaint, COLOR_BRAND)
            ctx.y += 24f
        }
        ctx.y += 12f
    }

    private fun drawScreenSections(
        ctx: PdfContext,
        violations: List<AccessibilityViolation>,
        talkBack: List<ScreenReaderFinding>,
    ) {
        val talkBackBySection = ReportHelper.groupTalkBackBySection(talkBack)
        ReportHelper.groupViolationsBySection(violations).forEach { (section, sectionViolations) ->
            val sectionTalkBack = talkBackBySection[section].orEmpty()
            if (sectionViolations.isEmpty() && sectionTalkBack.isEmpty()) return@forEach

            ctx.ensureSpace(80f)
            ctx.fillRect(40f, ctx.y - 8f, CONTENT_W, 34f, COLOR_BRAND_LIGHT)
            ctx.drawText(section.screenTitle, 48f, ctx.y + 12f, ctx.areaTitlePaint, COLOR_BRAND_DARK)
            ctx.y += 38f
            if (section.hasSubsection) {
                ctx.drawText("Sezione: ${section.sectionTitle}", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND)
                ctx.y += 18f
            }

            ReportHelper.SEVERITY_ORDER.forEach { severity ->
                val items = sectionViolations.filter { it.type.severity == severity }
                if (items.isEmpty()) return@forEach
                ctx.ensureSpace(28f)
                ctx.drawText(
                    "${ReportHelper.severityEmoji(severity)} ${ReportHelper.severityGroupTitle(severity)} (${items.size})",
                    48f,
                    ctx.y,
                    ctx.bodyBoldPaint,
                    severityColor(severity),
                )
                ctx.y += 20f
                items.take(MAX_PER_TYPE).forEach { drawViolationCard(ctx, it) }
                if (items.size > MAX_PER_TYPE) {
                    ctx.drawText("… altri ${items.size - MAX_PER_TYPE} simili", 56f, ctx.y, ctx.metaPaint, COLOR_MUTED)
                    ctx.y += 16f
                }
            }

            if (sectionTalkBack.isNotEmpty()) {
                ctx.ensureSpace(36f)
                ctx.drawText("Simulazione TalkBack", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND)
                ctx.y += 18f
                sectionTalkBack.take(20).forEach { f ->
                    ctx.ensureSpace(44f)
                    ctx.drawWrapped("• ${f.issue}", 56f, ctx.y, CONTENT_W - 24f, ctx.bodyPaint, COLOR_TEXT)
                    ctx.y += 10f
                }
            }
            ctx.y += 16f
        }
    }

    private fun drawHowToRead(ctx: PdfContext) {
        ctx.ensureSpace(120f)
        ctx.drawText("Come leggere questo report", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 24f
        val tips = listOf(
            "🔴 Critico — blocca l'uso con TalkBack o rende illeggibile il contenuto.",
            "🟠 Grave — ostacolo serio, da correggere presto.",
            "🟡 Medio — miglioramento consigliato.",
            "⚪ Lieve — rifinitura, bassa priorità.",
            "🟢 Verde (dashboard) — metriche OK, non problemi.",
            "Confidenza % — quanto siamo sicuri del rilevamento (più alto = più affidabile).",
        )
        tips.forEach { tip ->
            ctx.ensureSpace(20f)
            ctx.drawWrapped(tip, 48f, ctx.y, CONTENT_W - 8f, ctx.bodyPaint, COLOR_MUTED)
            ctx.y += 8f
        }
        ctx.y += 16f
    }

    private fun drawViolationCard(ctx: PdfContext, v: AccessibilityViolation) {
        val type = v.type
        ctx.ensureSpace(90f)
        val severityColor = severityColor(type.severity)
        ctx.fillRect(48f, ctx.y - 6f, 6f, 52f, severityColor)
        ctx.fillRect(54f, ctx.y - 6f, CONTENT_W - 14f, 52f, 0xFFFAFAFA.toInt())

        val sevEmoji = severityEmoji(type.severity)
        ctx.drawText("$sevEmoji ${type.displayName}", 64f, ctx.y + 10f, ctx.bodyBoldPaint, COLOR_TEXT)
        ctx.drawText("${v.confidence.times(100).roundToInt()}%", PAGE_W - 56f, ctx.y + 10f, ctx.metaPaint, COLOR_MUTED)
        ctx.y += 22f
        ctx.drawWrapped(v.simpleExplanation, 64f, ctx.y, CONTENT_W - 32f, ctx.bodyPaint, COLOR_TEXT)
        ctx.y += 18f
        ctx.drawText("Dettaglio: ${v.details}", 64f, ctx.y, ctx.metaPaint, COLOR_MUTED)
        ctx.y += 14f
        val meta = buildList {
            add(v.viewClassName.substringAfterLast('.'))
            v.viewId?.let { add(it.substringAfterLast('/')) }
            v.bounds?.let { add(it) }
        }.joinToString("  ·  ")
        ctx.drawText(meta, 64f, ctx.y, ctx.metaPaint, 0xFF999999.toInt())
        ctx.y += 24f
    }

    private fun drawGlossary(ctx: PdfContext) {
        ctx.ensureSpace(80f)
        ctx.drawText("Glossario rapido", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 24f
        val terms = listOf(
            "TalkBack" to "Lettore vocale di Android: legge ad alta voce cosa tocchi.",
            "WCAG" to "Linee guida internazionali per rendere siti e app usabili da tutti.",
            "Contrasto" to "Differenza tra colore testo e sfondo: più alto = più leggibile.",
            "Target di tocco" to "Area premibile: deve essere abbastanza grande (circa 48×48 px).",
            "contentDescription" to "Testo che TalkBack legge al posto di un'icona o immagine.",
        )
        terms.forEach { (term, def) ->
            ctx.ensureSpace(36f)
            ctx.drawText(term, 48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND)
            ctx.y += 16f
            ctx.drawWrapped(def, 56f, ctx.y, CONTENT_W - 16f, ctx.bodyPaint, COLOR_MUTED)
            ctx.y += 20f
        }
    }

    private fun severityColor(s: ViolationSeverity) = when (s) {
        ViolationSeverity.CRITICAL -> 0xFFC62828.toInt()
        ViolationSeverity.SERIOUS -> 0xFFE65100.toInt()
        ViolationSeverity.MODERATE -> 0xFFF9A825.toInt()
        ViolationSeverity.MINOR -> 0xFF9E9E9E.toInt()
    }

    private fun severityEmoji(s: ViolationSeverity) = when (s) {
        ViolationSeverity.CRITICAL -> "🔴"
        ViolationSeverity.SERIOUS -> "🟠"
        ViolationSeverity.MODERATE -> "🟡"
        ViolationSeverity.MINOR -> "⚪"
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
                ?: error("Impossibile creare il PDF.")
            resolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: error("Impossibile scrivere il PDF.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            document.close()
            "Download/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            file.absolutePath
        }
    }

    private class PdfContext(private val document: PdfDocument) {
        var y = 40f
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page

        init {
            page = createPage()
        }

        val titlePaint = paint(26f, true)
        val subtitlePaint = paint(14f, false)
        val headingPaint = paint(16f, true)
        val areaTitlePaint = paint(15f, true)
        val bodyPaint = paint(11f, false)
        val bodyBoldPaint = paint(11f, true)
        val metaPaint = paint(9.5f, false)

        fun newPage() {
            document.finishPage(page)
            page = createPage()
            y = 40f
        }

        fun ensureSpace(required: Float) {
            if (y + required > PAGE_H - 40f) newPage()
        }

        fun finish() {
            document.finishPage(page)
        }

        fun fileName() = "AccessScope_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"

        private fun createPage(): PdfDocument.Page {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create()
            return document.startPage(info)
        }

        fun drawText(text: String, x: Float, yPos: Float, paint: Paint, color: Int) {
            paint.color = color
            page.canvas.drawText(text, x, yPos, paint)
        }

        fun fillRect(l: Float, t: Float, w: Float, h: Float, color: Int) {
            val p = Paint().apply { this.color = color }
            page.canvas.drawRect(RectF(l, t, l + w, t + h), p)
        }

        fun drawWrapped(text: String, x: Float, startY: Float, maxW: Float, paint: Paint, color: Int) {
            paint.color = color
            var cy = startY
            wrap(text, paint, maxW).forEach { line ->
                ensureSpace(16f)
                page.canvas.drawText(line, x, cy, paint)
                cy += 14f
            }
            y = cy
        }

        private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            val words = text.split(' ')
            val lines = mutableListOf<String>()
            var cur = StringBuilder()
            words.forEach { w ->
                val cand = if (cur.isEmpty()) w else "$cur $w"
                if (paint.measureText(cand) <= maxW) cur = StringBuilder(cand)
                else {
                    if (cur.isNotEmpty()) lines += cur.toString()
                    cur = StringBuilder(w)
                }
            }
            if (cur.isNotEmpty()) lines += cur.toString()
            return lines.ifEmpty { listOf(text) }
        }

        private fun paint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    companion object {
        private const val PAGE_W = 595f
        private const val PAGE_H = 842f
        private const val CONTENT_W = PAGE_W - 80f
        private const val MAX_PER_TYPE = 25
        private const val COLOR_BRAND = 0xFF0D7377.toInt()
        private const val COLOR_BRAND_DARK = 0xFF0A4F52.toInt()
        private const val COLOR_BRAND_LIGHT = 0xFFE8F5F4.toInt()
        private const val COLOR_TEXT = 0xFF1A2B2C.toInt()
        private const val COLOR_MUTED = 0xFF5C6B6C.toInt()
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    }
}
