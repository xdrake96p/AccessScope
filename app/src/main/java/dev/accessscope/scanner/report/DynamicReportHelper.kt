/**
 * Aggregazione dati per il report dinamico navigabile per schermata.
 *
 * Attribuzione fingerprint-first per violazioni, TalkBack e controlli passati;
 * fallback al titolo solo se fingerprint assente (sessioni legacy).
 */
package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ScreenProtectionReason
import dev.accessscope.scanner.data.VisitedScreen
import dev.accessscope.scanner.util.ScanEvidenceStore

/** Frame di una schermata visitata con problemi e controlli associati. */
data class DynamicScreenFrame(
    val fingerprint: String,
    val title: String,
    val visitIndex: Int,
    val violations: List<AccessibilityViolation>,
    val talkBackFindings: List<ScreenReaderFinding>,
    val passedCount: Int,
    val screenEvidenceId: String,
    val protectionReason: ScreenProtectionReason = ScreenProtectionReason.NONE,
)

/**
 * Costruisce i frame del report dinamico da visite e finding di sessione.
 */
object DynamicReportHelper {

    /**
     * Costruisce i frame ordinati per percorso di navigazione.
     *
     * @param visitedScreens Schermate visitate con fingerprint (sessione live).
     * @param violations Violazioni della sessione.
     * @param talkBackFindings Note TalkBack della sessione.
     * @param checkSummaries Controlli superati per schermata.
     * @param screenEvidenceIdResolver Funzione per derivare l'ID evidenza da fingerprint.
     * @param includeLowConfidence Se true, non filtra findings sotto soglia confidenza.
     */
    fun buildFrames(
        visitedScreens: List<VisitedScreen>,
        violations: List<AccessibilityViolation>,
        talkBackFindings: List<ScreenReaderFinding>,
        checkSummaries: List<CheckAreaSummary>,
        screenEvidenceIdResolver: (String) -> String = { fingerprint ->
            fingerprint.replace(Regex("""[^\w.-]"""), "_").take(120)
        },
        includeLowConfidence: Boolean = false,
    ): List<DynamicScreenFrame> {
        val filteredViolations = ReportHelper.filterViolations(violations, includeLowConfidence)
        val violationsByFingerprint = filteredViolations
            .filter { !it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenFingerprint!! }
        val violationsByTitle = filteredViolations
            .filter { it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenTitle }

        val talkBackByFingerprint = talkBackFindings
            .filter { !it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenFingerprint!! }
        val talkBackByTitle = talkBackFindings
            .filter { it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.reportSection }

        val passedByFingerprint = checkSummaries
            .filter { !it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenFingerprint!! }
            .mapValues { (_, items) -> items.sumOf { it.passedCount } }
        val passedByTitle = checkSummaries
            .filter { it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenTitle }
            .mapValues { (_, items) -> items.sumOf { it.passedCount } }

        val titleVisitCount = visitedScreens.groupingBy { it.title }.eachCount()

        val framesFromVisits = visitedScreens.map { screen ->
            val screenViolations = violationsByFingerprint[screen.fingerprint].orEmpty()
            val titleViolations = if (screenViolations.isEmpty()) {
                violationsByTitle[screen.title].orEmpty()
            } else {
                screenViolations
            }
            val shareTitle = (titleVisitCount[screen.title] ?: 1) > 1

            val talkBackFp = talkBackByFingerprint[screen.fingerprint].orEmpty()
            val talkBackForFrame = when {
                talkBackFp.isNotEmpty() -> talkBackFp
                !shareTitle -> talkBackByTitle[screen.title].orEmpty()
                titleViolations.isNotEmpty() -> talkBackByTitle[screen.title].orEmpty()
                else -> emptyList()
            }

            val passedFp = passedByFingerprint[screen.fingerprint]
            val passedForFrame = when {
                passedFp != null -> passedFp
                !shareTitle -> passedByTitle[screen.title] ?: 0
                titleViolations.isNotEmpty() -> passedByTitle[screen.title] ?: 0
                else -> 0
            }

            DynamicScreenFrame(
                fingerprint = screen.fingerprint,
                title = screen.title,
                visitIndex = screen.visitIndex,
                violations = titleViolations,
                talkBackFindings = talkBackForFrame,
                passedCount = passedForFrame,
                screenEvidenceId = screenEvidenceIdResolver(screen.fingerprint),
                protectionReason = screen.protectionReason,
            )
        }

        if (framesFromVisits.isNotEmpty()) return framesFromVisits

        return buildFramesFromViolationsOnly(
            filteredViolations = filteredViolations,
            talkBackFindings = talkBackFindings,
            checkSummaries = checkSummaries,
            screenEvidenceIdResolver = screenEvidenceIdResolver,
        )
    }

    private fun buildFramesFromViolationsOnly(
        filteredViolations: List<AccessibilityViolation>,
        talkBackFindings: List<ScreenReaderFinding>,
        checkSummaries: List<CheckAreaSummary>,
        screenEvidenceIdResolver: (String) -> String,
    ): List<DynamicScreenFrame> {
        val passedByTitle = checkSummaries.groupBy { it.screenTitle }
            .mapValues { (_, items) -> items.sumOf { it.passedCount } }
        val talkBackByTitle = talkBackFindings.groupBy { it.reportSection }
        val talkBackByFingerprint = talkBackFindings
            .filter { !it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenFingerprint!! }
        val passedByFingerprint = checkSummaries
            .filter { !it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenFingerprint!! }
            .mapValues { (_, items) -> items.sumOf { it.passedCount } }

        val fingerprintGroups = filteredViolations
            .filter { !it.screenFingerprint.isNullOrBlank() }
            .groupBy { it.screenFingerprint!! }

        if (fingerprintGroups.isNotEmpty()) {
            return fingerprintGroups.entries.mapIndexed { index, (fingerprint, group) ->
                val title = group.firstOrNull()?.screenTitle ?: "Schermata ${index + 1}"
                DynamicScreenFrame(
                    fingerprint = fingerprint,
                    title = title,
                    visitIndex = index,
                    violations = group,
                    talkBackFindings = talkBackByFingerprint[fingerprint]
                        ?: talkBackByTitle[title].orEmpty(),
                    passedCount = passedByFingerprint[fingerprint]
                        ?: passedByTitle[title]
                        ?: 0,
                    screenEvidenceId = screenEvidenceIdResolver(fingerprint),
                )
            }
        }

        val titleGroups = (filteredViolations.map { it.screenTitle } +
            talkBackFindings.map { it.reportSection } +
            checkSummaries.map { it.screenTitle })
            .distinct()
            .sorted()

        return titleGroups.mapIndexed { index, title ->
            val fingerprint = "title::$title"
            DynamicScreenFrame(
                fingerprint = fingerprint,
                title = title,
                visitIndex = index,
                violations = filteredViolations.filter { it.screenTitle == title },
                talkBackFindings = talkBackByTitle[title].orEmpty(),
                passedCount = passedByTitle[title] ?: 0,
                screenEvidenceId = screenEvidenceIdResolver(fingerprint),
            )
        }
    }

    /** Filtra le violazioni di un frame per gravità. */
    fun filterFrameViolations(
        frame: DynamicScreenFrame,
        severityFilter: dev.accessscope.scanner.data.ViolationSeverity?,
    ): List<AccessibilityViolation> {
        if (severityFilter == null) return frame.violations
        return frame.violations.filter { it.type.severity == severityFilter }
    }

    fun screenEvidenceId(evidenceStore: ScanEvidenceStore, fingerprint: String): String =
        evidenceStore.screenEvidenceId(fingerprint)
}
