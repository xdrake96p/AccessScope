/**
 * Repository centralizzato per lo stato di una sessione di scansione.
 *
 * Gestisce violazioni, schermate visitate, controlli superati e persistenza
 * su [android.content.SharedPreferences] per ripristinare scansioni interrotte.
 */
package dev.accessscope.scanner.data

import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Fonte di verità per lo stato della scansione di accessibilità.
 *
 * Espone un [state] osservabile e offre metodi per avviare, arrestare
 * e arricchire la sessione con violazioni, schermate e report.
 *
 * @param context Contesto Android per l'accesso alle preferenze condivise.
 */
class ScanSessionRepository(context: android.content.Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ScanSessionState())
    /** Flusso osservabile dello stato corrente della sessione di scansione. */
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    private val violationKeys = LinkedHashSet<String>()
    private val screenReaderKeys = LinkedHashSet<String>()
    private val seenFingerprints = LinkedHashSet<String>()
    private val scannedScreenTitles = LinkedHashMap<String, String>()
    private var activeSessionId: String? = null

    /** Callback invocata quando l'overlay richiede l'arresto della scansione. */
    var stopCallback: (() -> Unit)? = null

    /**
     * Ripristina una scansione precedentemente interrotta dalle preferenze.
     *
     * @return `true` se è stata trovata e ripristinata una scansione in corso.
     */
    fun restorePersistedScan(): Boolean {
        if (!prefs.getBoolean(KEY_SCANNING, false)) return false
        val packages = AppSelectionPolicy.enforceMax(
            prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty(),
        )
        if (packages.isEmpty()) {
            clearPersistedScan()
            return false
        }
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = packages,
        )
        AppFileLogger.log(
            "SCAN",
            "ScanSession",
            "restore_persisted",
            mapOf("packages" to packages.joinToString()),
        )
        return true
    }

    /**
     * Avvia una nuova sessione di scansione sui package indicati.
     *
     * Azzera i contatori e le liste accumulate e persiste lo stato su disco.
     *
     * @param selectedPackages Set di package Android da analizzare.
     * @param scanScope Ambito tematico dei controlli da eseguire.
     */
    fun startScan(selectedPackages: Set<String>, scanScope: ScanScope = ScanScope.FULL) {
        val packages = AppSelectionPolicy.enforceMax(selectedPackages)
        if (packages.isEmpty()) return
        violationKeys.clear()
        screenReaderKeys.clear()
        seenFingerprints.clear()
        scannedScreenTitles.clear()
        activeSessionId = UUID.randomUUID().toString()
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = packages,
            scanScope = scanScope,
            lastPdfPath = null,
            errorMessage = null,
            checkSummaries = emptyList(),
            sessionId = activeSessionId,
        )
        prefs.edit()
            .putBoolean(KEY_SCANNING, true)
            .putStringSet(KEY_PACKAGES, packages)
            .putString(KEY_SESSION_ID, activeSessionId)
            .apply()
        AppFileLogger.log(
            "SCAN",
            "ScanSession",
            "start",
            mapOf(
                "packages" to packages.joinToString(),
                "requestedCount" to selectedPackages.size,
                "scope" to scanScope.label(),
            ),
        )
    }

    /** Arresta la scansione corrente e cancella lo stato persistito. */
    fun stopScan() {
        val packages = _state.value.selectedPackages
        val violations = _state.value.violations.size
        val screens = _state.value.uniqueScreens
        _state.update { it.copy(isScanning = false) }
        activeSessionId = null
        clearPersistedScan()
        AppFileLogger.log(
            "SCAN",
            "ScanSession",
            "stop",
            mapOf(
                "packages" to packages.joinToString(),
                "violations" to violations,
                "screens" to screens,
            ),
        )
    }

    private fun clearPersistedScan() {
        prefs.edit()
            .remove(KEY_SCANNING)
            .remove(KEY_PACKAGES)
            .remove(KEY_SESSION_ID)
            .apply()
    }

    /** ID sessione corrente (per evidenze visive in cache). */
    fun currentSessionId(): String? = _state.value.sessionId ?: activeSessionId

    /** Trova una violazione nella sessione live per [dedupeKey]. */
    fun findViolation(dedupeKey: String): AccessibilityViolation? =
        _state.value.violations.find { it.dedupeKey == dedupeKey }

    /**
     * Aggiunge violazioni alla sessione, applicando filtri e deduplicazione.
     *
     * @param violations Elenco di violazioni da registrare.
     */
    fun addViolations(violations: List<AccessibilityViolation>) {
        val filtered = ReportHelper.filterViolations(violations)
        val newOnes = filtered.filter { violationKeys.add(it.dedupeKey) }
        val skipped = filtered.size - newOnes.size
        // #region agent log
        if (filtered.isNotEmpty()) {
            val newDetails = newOnes.joinToString(" || ") { v ->
                "${v.type.name}|${v.viewId?.substringAfterLast('/') ?: "no-id"}|${v.screenTitle}|${v.bounds?.take(40) ?: "-"}|${v.dedupeKey}"
            }
            DebugTrace.log(
                hypothesisId = if (newOnes.isNotEmpty()) "H1-H2" else "H-DEDUPE",
                location = "ScanSessionRepository.addViolations",
                message = if (newOnes.isNotEmpty()) "violation_added" else "violation_skipped_only",
                data = mapOf(
                    "rawInPass" to violations.size,
                    "afterConfidenceFilter" to filtered.size,
                    "addedNew" to newOnes.size,
                    "skippedDuplicate" to skipped,
                    "sessionViolations" to (_state.value.violations.size + newOnes.size),
                    "sessionTalkBack" to _state.value.screenReaderFindings.size,
                    "sessionScreens" to _state.value.uniqueScreens,
                    "sessionAnalyses" to _state.value.scanAnalyses,
                    "newDetails" to newDetails,
                ),
            )
        }
        // #endregion
        if (newOnes.isEmpty()) return
        _state.update { current ->
            current.copy(violations = current.violations + newOnes)
        }
    }

    /**
     * Aggiunge risultati della simulazione screen reader, deduplicandoli per chiave composta.
     *
     * @param findings Elenco di risultati TalkBack da registrare.
     */
    fun addScreenReaderFindings(findings: List<ScreenReaderFinding>) {
        if (findings.isEmpty()) return
        val sessionBefore = _state.value.screenReaderFindings.size
        val newOnes = findings.filter {
            val normalizedViewId = it.viewId?.let { id -> ViolationDedupeRules.normalizeViewId(id) }
            val labelToken = it.announcedText?.let { t -> ViolationDedupeRules.normalizeElementLabel(t) }
            val key = buildString {
                append(it.packageName)
                append('|')
                append(it.screenTitle)
                append('|')
                append(it.reportSection)
                append('|')
                append(it.nodeClassName.substringAfterLast('.'))
                append('|')
                append(it.issue)
                append('|')
                append(normalizedViewId ?: labelToken ?: "no-id")
            }
            screenReaderKeys.add(key)
        }
        val skipped = findings.size - newOnes.size
        // #region agent log
        if (newOnes.isNotEmpty() || skipped > 0) {
            DebugTrace.log(
                hypothesisId = "H3",
                location = "ScanSessionRepository.addScreenReaderFindings",
                message = if (newOnes.isNotEmpty()) "talkback_added" else "talkback_skipped_only",
                data = mapOf(
                    "rawInPass" to findings.size,
                    "addedNew" to newOnes.size,
                    "skippedDuplicate" to skipped,
                    "sessionTalkBack" to (sessionBefore + newOnes.size),
                    "sessionViolations" to _state.value.violations.size,
                    "newIssues" to newOnes.take(3).joinToString(" || ") {
                        "${it.screenTitle}|${it.viewId?.substringAfterLast('/') ?: "no-id"}|${it.issue.take(40)}"
                    },
                ),
            )
        }
        // #endregion
        if (newOnes.isEmpty()) return
        _state.update { current ->
            current.copy(screenReaderFindings = current.screenReaderFindings + newOnes)
        }
    }

    /** Incrementa il contatore di analisi dell'albero di accessibilità eseguite. */
    fun incrementScanAnalysis() {
        _state.update { it.copy(scanAnalyses = it.scanAnalyses + 1) }
    }

    /**
     * Registra una schermata univoca tramite impronta e titolo.
     *
     * Aggiorna [ScanSessionState.uniqueScreens] solo alla prima occorrenza
     * di ciascuna impronta.
     *
     * @param fingerprint Impronta univoca della schermata.
     * @param screenTitle Titolo leggibile della schermata.
     */
    fun registerUniqueScreen(fingerprint: String, screenTitle: String) {
        if (fingerprint.isBlank()) return
        scannedScreenTitles[fingerprint] = screenTitle
        if (!seenFingerprints.add(fingerprint)) {
            _state.update { it.copy(visitedScreenTitles = scannedScreenTitles.values.toList()) }
            return
        }
        val screensBefore = _state.value.uniqueScreens
        _state.update {
            it.copy(
                uniqueScreens = it.uniqueScreens + 1,
                visitedScreenTitles = scannedScreenTitles.values.toList(),
            )
        }
        // #region agent log
        DebugTrace.log(
            hypothesisId = "H4",
            location = "ScanSessionRepository.registerUniqueScreen",
            message = "screen_count_incremented",
            data = mapOf(
                "screenTitle" to screenTitle,
                "fingerprintPrefix" to fingerprint.take(100),
                "uniqueScreens" to (screensBefore + 1),
            ),
        )
        // #endregion
    }

    /**
     * Aggiunge riepiloghi di controlli superati, fondendoli con quelli esistenti.
     *
     * @param summaries Elenco di riepiloghi da unire allo stato corrente.
     */
    fun addCheckSummaries(summaries: List<CheckAreaSummary>) {
        if (summaries.isEmpty()) return
        _state.update { current ->
            current.copy(
                checkSummaries = CheckCollector.merge(current.checkSummaries + summaries),
            )
        }
    }

    /**
     * Imposta il percorso dell'ultimo report PDF generato.
     *
     * @param path Percorso del file PDF, oppure `null` per azzerarlo.
     */
    fun setPdfPath(path: String?) {
        _state.update { it.copy(lastPdfPath = path, errorMessage = null) }
    }

    /** Imposta il percorso del report Markdown di affidabilità (debug). */
    fun setReliabilityMdPath(path: String?) {
        _state.update { it.copy(lastReliabilityMdPath = path) }
    }

    /**
     * Registra un messaggio di errore nella sessione corrente.
     *
     * @param message Testo dell'errore da mostrare all'utente.
     */
    fun setError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    /**
     * Verifica se un package fa parte dei target della scansione attiva.
     *
     * @param packageName Package da verificare.
     * @return `true` se la scansione è attiva e il package è tra i selezionati.
     */
    fun isTargetPackage(packageName: String): Boolean {
        val targets = _state.value.selectedPackages
        return targets.isNotEmpty() && packageName in targets
    }

    /**
     * Restituisce l'ambito di scansione configurato per la sessione corrente.
     *
     * @return [ScanScope] attivo nello stato corrente.
     */
    fun currentScanScope(): ScanScope = _state.value.scanScope

    /** Propaga la richiesta di arresto dall'overlay al [stopCallback] registrato. */
    fun requestStopFromOverlay() {
        stopCallback?.invoke()
    }

    companion object {
        private const val PREFS_NAME = "access_scope_scan"
        private const val KEY_SCANNING = "is_scanning"
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_SESSION_ID = "session_id"
    }
}
