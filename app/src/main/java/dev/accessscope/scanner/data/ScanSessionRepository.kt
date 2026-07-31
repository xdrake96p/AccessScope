/**
 * Repository centralizzato per lo stato di una sessione di scansione.
 *
 * Gestisce violazioni, schermate visitate, controlli superati e persistenza
 * su [android.content.SharedPreferences] per ripristinare scansioni interrotte.
 */
package dev.accessscope.scanner.data

import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.ViolationConfidencePolicy
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.util.AppFileLogger
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

    private val persistence = ScanSessionPersistence(
        context.getSharedPreferences(ScanSessionPersistence.PREFS_NAME, android.content.Context.MODE_PRIVATE),
    )

    private val _state = MutableStateFlow(ScanSessionState())
    /** Flusso osservabile dello stato corrente della sessione di scansione. */
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    private val violationKeys = LinkedHashSet<String>()
    private val screenReaderKeys = LinkedHashSet<String>()
    private val seenFingerprints = LinkedHashSet<String>()
    private val scannedScreenTitles = LinkedHashMap<String, String>()
    private val scannedScreenProtection = LinkedHashMap<String, ScreenProtectionReason>()
    private var activeSessionId: String? = null

    private fun buildVisitedScreens(): List<VisitedScreen> =
        scannedScreenTitles.entries.mapIndexed { index, (fingerprint, title) ->
            VisitedScreen(
                fingerprint = fingerprint,
                title = title,
                visitIndex = index,
                protectionReason = scannedScreenProtection[fingerprint] ?: ScreenProtectionReason.NONE,
            )
        }

    private fun syncVisitedScreensState() {
        val screens = buildVisitedScreens()
        _state.update {
            it.copy(
                visitedScreenTitles = screens.map { s -> s.title },
                visitedScreens = screens,
            )
        }
    }

    /** Callback invocata quando l'overlay richiede l'arresto della scansione. */
    var stopCallback: (() -> Unit)? = null

    /**
     * Ripristina una scansione precedentemente interrotta dalle preferenze.
     *
     * @return `true` se è stata trovata e ripristinata una scansione in corso.
     */
    fun restorePersistedScan(): Boolean {
        if (!persistence.hasPersistedScan()) return false
        val packages = persistence.loadPersistedPackages()
        if (packages.isEmpty()) {
            persistence.clear()
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
        scannedScreenProtection.clear()
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
        persistence.persistStart(packages, activeSessionId!!)
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
        persistence.clear()
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

    /** ID sessione corrente (per evidenze visive in cache). */
    fun currentSessionId(): String? = _state.value.sessionId ?: activeSessionId

    /** Trova una violazione nella sessione live per [dedupeKey]. */
    fun findViolation(dedupeKey: String): AccessibilityViolation? =
        _state.value.violations.find { it.dedupeKey == dedupeKey }

    /**
     * Aggiunge violazioni alla sessione, applicando demotion di confidenza e deduplicazione.
     *
     * Non applica la soglia di confidenza (quella è responsabilità del report, via
     * [dev.accessscope.scanner.report.ReportHelper.filterViolations] con il toggle
     * "findings a bassa confidenza"): filtrare qui le scarterebbe in modo permanente,
     * rendendo quel toggle inefficace su sessioni live/archiviate.
     *
     * @param violations Elenco di violazioni da registrare.
     */
    fun addViolations(violations: List<AccessibilityViolation>) {
        val demoted = violations.map { ViolationConfidencePolicy.demoteIfNoisy(it) }
            .distinctBy { it.dedupeKey }
        val newOnes = demoted.filter { violationKeys.add(it.dedupeKey) }
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
     * @param protectionReason Motivo di protezione screenshot/contrasto per questa schermata.
     */
    fun registerUniqueScreen(
        fingerprint: String,
        screenTitle: String,
        protectionReason: ScreenProtectionReason = ScreenProtectionReason.NONE,
    ) {
        if (fingerprint.isBlank()) return
        val isNew = !seenFingerprints.contains(fingerprint)
        scannedScreenTitles[fingerprint] = screenTitle
        scannedScreenProtection[fingerprint] = protectionReason
        if (!isNew) {
            syncVisitedScreensState()
            return
        }
        seenFingerprints.add(fingerprint)
        val screensBefore = _state.value.uniqueScreens
        syncVisitedScreensState()
        _state.update { it.copy(uniqueScreens = screensBefore + 1) }
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
}
