/**
 * Modelli per lo stato live e le sessioni archiviate di scansione.
 */
package dev.accessscope.scanner.data

/** Motivo per cui una schermata non ha screenshot reale o contrasto colore completo. */
enum class ScreenProtectionReason {
    /** Schermata normale, screenshot e contrasto disponibili se catturati. */
    NONE,
    /** `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (FLAG_SECURE). */
    FLAG_SECURE,
    /** PIN pad, campo password o titolo/nodi sensibili nell'albero a11y. */
    PIN_OR_PASSWORD,
    /** Bitmap nero/vuoto o capture fallita senza segnale secure nell'albero. */
    SCREENSHOT_BLOCKED,
}

/** Schermata visitata durante una sessione, con impronta stabile e ordine di navigazione. */
data class VisitedScreen(
    val fingerprint: String,
    val title: String,
    val visitIndex: Int,
    val protectionReason: ScreenProtectionReason = ScreenProtectionReason.NONE,
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
    val visitedScreens: List<VisitedScreen> = emptyList(),
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
