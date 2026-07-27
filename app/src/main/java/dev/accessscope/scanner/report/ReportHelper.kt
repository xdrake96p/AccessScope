/**
 * Utilità per l'aggregazione e la presentazione dei dati di accessibilità.
 *
 * Fornisce filtri per confidenza, calcolo del punteggio, raggruppamento per schermata
 * e sezione, e formattazione delle righe per report UI e PDF.
 */
package dev.accessscope.scanner.report

import dev.accessscope.scanner.analyzer.ViolationConfidencePolicy
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.PassedCheck
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import kotlin.math.roundToInt

/**
 * Chiave di raggruppamento per violazioni e risultati TalkBack in report e PDF.
 *
 * @property screenTitle Titolo della schermata (es. "Home", "Impostazioni").
 * @property sectionTitle Titolo della sottosezione all'interno della schermata;
 *                        coincide con [screenTitle] se non esiste una sottosezione distinta.
 */
data class ReportSectionGroup(
    val screenTitle: String,
    val sectionTitle: String,
) {
    /** `true` se [sectionTitle] rappresenta una sottosezione diversa da [screenTitle]. */
    val hasSubsection: Boolean get() = sectionTitle != screenTitle
}

/**
 * Helper con funzioni pure per filtrare, aggregare e formattare i risultati
 * di una sessione di scansione AccessScope.
 */
object ReportHelper {
    /** Soglia minima di confidenza predefinita per includere una violazione nel report. */
    const val MIN_CONFIDENCE = 0.65f

    /** Ordine di visualizzazione delle severità, dalla più grave alla più lieve. */
    val SEVERITY_ORDER = listOf(
        ViolationSeverity.CRITICAL,
        ViolationSeverity.SERIOUS,
        ViolationSeverity.MODERATE,
        ViolationSeverity.MINOR,
    )

    /**
     * Restituisce la soglia di confidenza minima per un dato tipo di violazione.
     *
     * Alcuni tipi (contrasto, gerarchia titoli, ecc.) richiedono confidenza più alta
     * per ridurre i falsi positivi.
     *
     * @param type Tipo di violazione da valutare.
     * @return Valore di confidenza compreso tra 0 e 1 oltre il quale la violazione è inclusa.
     */
    fun confidenceThreshold(type: ViolationType): Float = when (type) {
        ViolationType.LOW_COLOR_CONTRAST,
        ViolationType.LOW_NON_TEXT_CONTRAST,
        -> 0.72f
        ViolationType.HEADING_HIERARCHY,
        ViolationType.TEXT_TRUNCATED,
        ViolationType.DUPLICATE_VIEW_ID,
        -> 0.80f
        ViolationType.REQUIRED_FIELD_UNMARKED,
        ViolationType.INPUT_ERROR_MISSING,
        -> 0.75f
        ViolationType.OVERLAPPING_TOUCH_TARGETS,
        ViolationType.REDUNDANT_ACCESSIBLE_NAME,
        -> 0.80f
        ViolationType.CUSTOM_ACTION_UNLABELED,
        ViolationType.SCROLLABLE_WITHOUT_LABEL,
        -> 0.78f
        else -> MIN_CONFIDENCE
    }

    /**
     * Filtra le violazioni la cui confidenza supera la soglia specifica per il tipo.
     *
     * Applica anche [dev.accessscope.scanner.analyzer.ViolationConfidencePolicy.demoteIfNoisy]
     * per demotare pattern strutturali rumorosi prima del confronto soglia.
     *
     * @param violations Elenco completo delle violazioni rilevate in sessione.
     * @param includeLowConfidence Se true, include anche findings sotto soglia (debug/Settings).
     * @return Sottoinsieme di violazioni con confidenza sufficiente per il report, deduplicate per [AccessibilityViolation.dedupeKey].
     */
    fun filterViolations(
        violations: List<AccessibilityViolation>,
        includeLowConfidence: Boolean = false,
    ): List<AccessibilityViolation> {
        val demoted = violations.map { ViolationConfidencePolicy.demoteIfNoisy(it) }
        val filtered = if (includeLowConfidence) {
            demoted
        } else {
            demoted.filter { it.confidence >= confidenceThreshold(it.type) }
        }
        return filtered.distinctBy { it.dedupeKey }
    }

    /**
     * Calcola un punteggio di accessibilità stimato (0–100) in base a violazioni e schermate.
     *
     * Il calcolo applica pesi per severità e normalizza sul numero di schermate uniche analizzate.
     *
     * @param violations Elenco delle violazioni da considerare (verranno filtrate per confidenza).
     * @param screens Numero di schermate uniche visitate durante la scansione.
     * @return Punteggio intero tra 0 (critico) e 100 (ottimo).
     */
    fun computeScore(violations: List<AccessibilityViolation>, screens: Int): Int {
        if (screens == 0) return 100
        val filtered = filterViolations(violations)
        val weighted = filtered.sumOf { severityWeight(it.type.severity) }
        return (100 - weighted / screens * 6).roundToInt().coerceIn(0, 100)
    }

    /**
     * Calcola un punteggio semplificato in base al conteggio grezzo dei problemi.
     *
     * @param issues Numero totale di problemi rilevati.
     * @param screens Numero di schermate uniche analizzate.
     * @return Punteggio intero tra 0 e 100.
     * @deprecated Usare [computeScore] con l'elenco delle violazioni per un calcolo ponderato per severità.
     */
    fun computeScore(issues: Int, screens: Int): Int {
        if (screens == 0) return 100
        return (100 - issues.toFloat() / screens * 8).roundToInt().coerceIn(0, 100)
    }

    /**
     * Peso numerico associato a una severità per il calcolo del punteggio.
     *
     * @param severity Livello di gravità della violazione.
     * @return Peso decimale usato nella somma ponderata.
     */
    private fun severityWeight(severity: ViolationSeverity): Double = when (severity) {
        ViolationSeverity.CRITICAL -> 4.0
        ViolationSeverity.SERIOUS -> 2.0
        ViolationSeverity.MODERATE -> 1.0
        ViolationSeverity.MINOR -> 0.5
    }

    /**
     * Restituisce l'etichetta testuale italiana corrispondente a un punteggio.
     *
     * @param score Punteggio calcolato tra 0 e 100.
     * @return Etichetta qualitativa ("Ottimo", "Buono", "Da migliorare", "Critico").
     */
    fun scoreLabel(score: Int): String = when {
        score >= 85 -> "Ottimo"
        score >= 70 -> "Buono"
        score >= 50 -> "Da migliorare"
        else -> "Critico"
    }

    /**
     * Estrae la chiave di sezione da una violazione di accessibilità.
     *
     * @param violation Violazione da cui derivare schermata e sottosezione.
     * @return [ReportSectionGroup] con titolo schermata e sezione report.
     */
    fun sectionKey(violation: AccessibilityViolation): ReportSectionGroup =
        ReportSectionGroup(
            screenTitle = violation.screenTitle,
            sectionTitle = violation.reportSection,
        )

    /**
     * Estrae la chiave di sezione da un risultato della simulazione TalkBack.
     *
     * @param finding Risultato screen reader da raggruppare.
     * @return [ReportSectionGroup] con titolo schermata e sezione report.
     */
    fun sectionKey(finding: ScreenReaderFinding): ReportSectionGroup =
        ReportSectionGroup(
            screenTitle = finding.screenTitle,
            sectionTitle = finding.reportSection,
        )

    /**
     * Ordina le violazioni per severità (dalla più grave) e poi per nome del tipo.
     *
     * @param violations Elenco di violazioni da ordinare.
     * @return Nuova lista ordinata secondo [SEVERITY_ORDER].
     */
    fun sortBySeverity(violations: List<AccessibilityViolation>): List<AccessibilityViolation> =
        violations.sortedWith(
            compareBy(
                { SEVERITY_ORDER.indexOf(it.type.severity) },
                { it.type.displayName },
            ),
        )

    /**
     * Raggruppa le violazioni per schermata e sottosezione, ordinate per severità.
     *
     * @param violations Elenco completo delle violazioni della sessione.
     * @return Coppie (chiave sezione, violazioni ordinate) ordinate per schermata e sezione.
     */
    fun groupViolationsBySection(
        violations: List<AccessibilityViolation>,
    ): List<Pair<ReportSectionGroup, List<AccessibilityViolation>>> =
        violations
            .groupBy(::sectionKey)
            .toList()
            .sortedWith(compareBy({ it.first.screenTitle }, { it.first.sectionTitle }))
            .map { (key, items) -> key to sortBySeverity(items) }

    /**
     * Raggruppa le violazioni per gravità, poi per schermata/sezione.
     *
     * @param violations Elenco violazioni da aggregare.
     * @return Coppie (severità, gruppi per sezione) nell'ordine [SEVERITY_ORDER].
     */
    fun groupViolationsBySeverity(
        violations: List<AccessibilityViolation>,
    ): List<Pair<ViolationSeverity, List<Pair<ReportSectionGroup, List<AccessibilityViolation>>>>> =
        SEVERITY_ORDER.mapNotNull { severity ->
            val forSeverity = violations.filter { it.type.severity == severity }
            if (forSeverity.isEmpty()) return@mapNotNull null
            severity to groupViolationsBySection(forSeverity)
        }

    /**
     * Raggruppa i risultati TalkBack per schermata e sottosezione.
     *
     * @param findings Elenco dei risultati della simulazione screen reader.
     * @return Mappa da chiave sezione a elenco di [ScreenReaderFinding].
     */
    fun groupTalkBackBySection(
        findings: List<ScreenReaderFinding>,
    ): Map<ReportSectionGroup, List<ScreenReaderFinding>> =
        findings.groupBy(::sectionKey)

    /**
     * Restituisce l'emoji associata a un livello di severità per la UI e il PDF.
     *
     * @param severity Livello di gravità.
     * @return Emoji Unicode (🔴, 🟠, 🟡, ⚪).
     */
    fun severityEmoji(severity: ViolationSeverity): String = when (severity) {
        ViolationSeverity.CRITICAL -> "🔴"
        ViolationSeverity.SERIOUS -> "🟠"
        ViolationSeverity.MODERATE -> "🟡"
        ViolationSeverity.MINOR -> "⚪"
    }

    /**
     * Restituisce il titolo di gruppo in italiano per un livello di severità.
     *
     * @param severity Livello di gravità.
     * @return Etichetta plurale ("Critiche", "Gravi", "Medie", "Lievi").
     */
    fun severityGroupTitle(severity: ViolationSeverity): String = when (severity) {
        ViolationSeverity.CRITICAL -> "Critiche"
        ViolationSeverity.SERIOUS -> "Gravi"
        ViolationSeverity.MODERATE -> "Medie"
        ViolationSeverity.MINOR -> "Lievi"
    }

    /**
     * Conta le violazioni per titolo di schermata.
     *
     * @param violations Elenco delle violazioni da aggregare.
     * @return Mappa schermata → numero di problemi.
     */
    fun screenTotals(violations: List<AccessibilityViolation>): Map<String, Int> =
        violations.groupingBy { it.screenTitle }.eachCount()

    /**
     * Voce di riepilogo per una singola schermata nel report.
     *
     * @property screenTitle Titolo della schermata.
     * @property violationCount Numero di violazioni rilevate sulla schermata.
     * @property passedCount Numero totale di controlli superati sulla schermata.
     */
    data class ScreenOverviewEntry(
        val screenTitle: String,
        val violationCount: Int,
        val passedCount: Int,
    )

    /**
     * Costruisce un riepilogo per schermata con conteggi di problemi e controlli superati.
     *
     * @param violations Violazioni della sessione.
     * @param summaries Riepiloghi dei controlli superati per area.
     * @return Elenco ordinato alfabeticamente per titolo schermata.
     */
    fun screenOverview(
        violations: List<AccessibilityViolation>,
        summaries: List<CheckAreaSummary>,
    ): List<ScreenOverviewEntry> {
        val violationTotals = screenTotals(violations)
        val passedByScreen = summaries.groupBy { it.screenTitle }
            .mapValues { (_, items) -> items.sumOf { it.passedCount } }
        return (violationTotals.keys + passedByScreen.keys)
            .sorted()
            .map { screen ->
                ScreenOverviewEntry(
                    screenTitle = screen,
                    violationCount = violationTotals[screen] ?: 0,
                    passedCount = passedByScreen[screen] ?: 0,
                )
            }
    }

    /**
     * Conta problemi e note TalkBack per ambito di violazione ([ViolationArea]).
     *
     * @param violations Violazioni da aggregare per area.
     * @param screenReaderFindings Risultati TalkBack; conteggiati solo nell'area SCREEN_READER.
     * @return Mappa area → conteggio totale (solo aree con almeno un elemento).
     */
    fun areaTotals(
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
    ): Map<ViolationArea, Int> = ViolationArea.entries.associateWith { area ->
        val count = violations.count { it.area == area }
        val talkBack = if (area == ViolationArea.SCREEN_READER) screenReaderFindings.size else 0
        count + talkBack
    }.filterValues { it > 0 }

    /**
     * Conta quanti ambiti di analisi presentano almeno un problema o nota TalkBack.
     *
     * @param violations Violazioni della sessione.
     * @param talkBackCount Numero di risultati della simulazione TalkBack.
     * @return Numero di [ViolationArea] con almeno un'anomalia.
     */
    fun areasWithIssues(violations: List<AccessibilityViolation>, talkBackCount: Int): Int {
        val areas = violations.map { it.area }.toMutableSet()
        if (talkBackCount > 0) areas.add(ViolationArea.SCREEN_READER)
        return areas.size
    }

    /**
     * Conta gli ambiti di analisi senza problemi rilevati.
     *
     * @param violations Violazioni della sessione.
     * @param talkBackCount Numero di risultati TalkBack.
     * @return Numero di ambiti "puliti" rispetto al totale definito in [ViolationArea].
     */
    fun cleanAreaCount(violations: List<AccessibilityViolation>, talkBackCount: Int): Int =
        ViolationArea.entries.size - areasWithIssues(violations, talkBackCount)

    /**
     * Somma il numero totale di controlli superati in tutte le aree e schermate.
     *
     * @param summaries Riepiloghi dei controlli superati.
     * @return Conteggio aggregato di tutti i `passedCount`.
     */
    fun totalPassedChecks(summaries: List<CheckAreaSummary>): Int =
        summaries.sumOf { it.passedCount }

    /**
     * Raggruppa i riepiloghi controlli per titolo di schermata.
     *
     * @param summaries Elenco dei riepiloghi per area e schermata.
     * @return Mappa schermata → riepiloghi ordinati per ordinal dell'area.
     */
    fun groupChecksByScreen(summaries: List<CheckAreaSummary>): Map<String, List<CheckAreaSummary>> =
        summaries.groupBy { it.screenTitle }.mapValues { (_, items) ->
            items.sortedBy { it.area.ordinal }
        }

    /**
     * Filtra i riepiloghi controlli per una schermata specifica.
     *
     * @param summaries Elenco completo dei riepiloghi.
     * @param screenTitle Titolo della schermata da filtrare.
     * @return Riepiloghi relativi alla schermata indicata.
     */
    fun checksForScreen(summaries: List<CheckAreaSummary>, screenTitle: String): List<CheckAreaSummary> =
        summaries.filter { it.screenTitle == screenTitle }

    /**
     * Calcola la copertura globale dei controlli per ambito (superati vs problemi).
     *
     * @param summaries Riepiloghi dei controlli superati per area.
     * @param violations Violazioni rilevate, usate per il conteggio dei fallimenti per area.
     * @return Elenco di coppie (area, passed to failed) per le aree con almeno un dato.
     */
    fun globalCheckCoverage(
        summaries: List<CheckAreaSummary>,
        violations: List<AccessibilityViolation>,
    ): List<Pair<ViolationArea, Pair<Int, Int>>> {
        val passedByArea = summaries.groupBy { it.area }.mapValues { (_, items) -> items.sumOf { it.passedCount } }
        val failedByArea = violations.groupingBy { it.area }.eachCount()
        return ViolationArea.entries.mapNotNull { area ->
            val passed = passedByArea[area] ?: 0
            val failed = failedByArea[area] ?: 0
            if (passed == 0 && failed == 0) null else area to (passed to failed)
        }
    }

    /**
     * Formatta le righe di dettaglio di una violazione per la visualizzazione in report/PDF.
     *
     * Include dettaglio tecnico, etichetta elemento, misure, riferimento WCAG,
     * suggerimento di correzione e metadati di posizione.
     *
     * @param v Violazione da descrivere.
     * @return Elenco di righe testuali pronte per la stampa o la UI.
     */
    fun violationDetailLines(v: AccessibilityViolation): List<String> = buildList {
        add("Dettaglio: ${v.details}")
        v.elementLabel?.takeIf { it.isNotBlank() }?.let { add("Elemento: \"$it\"") }
        if (!v.measuredValue.isNullOrBlank() || !v.requiredValue.isNullOrBlank()) {
            add("Misura: ${v.measuredValue ?: "—"} · Richiesto: ${v.requiredValue ?: "—"}")
        }
        contrastColorLine(v)?.let { add(it) }
        add("WCAG: ${v.wcagReference}")
        v.remediation?.let { add("Suggerimento: $it") }
        val meta = buildList {
            add(v.viewClassName.substringAfterLast('.'))
            v.viewId?.substringAfterLast('/')?.let { add("@id/$it") }
            v.bounds?.let { add(it) }
        }.joinToString(" · ")
        if (meta.isNotBlank()) add("Posizione: $meta")
    }

    /**
     * Riga colori esadecimali per violazioni di contrasto (testo o UI).
     */
    fun contrastColorLine(v: AccessibilityViolation): String? {
        if (v.type != ViolationType.LOW_COLOR_CONTRAST &&
            v.type != ViolationType.LOW_NON_TEXT_CONTRAST
        ) {
            return null
        }
        val fg = v.foregroundColorHex?.takeIf { it.isNotBlank() }
        val bg = v.backgroundColorHex?.takeIf { it.isNotBlank() }
        if (fg == null && bg == null) return null
        return "Colori: primo piano ${fg ?: "—"} · sfondo ${bg ?: "—"}"
    }

    /**
     * Formatta una riga descrittiva per un controllo superato.
     *
     * @param check Controllo superato con etichetta e riepilogo elemento.
     * @return Stringa con checkmark, etichetta e opzionalmente view id.
     */
    fun passedCheckLine(check: PassedCheck): String = buildString {
        append("✓ ${check.checkLabel}")
        if (check.elementSummary.isNotBlank()) append(": ${check.elementSummary}")
        check.viewId?.substringAfterLast('/')?.let { append(" (@id/$it)") }
    }
}
