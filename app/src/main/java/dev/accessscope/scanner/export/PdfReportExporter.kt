/**
 * Esportazione del report di accessibilità in formato PDF.
 *
 * Genera un documento multipagina con copertina, panoramica, copertura controlli,
 * dettaglio per schermata e glossario, salvandolo nella cartella Download.
 */
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
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.ReportHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Esportatore che trasforma i risultati di una sessione di scansione
 * in un report PDF leggibile e condivisibile.
 *
 * @property context Contesto Android usato per l'accesso a MediaStore e alle risorse di sistema.
 */
class PdfReportExporter(private val context: Context) {

    /**
     * Genera e salva il report PDF con tutti i dati della sessione di scansione.
     *
     * @param targetPackages Insieme dei pacchetti delle app analizzate.
     * @param violations Elenco completo delle violazioni rilevate.
     * @param screenReaderFindings Risultati della simulazione TalkBack.
     * @param uniqueScreens Numero di schermate uniche visitate.
     * @param scanAnalyses Numero totale di analisi eseguite (default: uguale a [uniqueScreens]).
     * @param scanScopeLabel Etichetta testuale dell'ambito di scansione (es. "Completa").
     * @param scannedScreens Titoli delle schermate visitate, in ordine di navigazione.
     * @param checkSummaries Riepiloghi dei controlli superati per area e schermata.
     * @return [Result] con il percorso del file salvato in caso di successo, o l'eccezione in caso di errore.
     */
    fun export(
        targetPackages: Set<String>,
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
        uniqueScreens: Int,
        scanAnalyses: Int = uniqueScreens,
        scanScopeLabel: String = "Completa",
        scannedScreens: List<String> = emptyList(),
        checkSummaries: List<CheckAreaSummary> = emptyList(),
    ): Result<String> = runCatching {
        val filtered = ReportHelper.filterViolations(violations)
        val document = PdfDocument()
        val ctx = PdfContext(document)
        val passedTotal = ReportHelper.totalPassedChecks(checkSummaries)

        drawCover(ctx, targetPackages, uniqueScreens, scanAnalyses, scanScopeLabel, filtered, screenReaderFindings.size, passedTotal)
        drawSummary(ctx, filtered, screenReaderFindings, scannedScreens)
        drawCheckCoverage(ctx, checkSummaries, filtered)
        drawHowToRead(ctx)
        drawScreenSections(ctx, filtered, screenReaderFindings, checkSummaries)

        drawGlossary(ctx)
        ctx.finish()
        savePdf(document, ctx.fileName())
    }

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
    private fun drawCover(
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

        y += 16f
        ctx.drawWrapped(
            "Questo report elenca problemi e controlli superati durante l'uso delle app selezionate. " +
                "Ogni problema include misure, riferimento WCAG e un suggerimento di correzione.",
            40f, y, CONTENT_W, ctx.bodyPaint, COLOR_TEXT,
        )
        ctx.y = y + 40f
    }

    /**
     * Disegna la sezione "Copertura controlli per ambito" con conteggi OK e problemi.
     *
     * @param ctx Contesto di rendering PDF.
     * @param checkSummaries Riepiloghi dei controlli superati.
     * @param violations Violazioni usate per il conteggio dei fallimenti per area.
     */
    private fun drawCheckCoverage(
        ctx: PdfContext,
        checkSummaries: List<CheckAreaSummary>,
        violations: List<AccessibilityViolation>,
    ) {
        val coverage = ReportHelper.globalCheckCoverage(checkSummaries, violations)
        if (coverage.isEmpty() && checkSummaries.isEmpty()) return

        ctx.ensureSpace(60f)
        ctx.drawText("Copertura controlli per ambito", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 26f

        if (coverage.isEmpty()) {
            ctx.drawText("Nessun controllo registrato in questa sessione.", 48f, ctx.y, ctx.bodyPaint, COLOR_MUTED)
            ctx.y += 22f
        } else {
            coverage.forEach { (area, counts) ->
                val (passed, failed) = counts
                ctx.ensureSpace(22f)
                ctx.drawText("${area.emoji} ${area.title}", 48f, ctx.y + 4f, ctx.bodyBoldPaint, COLOR_TEXT)
                ctx.drawText("OK $passed · Problemi $failed", PAGE_W - 160f, ctx.y + 4f, ctx.bodyPaint, COLOR_OK)
                ctx.y += 22f
            }
        }
        ctx.y += 8f
    }

    /**
     * Disegna la panoramica per schermata con conteggio problemi e elenco schermate pulite.
     *
     * @param ctx Contesto di rendering PDF.
     * @param violations Violazioni filtrate da mostrare nel riepilogo.
     * @param talkBack Risultati TalkBack (usati indirettamente tramite schermate visitate).
     * @param scannedScreens Elenco titoli schermate visitate; se vuoto si usano le chiavi dalle violazioni.
     */
    private fun drawSummary(
        ctx: PdfContext,
        violations: List<AccessibilityViolation>,
        talkBack: List<ScreenReaderFinding>,
        scannedScreens: List<String>,
    ) {
        ctx.ensureSpace(80f)
        ctx.drawText("Panoramica per schermata", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 28f

        val violationTotals = ReportHelper.screenTotals(violations)
        val allScreens = if (scannedScreens.isNotEmpty()) {
            scannedScreens.distinct().sorted()
        } else {
            violationTotals.keys.sorted()
        }

        allScreens.forEach { screen ->
            ctx.ensureSpace(28f)
            val total = violationTotals[screen] ?: 0
            ctx.drawText(screen, 48f, ctx.y + 4f, ctx.bodyBoldPaint, COLOR_TEXT)
            ctx.drawText("$total", PAGE_W - 60f, ctx.y + 4f, ctx.bodyBoldPaint, if (total == 0) 0xFF2E7D32.toInt() else COLOR_BRAND)
            ctx.y += 24f
        }

        val cleanScreens = allScreens.filter { (violationTotals[it] ?: 0) == 0 }
        if (cleanScreens.isNotEmpty()) {
            ctx.y += 8f
            ctx.ensureSpace(40f)
            ctx.drawText("Schermate senza problemi rilevati", 48f, ctx.y, ctx.bodyBoldPaint, 0xFF2E7D32.toInt())
            ctx.y += 20f
            cleanScreens.forEach { screen ->
                ctx.ensureSpace(22f)
                ctx.drawText("• $screen", 56f, ctx.y, ctx.bodyPaint, COLOR_TEXT)
                ctx.y += 18f
            }
        }
        ctx.y += 12f
    }

    /**
     * Disegna le sezioni dettagliate per ogni schermata: controlli superati, problemi e TalkBack.
     *
     * @param ctx Contesto di rendering PDF.
     * @param violations Violazioni filtrate da elencare per schermata e severità.
     * @param talkBack Risultati della simulazione TalkBack.
     * @param checkSummaries Riepiloghi controlli superati per schermata.
     */
    private fun drawScreenSections(
        ctx: PdfContext,
        violations: List<AccessibilityViolation>,
        talkBack: List<ScreenReaderFinding>,
        checkSummaries: List<CheckAreaSummary>,
    ) {
        val talkBackBySection = ReportHelper.groupTalkBackBySection(talkBack)
        val screensWithIssues = ReportHelper.groupViolationsBySection(violations).map { it.first.screenTitle }.toSet()
        val screensWithChecks = checkSummaries.map { it.screenTitle }.toSet()
        val allScreens = (screensWithIssues + screensWithChecks).toSortedSet()

        allScreens.forEach { screenTitle ->
            val sectionGroups = ReportHelper.groupViolationsBySection(violations)
                .filter { it.first.screenTitle == screenTitle }
            val screenChecks = ReportHelper.checksForScreen(checkSummaries, screenTitle)
            val screenViolations = violations.filter { it.screenTitle == screenTitle }
            if (screenViolations.isEmpty() && screenChecks.isEmpty()) return@forEach

            ctx.ensureSpace(80f)
            ctx.fillRect(40f, ctx.y - 8f, CONTENT_W, 34f, COLOR_BRAND_LIGHT)
            ctx.drawText(screenTitle, 48f, ctx.y + 12f, ctx.areaTitlePaint, COLOR_BRAND_DARK)
            ctx.y += 38f

            if (screenChecks.isNotEmpty()) {
                ctx.ensureSpace(28f)
                ctx.drawText("✅ Controlli superati", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_OK)
                ctx.y += 18f
                screenChecks.forEach { summary ->
                    ctx.ensureSpace(20f)
                    ctx.drawText(
                        "${summary.area.emoji} ${summary.area.title}: ${summary.passedCount} OK",
                        56f, ctx.y, ctx.bodyBoldPaint, COLOR_OK,
                    )
                    ctx.y += 16f
                    summary.samples.take(3).forEach { sample ->
                        ctx.ensureSpace(18f)
                        ctx.drawText(ReportHelper.passedCheckLine(sample), 64f, ctx.y, ctx.metaPaint, COLOR_MUTED)
                        ctx.y += 14f
                    }
                }
                ctx.y += 8f
            }

            sectionGroups.forEach { (section, sectionViolations) ->
            val sectionTalkBack = talkBackBySection[section].orEmpty()
            if (sectionViolations.isEmpty() && sectionTalkBack.isEmpty()) return@forEach

            if (section.hasSubsection) {
                ctx.ensureSpace(24f)
                ctx.drawText("Sezione: ${section.sectionTitle}", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND)
                ctx.y += 18f
            }

            if (sectionViolations.isNotEmpty()) {
                ctx.ensureSpace(24f)
                ctx.drawText("⚠️ Problemi rilevati", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND)
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
    }

    /**
     * Disegna la sezione esplicativa su come interpretare severità, controlli e confidenza.
     *
     * @param ctx Contesto di rendering PDF.
     */
    private fun drawHowToRead(ctx: PdfContext) {
        ctx.ensureSpace(120f)
        ctx.drawText("Come leggere questo report", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 24f
        val tips = listOf(
            "🔴 Critico — blocca l'uso con TalkBack o rende illeggibile il contenuto.",
            "🟠 Grave — ostacolo serio, da correggere presto.",
            "🟡 Medio — miglioramento consigliato.",
            "⚪ Lieve — rifinitura, bassa priorità.",
            "✅ Verde — controlli superati (contrasto, etichette, touch, ecc.).",
            "Confidenza % — quanto siamo sicuri del rilevamento (più alto = più affidabile).",
        )
        tips.forEach { tip ->
            ctx.ensureSpace(20f)
            ctx.drawWrapped(tip, 48f, ctx.y, CONTENT_W - 8f, ctx.bodyPaint, COLOR_MUTED)
            ctx.y += 8f
        }
        ctx.y += 16f
    }

    /**
     * Disegna una card singola per una violazione con barra laterale colorata per severità.
     *
     * @param ctx Contesto di rendering PDF.
     * @param v Violazione da visualizzare.
     */
    private fun drawViolationCard(ctx: PdfContext, v: AccessibilityViolation) {
        val type = v.type
        val lines = ReportHelper.violationDetailLines(v)
        val cardHeight = 36f + lines.size * 13f
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
        ctx.y += 12f
    }

    /**
     * Disegna il glossario con termini di accessibilità usati nel report.
     *
     * @param ctx Contesto di rendering PDF.
     */
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

    /**
     * Restituisce il colore ARGB associato a un livello di severità per la UI del PDF.
     *
     * @param s Livello di gravità.
     * @return Valore colore intero ARGB.
     */
    private fun severityColor(s: ViolationSeverity) = when (s) {
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
    private fun severityEmoji(s: ViolationSeverity) = when (s) {
        ViolationSeverity.CRITICAL -> "🔴"
        ViolationSeverity.SERIOUS -> "🟠"
        ViolationSeverity.MODERATE -> "🟡"
        ViolationSeverity.MINOR -> "⚪"
    }

    /**
     * Scrive il documento PDF su disco tramite MediaStore (API 29+) o file system legacy.
     *
     * @param document Documento PDF completato da salvare.
     * @param fileName Nome file desiderato (es. `AccessScope_20260101_120000.pdf`).
     * @return Percorso relativo o assoluto del file salvato.
     */
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

    /**
     * Contesto interno per il layout e il disegno progressivo delle pagine PDF.
     *
     * Gestisce paginazione automatica, paint preconfigurati e utilità di disegno testo/rettangoli.
     *
     * @property document Documento PDF Android su cui scrivere le pagine.
     */
    private class PdfContext(private val document: PdfDocument) {
        /** Coordinata Y corrente sul canvas della pagina attiva. */
        var y = 40f
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page

        init {
            page = createPage()
        }

        /** Paint per il titolo principale (copertina). */
        val titlePaint = paint(26f, true)
        /** Paint per il sottotitolo. */
        val subtitlePaint = paint(14f, false)
        /** Paint per le intestazioni di sezione. */
        val headingPaint = paint(16f, true)
        /** Paint per i titoli di area/schermata. */
        val areaTitlePaint = paint(15f, true)
        /** Paint per il corpo del testo. */
        val bodyPaint = paint(11f, false)
        /** Paint per il corpo in grassetto. */
        val bodyBoldPaint = paint(11f, true)
        /** Paint per metadati e testo secondario. */
        val metaPaint = paint(9.5f, false)

        /**
         * Chiude la pagina corrente e ne apre una nuova, azzerando [y].
         */
        fun newPage() {
            document.finishPage(page)
            page = createPage()
            y = 40f
        }

        /**
         * Apre una nuova pagina se lo spazio verticale residuo è insufficiente.
         *
         * @param required Altezza minima richiesta in punti dalla posizione [y] corrente.
         */
        fun ensureSpace(required: Float) {
            if (y + required > PAGE_H - 40f) newPage()
        }

        /**
         * Chiude l'ultima pagina aperta senza crearne una nuova.
         */
        fun finish() {
            document.finishPage(page)
        }

        /**
         * Genera un nome file univoco basato sulla data e ora corrente.
         *
         * @return Nome file nel formato `AccessScope_yyyyMMdd_HHmmss.pdf`.
         */
        fun fileName() = "AccessScope_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"

        /**
         * Crea e avvia una nuova pagina PDF con dimensioni A4 in punti.
         *
         * @return Pagina appena creata e pronta per il disegno.
         */
        private fun createPage(): PdfDocument.Page {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create()
            return document.startPage(info)
        }

        /**
         * Disegna una singola riga di testo sul canvas della pagina corrente.
         *
         * @param text Testo da disegnare.
         * @param x Coordinata X in punti.
         * @param yPos Coordinata Y della baseline in punti.
         * @param paint Paint con dimensione e stile del carattere.
         * @param color Colore ARGB del testo.
         */
        fun drawText(text: String, x: Float, yPos: Float, paint: Paint, color: Int) {
            paint.color = color
            page.canvas.drawText(text, x, yPos, paint)
        }

        /**
         * Disegna un rettangolo pieno sul canvas della pagina corrente.
         *
         * @param l Coordinata X dell'angolo superiore sinistro.
         * @param t Coordinata Y dell'angolo superiore sinistro.
         * @param w Larghezza in punti.
         * @param h Altezza in punti.
         * @param color Colore ARGB di riempimento.
         */
        fun fillRect(l: Float, t: Float, w: Float, h: Float, color: Int) {
            val p = Paint().apply { this.color = color }
            page.canvas.drawRect(RectF(l, t, l + w, t + h), p)
        }

        /**
         * Disegna testo con a capo automatico entro una larghezza massima.
         *
         * Aggiorna [y] alla fine del blocco disegnato.
         *
         * @param text Testo da disegnare, eventualmente su più righe.
         * @param x Coordinata X in punti.
         * @param startY Coordinata Y di partenza.
         * @param maxW Larghezza massima disponibile per il testo.
         * @param paint Paint per il rendering del testo.
         * @param color Colore ARGB del testo.
         */
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

        /**
         * Suddivide il testo in righe che rientrano nella larghezza massima misurata col [paint].
         *
         * @param text Testo da spezzare.
         * @param paint Paint usato per misurare la larghezza dei caratteri.
         * @param maxW Larghezza massima per riga in punti.
         * @return Elenco di righe pronte per il disegno.
         */
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

        /**
         * Crea un [Paint] per il testo con dimensione e peso specificati.
         *
         * @param size Dimensione del testo in punti.
         * @param bold `true` per carattere grassetto.
         * @return Paint configurato con antialiasing.
         */
        private fun paint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /**
     * Costanti di layout, colori e limiti per la generazione del PDF.
     */
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
        private const val COLOR_OK = 0xFF2E7D32.toInt()
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    }
}
