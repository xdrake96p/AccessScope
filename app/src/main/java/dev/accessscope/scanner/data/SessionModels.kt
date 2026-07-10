/**
 * Modelli per lo stato live e le sessioni archiviate di scansione.
 */
package dev.accessscope.scanner.data

/** Schermata visitata durante una sessione, con impronta stabile e ordine di navigazione. */
data class VisitedScreen(
    val fingerprint: String,
    val title: String,
    val visitIndex: Int,
)

/** Sessione di scansione archiviata su disco per cronologia e confronto tra sessioni. */
data class ArchivedScanSession(
    val id: String,
    val completedAtMs: Long,
    val targetPackages: Set<String>,
    val violations: List<AccessibilityViolation>,
    val screenReaderFindings: List<ScreenReaderFinding>,
    val uniqueScreens: Int,
    val scanAnalyses: Int,
    val scanScopeLabel: String,
    val score: Int,
    val pdfPath: String?,
    val violationKeys: Set<String>,
)

/**
 * Stato completo e osservabile di una sessione di scansione di accessibilità.
 *
 * Viene aggiornato dal [ScanSessionRepository] e consumato dall'interfaccia utente.
 */
data class ScanSessionState(
    val isScanning: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val violations: List<AccessibilityViolation> = emptyList(),
    val screenReaderFindings: List<ScreenReaderFinding> = emptyList(),
    val uniqueScreens: Int = 0,
    val scanAnalyses: Int = 0,
    val scanScope: ScanScope = ScanScope.FULL,
    val visitedScreenTitles: List<String> = emptyList(),
    val visitedScreens: List<VisitedScreen> = emptyList(),
    val checkSummaries: List<CheckAreaSummary> = emptyList(),
    val lastPdfPath: String? = null,
    val lastReliabilityMdPath: String? = null,
    val errorMessage: String? = null,
    val sessionId: String? = null,
) {
    /**
     * Alias retrocompatibile per [uniqueScreens].
     *
     * @deprecated Usare [uniqueScreens].
     */
    val scannedScreens: Int get() = uniqueScreens
}
