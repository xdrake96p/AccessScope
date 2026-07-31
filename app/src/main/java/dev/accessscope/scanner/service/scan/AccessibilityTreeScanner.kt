package dev.accessscope.scanner.service.scan

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.NodeAccessibilityAnalyzer
import dev.accessscope.scanner.analyzer.ScreenFingerprint
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.util.ScanEvidenceStore
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.ScreenshotCapture
import dev.accessscope.scanner.util.SecureScreenDetector
import dev.accessscope.scanner.util.ViolationBoundsParser

/**
 * Analizza una radice dell'albero UI e aggiorna il repository con i risultati.
 */
internal class AccessibilityTreeScanner(
    private val repository: ScanSessionRepository,
    private val evidenceStore: ScanEvidenceStore,
    private val seenFingerprintsThisSession: MutableSet<String>,
) {

    /**
     * Clona [AccessibilityEvent.source] sul thread dell'evento, prima che venga riciclato dal framework.
     */
    fun obtainEventSourceSnapshot(event: AccessibilityEvent): AccessibilityNodeInfo? =
        try {
            event.source?.let { AccessibilityNodeInfo.obtain(it) }
        } catch (e: IllegalStateException) {
            AppFileLogger.info("A11yService", "event_source_not_sealed type=${event.eventType}")
            null
        }

    /**
     * Analizza una singola radice dell'albero UI e aggiorna il repository con i risultati.
     *
     * Ignora overlay transitori e drawer; registra violazioni, controlli superati,
     * risultati TalkBack e schermate uniche tramite fingerprint.
     *
     * @param root Radice dell'albero da analizzare.
     * @param packageName Pacchetto dell'app target.
     * @param event Evento di accessibilità che ha scatenato la scansione.
     * @param screenshot Bitmap opzionale per controlli sul colore; `null` se non disponibile.
     * @param analyzer Analizzatore configurato per l'ambito e la densità correnti.
     */
    fun scanRoot(
        root: AccessibilityNodeInfo,
        packageName: String,
        event: AccessibilityEvent,
        capture: ScreenshotCapture?,
        analyzer: NodeAccessibilityAnalyzer,
    ) {
        if (ScreenTitleResolver.isTransientOverlay(root)) {
            return
        }
        if (ScreenTitleResolver.isDrawerOnlyRoot(root)) {
            return
        }
        val screenTitle = ScreenTitleResolver.resolve(root, event)
        val rawFingerprint = ScreenFingerprint.compute(root, packageName, screenTitle)
        // Ricondotto a un fingerprint già visto se la differenza è solo chrome transitorio
        // (es. collapsing toolbar apparsa/scomparsa per lo scroll) — altrimenti la stessa
        // schermata logica si frammenta in più "schermate" fasulle nel report.
        val fingerprint = ScreenFingerprint.canonicalize(rawFingerprint, seenFingerprintsThisSession)
        val assessment = SecureScreenDetector.assess(root, screenTitle, packageName, capture)
        val contrastBitmap = if (assessment.allowContrast) capture?.bitmap else null
        val result = analyzer.analyzeTree(root, packageName, screenTitle, contrastBitmap, fingerprint)
        val sessionId = repository.currentSessionId()
        val violations = when {
            sessionId == null -> result.violations
            assessment.useSecureEvidence -> {
                val viewport = SecureScreenDetector.rootViewport(root)
                evidenceStore.enrichViolationsSecure(
                    sessionId = sessionId,
                    screenFingerprint = fingerprint,
                    violations = result.violations,
                    viewport = viewport,
                    nearbyBoundsFor = { v ->
                        ViolationBoundsParser.rect(v)?.let { focus ->
                            SecureScreenDetector.collectNearbyBounds(root, focus)
                        } ?: emptyList()
                    },
                )
            }
            contrastBitmap != null ->
                evidenceStore.enrichViolations(sessionId, fingerprint, contrastBitmap, result.violations)
            else ->
                evidenceStore.backfillViolations(sessionId, fingerprint, result.violations)
        }
        repository.addViolations(violations)
        repository.addCheckSummaries(result.checkSummaries)
        if (repository.currentScanScope().includes(ViolationArea.SCREEN_READER)) {
            repository.addScreenReaderFindings(result.screenReaderFindings)
        }
        repository.incrementScanAnalysis()
        val isNewScreen = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            !seenFingerprintsThisSession.contains(fingerprint)
        if (isNewScreen) {
            seenFingerprintsThisSession.add(fingerprint)
            repository.registerUniqueScreen(fingerprint, screenTitle, assessment.reason)
        }
        AppFileLogger.info(
            "A11yService",
            "analyzed pkg=$packageName screen=$screenTitle protection=${assessment.reason} violations=${result.violations.size}",
        )
    }
}
