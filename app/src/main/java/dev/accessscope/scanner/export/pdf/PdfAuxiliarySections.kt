package dev.accessscope.scanner.export.pdf

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.ReportHelper

internal object PdfAuxiliarySections {

    /**
     * Disegna la sezione "Copertura controlli per ambito" con conteggi OK e problemi.
     *
     * @param ctx Contesto di rendering PDF.
     * @param checkSummaries Riepiloghi dei controlli superati.
     * @param violations Violazioni usate per il conteggio dei fallimenti per area.
     */
    fun drawCheckCoverage(
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
    fun drawSummary(
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

    fun drawExecutiveSummary(ctx: PdfContext, violations: List<AccessibilityViolation>) {
        if (violations.isEmpty()) return
        ctx.ensureSpace(90f)
        ctx.drawText("Executive summary", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 28f
        violations.groupBy { it.type.displayName }
            .toList()
            .sortedByDescending { it.second.size }
            .take(5)
            .forEach { (name, list) ->
                ctx.ensureSpace(20f)
                ctx.drawText("• $name: ${list.size}", 48f, ctx.y, ctx.bodyPaint, COLOR_TEXT)
                ctx.y += 18f
            }
        val criticalScreens = violations.groupBy { it.screenTitle }
            .mapValues { (_, items) ->
                items.count {
                    it.type.severity == ViolationSeverity.CRITICAL ||
                        it.type.severity == ViolationSeverity.SERIOUS
                }
            }
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        if (criticalScreens.isNotEmpty()) {
            ctx.y += 8f
            ctx.drawText("Schermate più critiche:", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_TEXT)
            ctx.y += 18f
            criticalScreens.forEach { (screen, count) ->
                ctx.ensureSpace(18f)
                ctx.drawText("• $screen ($count gravi/critici)", 56f, ctx.y, ctx.bodyPaint, COLOR_MUTED)
                ctx.y += 16f
            }
        }
        ctx.y += 12f
    }

    /**
     * Dettaglio problemi ordinato per gravità (Critico → Lieve), poi per schermata.
     */
    fun drawSeveritySections(
        ctx: PdfContext,
        violations: List<AccessibilityViolation>,
        talkBack: List<ScreenReaderFinding>,
        checkSummaries: List<CheckAreaSummary>,
    ) {
        val talkBackBySection = ReportHelper.groupTalkBackBySection(talkBack)
        val grouped = ReportHelper.groupViolationsBySeverity(violations)
        if (grouped.isEmpty() && talkBack.isEmpty()) return

        ctx.ensureSpace(60f)
        ctx.drawText("Dettaglio problemi per gravità", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
        ctx.y += 30f

        grouped.forEach { (severity, sections) ->
            ctx.ensureSpace(48f)
            ctx.fillRect(40f, ctx.y - 6f, CONTENT_W, 30f, PdfViolationRenderer.severityColor(severity).and(0x22FFFFFF))
            ctx.drawText(
                "${ReportHelper.severityEmoji(severity)} ${ReportHelper.severityGroupTitle(severity)}",
                48f, ctx.y + 14f, ctx.areaTitlePaint, PdfViolationRenderer.severityColor(severity),
            )
            ctx.y += 36f

            var imagesDrawn = 0
            sections.forEach { (section, sectionViolations) ->
                if (sectionViolations.isEmpty()) return@forEach
                ctx.ensureSpace(28f)
                ctx.drawText(
                    "${section.screenTitle}${if (section.hasSubsection) " · ${section.sectionTitle}" else ""}",
                    48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND,
                )
                ctx.y += 20f
                sectionViolations.take(MAX_PER_TYPE).forEach { v ->
                    if (imagesDrawn < MAX_IMAGES_PER_SEVERITY) {
                        PdfViolationRenderer.drawViolationCard(ctx, v, drawImage = true)
                        imagesDrawn++
                    } else {
                        PdfViolationRenderer.drawViolationCard(ctx, v, drawImage = false)
                    }
                }
                if (sectionViolations.size > MAX_PER_TYPE) {
                    ctx.drawText(
                        "… altri ${sectionViolations.size - MAX_PER_TYPE} simili",
                        56f, ctx.y, ctx.metaPaint, COLOR_MUTED,
                    )
                    ctx.y += 16f
                }
            }
            ctx.y += 12f
        }

        if (talkBack.isNotEmpty()) {
            ctx.ensureSpace(48f)
            ctx.drawText("Allegato TalkBack", 40f, ctx.y, ctx.headingPaint, COLOR_BRAND_DARK)
            ctx.y += 26f
            talkBackBySection.forEach { (section, findings) ->
                ctx.ensureSpace(24f)
                ctx.drawText("${section.screenTitle} — ${section.sectionTitle}", 48f, ctx.y, ctx.bodyBoldPaint, COLOR_BRAND)
                ctx.y += 18f
                findings.take(20).forEach { f ->
                    ctx.ensureSpace(40f)
                    ctx.drawWrapped("• ${f.issue}", 56f, ctx.y, CONTENT_W - 24f, ctx.bodyPaint, COLOR_TEXT)
                    ctx.y += 8f
                }
            }
        }
    }

    /**
     * Disegna le sezioni dettagliate per ogni schermata: controlli superati, problemi e TalkBack.
     *
     * @param ctx Contesto di rendering PDF.
     * @param violations Violazioni filtrate da elencare per schermata e severità.
     * @param talkBack Risultati della simulazione TalkBack.
     * @param checkSummaries Riepiloghi controlli superati per schermata.
     */
    fun drawScreenSections(
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
                        PdfViolationRenderer.severityColor(severity),
                    )
                    ctx.y += 20f
                    items.take(MAX_PER_TYPE).forEach { PdfViolationRenderer.drawViolationCard(ctx, it) }
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
    fun drawHowToRead(ctx: PdfContext) {
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
     * Disegna il glossario con termini di accessibilità usati nel report.
     *
     * @param ctx Contesto di rendering PDF.
     */
    fun drawGlossary(ctx: PdfContext) {
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
}
