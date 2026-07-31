package dev.accessscope.scanner.ui.viewmodel

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.EvidenceKind
import dev.accessscope.scanner.data.ScreenProtectionReason
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.data.VisitedScreen
import dev.accessscope.scanner.report.DynamicReportHelper
import dev.accessscope.scanner.report.DynamicScreenFrame
import dev.accessscope.scanner.report.SessionComparisonHelper
import dev.accessscope.scanner.util.ScanEvidenceStore
import dev.accessscope.scanner.util.ScanHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestisce cronologia sessioni, lookup violazioni e report dinamico.
 */
internal class ScanHistoryController(
    private val repository: ScanSessionRepository,
    private val scanHistoryStore: ScanHistoryStore,
    private val scanEvidenceStore: ScanEvidenceStore,
    private val uiState: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
) {

    fun refreshScanHistory(packages: Set<String> = uiState.value.selectedPackages) {
        val primary = packages.firstOrNull()
        if (primary == null) {
            uiState.update {
                it.copy(
                    latestArchivedSession = null,
                    sessionComparison = null,
                    historyPackageName = null,
                )
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val latest = scanHistoryStore.getLatest(primary)
            val previous = scanHistoryStore.getPrevious(primary)
            val comparison = SessionComparisonHelper.compareLatestWithPrevious(latest, previous)
            withContext(Dispatchers.Main) {
                uiState.update {
                    it.copy(
                        latestArchivedSession = latest,
                        sessionComparison = comparison,
                        historyPackageName = primary,
                    )
                }
            }
        }
    }

    /**
     * Elimina tutta la cronologia scansioni e azzera lo stato UI collegato.
     */
    fun clearHistory() {
        scope.launch(Dispatchers.IO) {
            scanHistoryStore.clearAll()
            withContext(Dispatchers.Main) {
                uiState.update {
                    it.copy(
                        latestArchivedSession = null,
                        sessionComparison = null,
                    )
                }
            }
        }
    }

    fun getScanHistory(packageName: String): List<ArchivedScanSession> =
        scanHistoryStore.getHistory(packageName)

    fun getArchivedSession(sessionId: String): ArchivedScanSession? =
        scanHistoryStore.getSession(sessionId)

    fun findViolation(dedupeKey: String, sessionId: String? = null): AccessibilityViolation? {
        if (!sessionId.isNullOrBlank()) {
            return getArchivedSession(sessionId)?.violations?.find { it.dedupeKey == dedupeKey }
        }
        return repository.findViolation(dedupeKey)
    }

    fun resolveEvidencePath(violation: AccessibilityViolation, sessionId: String? = null): String? {
        violation.evidenceImagePath?.let { path ->
            if (java.io.File(path).exists()) return path
        }
        val sid = sessionId
            ?: repository.currentSessionId()
            ?: uiState.value.scanState.sessionId
            ?: scanHistoryStore.getLatest(violation.packageName)?.id
            ?: return null
        return scanEvidenceStore.annotateOnDemand(sid, violation)
    }

    fun packageLabel(packageName: String): String =
        uiState.value.apps.find { it.packageName == packageName }?.label ?: packageName

    fun currentSessionId(): String? = repository.currentSessionId()

    fun buildDynamicReport(sessionId: String? = null): List<DynamicScreenFrame> {
        val includeLow = uiState.value.includeLowConfidenceFindings
        if (!sessionId.isNullOrBlank()) {
            val archived = getArchivedSession(sessionId) ?: return emptyList()
            return DynamicReportHelper.buildFrames(
                visitedScreens = buildVisitedScreensFromArchived(archived),
                violations = archived.violations,
                talkBackFindings = archived.screenReaderFindings,
                checkSummaries = emptyList(),
                screenEvidenceIdResolver = scanEvidenceStore::screenEvidenceId,
                includeLowConfidence = includeLow,
            )
        }
        val scan = uiState.value.scanState
        return DynamicReportHelper.buildFrames(
            visitedScreens = scan.visitedScreens,
            violations = scan.violations,
            talkBackFindings = scan.screenReaderFindings,
            checkSummaries = scan.checkSummaries,
            screenEvidenceIdResolver = scanEvidenceStore::screenEvidenceId,
            includeLowConfidence = includeLow,
        )
    }

    /**
     * @param maxDimPx Lato massimo del bitmap decodificato; `<= 0` = full-res.
     *   Il report dinamico passa un limite (~schermo) per evitare accumulo di
     *   bitmap full-res in memoria.
     */
    fun loadScreenBitmapForFrame(
        frame: DynamicScreenFrame,
        sessionId: String? = null,
        maxDimPx: Int = 0,
    ): android.graphics.Bitmap? {
        val sid = sessionId
            ?: repository.currentSessionId()
            ?: uiState.value.scanState.sessionId
            ?: return null
        val screenBitmap = scanEvidenceStore.loadScreenBitmap(sid, frame.screenEvidenceId, maxDimPx)
        if (screenBitmap != null) return screenBitmap
        if (frame.protectionReason == ScreenProtectionReason.FLAG_SECURE ||
            frame.protectionReason == ScreenProtectionReason.PIN_OR_PASSWORD
        ) {
            val wireframePath = frame.violations
                .firstOrNull { it.evidenceKind == EvidenceKind.SYNTHETIC_SECURE }
                ?.evidenceImagePath
                ?: frame.violations.firstOrNull()?.evidenceImagePath
            wireframePath?.let { path ->
                if (java.io.File(path).exists()) {
                    return android.graphics.BitmapFactory.decodeFile(path)
                }
            }
        }
        return null
    }

    private fun buildVisitedScreensFromArchived(session: ArchivedScanSession): List<VisitedScreen> {
        if (session.visitedScreens.isNotEmpty()) return session.visitedScreens
        val fingerprints = session.violations
            .mapNotNull { it.screenFingerprint }
            .distinct()
        if (fingerprints.isNotEmpty()) {
            return fingerprints.mapIndexed { index, fingerprint ->
                val title = session.violations
                    .firstOrNull { it.screenFingerprint == fingerprint }
                    ?.screenTitle
                    ?: "Schermata ${index + 1}"
                VisitedScreen(
                    fingerprint = fingerprint,
                    title = title,
                    visitIndex = index,
                )
            }
        }
        return session.violations
            .map { it.screenTitle }
            .distinct()
            .mapIndexed { index, title ->
                VisitedScreen(
                    fingerprint = "title::$title",
                    title = title,
                    visitIndex = index,
                )
            }
    }
}
