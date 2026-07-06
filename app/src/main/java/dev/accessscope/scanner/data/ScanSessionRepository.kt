/**
 * Repository centralizzato per lo stato di una sessione di scansione.
 *
 * Gestisce violazioni, schermate visitate, controlli superati e persistenza
 * su [android.content.SharedPreferences] per ripristinare scansioni interrotte.
 */
package dev.accessscope.scanner.data

import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.report.ReportHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    /** Callback invocata quando l'overlay richiede l'arresto della scansione. */
    var stopCallback: (() -> Unit)? = null

    /**
     * Ripristina una scansione precedentemente interrotta dalle preferenze.
     *
     * @return `true` se è stata trovata e ripristinata una scansione in corso.
     */
    fun restorePersistedScan(): Boolean {
        if (!prefs.getBoolean(KEY_SCANNING, false)) return false
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()
        if (packages.isEmpty()) {
            clearPersistedScan()
            return false
        }
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = packages,
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
        violationKeys.clear()
        screenReaderKeys.clear()
        seenFingerprints.clear()
        scannedScreenTitles.clear()
        _state.value = ScanSessionState(
            isScanning = true,
            selectedPackages = selectedPackages,
            scanScope = scanScope,
            lastPdfPath = null,
            errorMessage = null,
            checkSummaries = emptyList(),
            liveSnapshot = null,
        )
        prefs.edit()
            .putBoolean(KEY_SCANNING, true)
            .putStringSet(KEY_PACKAGES, selectedPackages)
            .apply()
    }

    /** Arresta la scansione corrente e cancella lo stato persistito. */
    fun stopScan() {
        _state.update { it.copy(isScanning = false) }
        clearPersistedScan()
    }

    private fun clearPersistedScan() {
        prefs.edit()
            .remove(KEY_SCANNING)
            .remove(KEY_PACKAGES)
            .apply()
    }

    /**
     * Aggiunge violazioni alla sessione, applicando filtri e deduplicazione.
     *
     * @param violations Elenco di violazioni da registrare.
     */
    fun addViolations(violations: List<AccessibilityViolation>) {
        val filtered = ReportHelper.filterViolations(violations)
        val newOnes = filtered.filter { violationKeys.add(it.dedupeKey) }
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
        val newOnes = findings.filter {
            val key = "${it.packageName}|${it.screenTitle}|${it.reportSection}|${it.nodeClassName}|${it.issue}|${it.viewId}"
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
     */
    fun registerUniqueScreen(fingerprint: String, screenTitle: String) {
        if (fingerprint.isBlank()) return
        scannedScreenTitles[fingerprint] = screenTitle
        if (!seenFingerprints.add(fingerprint)) {
            _state.update { it.copy(visitedScreenTitles = scannedScreenTitles.values.toList()) }
            return
        }
        _state.update {
            it.copy(
                uniqueScreens = it.uniqueScreens + 1,
                visitedScreenTitles = scannedScreenTitles.values.toList(),
            )
        }
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

    /**
     * Aggiorna l'istantanea live mostrata nel pannello debug.
     *
     * @param snapshot Dati dell'ultima analisi; `null` per azzerare.
     */
    fun updateLiveSnapshot(snapshot: LiveScanSnapshot?) {
        _state.update { it.copy(liveSnapshot = snapshot) }
    }

    /** Propaga la richiesta di arresto dall'overlay al [stopCallback] registrato. */
    fun requestStopFromOverlay() {
        stopCallback?.invoke()
    }

    companion object {
        private const val PREFS_NAME = "access_scope_scan"
        private const val KEY_SCANNING = "is_scanning"
        private const val KEY_PACKAGES = "selected_packages"
    }
}
