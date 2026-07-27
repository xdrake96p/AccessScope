/**
 * ViewModel principale per la schermata Home e le operazioni di scansione.
 *
 * Gestisce lo stato dell'interfaccia utente (app installate, selezione, permessi,
 * ambiti di scansione) e coordina avvio/arresto della sessione tramite [dev.accessscope.scanner.data.ScanRepository].
 */
package dev.accessscope.scanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.report.DynamicScreenFrame
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.ui.theme.AppThemeMode
import dev.accessscope.scanner.util.AppFileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel che espone [uiState] e le azioni per configurare e avviare le scansioni di accessibilità.
 *
 * @param application Contesto applicativo usato per accedere a repository, store e package manager.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Massimo numero di app monitorabili per sessione (alias per UI e impostazioni). */
        val MAX_APPS_WITH_AUTO_LAUNCH: Int = AppSelectionPolicy.MAX_MONITORED_APPS
    }

    private val repository = (application as AccessScopeApp).scanRepository
    private val favoriteAppsStore = (application as AccessScopeApp).favoriteAppsStore
    private val scanSettingsStore = (application as AccessScopeApp).scanSettingsStore
    private val themePreferencesStore = (application as AccessScopeApp).themePreferencesStore
    private val scanHistoryStore = (application as AccessScopeApp).scanHistoryStore
    private val scanEvidenceStore = (application as AccessScopeApp).scanEvidenceStore

    private val _uiState = MutableStateFlow(HomeUiState())

    private val historyController = ScanHistoryController(
        repository = repository,
        scanHistoryStore = scanHistoryStore,
        scanEvidenceStore = scanEvidenceStore,
        uiState = _uiState,
        scope = viewModelScope,
    )

    private val appListController = ScanAppListController(
        application = application,
        favoriteAppsStore = favoriteAppsStore,
        scanSettingsStore = scanSettingsStore,
        uiState = _uiState,
        scope = viewModelScope,
        onSelectionChanged = historyController::refreshScanHistory,
    )

    private val sessionController = ScanSessionController(
        application = application,
        repository = repository,
        scanSettingsStore = scanSettingsStore,
        themePreferencesStore = themePreferencesStore,
        uiState = _uiState,
        scope = viewModelScope,
        appListController = appListController,
    )

    /** Flusso osservabile dello stato UI della Home; aggiornato da repository, permessi e azioni utente. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Stato elenco app isolato dalla sessione di scansione — riduce jank in scroll durante scan live.
     */
    val appListUiState: StateFlow<AppListUiState> = _uiState
        .map {
            AppListUiState(
                apps = it.apps,
                selectedPackages = it.selectedPackages,
                isLoadingApps = it.isLoadingApps,
                includeSystemApps = it.includeSystemApps,
                autoLaunchEnabled = it.autoLaunchEnabled,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    /**
     * Stato dashboard/barra scansione — aggiornato frequentemente durante la sessione live.
     */
    val scanDashboardUiState: StateFlow<ScanDashboardUiState> = _uiState
        .map {
            ScanDashboardUiState(
                scanState = it.scanState,
                selectedPackages = it.selectedPackages,
                accessibilityGranted = it.accessibilityGranted,
                overlayGranted = it.overlayGranted,
                latestArchivedSession = it.latestArchivedSession,
                sessionComparison = it.sessionComparison,
                historyPackageName = it.historyPackageName,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanDashboardUiState())

    init {
        val favorites = favoriteAppsStore.getFavorites()
        _uiState.update {
            it.copy(
                favoritePackages = favorites,
                selectedPackages = emptySet(),
            )
        }
        AppFileLogger.log(
            hypothesisId = "SEL",
            location = "AppSelection",
            message = "init",
            data = mapOf(
                "favoritesCount" to favorites.size,
                "initialSelected" to "-",
            ),
        )
        viewModelScope.launch {
            repository.state.collect { scanState ->
                val wasScanning = _uiState.value.scanState.isScanning
                _uiState.update { state ->
                    val status = when {
                        scanState.lastPdfPath != null && scanState.lastReliabilityMdPath != null && !scanState.isScanning ->
                            "Report PDF: ${scanState.lastPdfPath}\nAffidabilità MD: ${scanState.lastReliabilityMdPath}"
                        scanState.lastPdfPath != null && !scanState.isScanning ->
                            "Report salvato in: ${scanState.lastPdfPath}"
                        scanState.lastReliabilityMdPath != null && !scanState.isScanning ->
                            "Report affidabilità: ${scanState.lastReliabilityMdPath}"
                        scanState.errorMessage != null -> scanState.errorMessage
                        else -> state.statusMessage
                    }
                    state.copy(scanState = scanState, statusMessage = status)
                }
                if (wasScanning && !scanState.isScanning) {
                    historyController.refreshScanHistory(scanState.selectedPackages)
                }
            }
        }
        historyController.refreshScanHistory(_uiState.value.selectedPackages)
        sessionController.refreshPermissions()
        sessionController.loadInitialSettings()
        appListController.loadApps()
    }

    fun toggleReliabilityReport() = sessionController.toggleReliabilityReport()

    fun toggleIncludeLowConfidenceFindings() = sessionController.toggleIncludeLowConfidenceFindings()

    fun toggleAutoLaunch() = appListController.toggleAutoLaunch()

    fun toggleScanArea(area: ViolationArea) = sessionController.toggleScanArea(area)

    fun setFullScan() = sessionController.setFullScan()

    fun applyTalkBackOnlyPreset() = sessionController.applyTalkBackOnlyPreset()

    fun applyLabelsOnlyPreset() = sessionController.applyLabelsOnlyPreset()

    fun applyContrastOnlyPreset() = sessionController.applyContrastOnlyPreset()

    fun refreshPermissions() = sessionController.refreshPermissions()

    fun loadApps() = appListController.loadApps()

    fun toggleFavorite(packageName: String) = appListController.toggleFavorite(packageName)

    fun toggleIncludeSystemApps() = appListController.toggleIncludeSystemApps()

    fun toggleApp(packageName: String) = appListController.toggleApp(packageName)

    fun dismissSelectionLimitDialog() = appListController.dismissSelectionLimitDialog()

    fun startScan() = sessionController.startScan()

    fun stopScan() = sessionController.stopScan()

    fun exportDiagnosticLogs(onResult: (Result<String>) -> Unit) =
        sessionController.exportDiagnosticLogs(onResult)

    fun resolveReliabilityMdForFeedback(onResult: (String?) -> Unit) =
        sessionController.resolveReliabilityMdForFeedback(onResult)

    fun appIconBitmap(packageName: String) = appListController.appIconBitmap(packageName)

    fun selectAllVisible() = appListController.selectAllVisible()

    fun clearSelection() = appListController.clearSelection()

    fun clearStatus() = sessionController.clearStatus()

    fun setThemeMode(mode: AppThemeMode) = sessionController.setThemeMode(mode)

    fun refreshScanHistory(packages: Set<String> = _uiState.value.selectedPackages) =
        historyController.refreshScanHistory(packages)

    fun clearScanHistory() = historyController.clearHistory()

    fun getScanHistory(packageName: String): List<ArchivedScanSession> =
        historyController.getScanHistory(packageName)

    fun getArchivedSession(sessionId: String): ArchivedScanSession? =
        historyController.getArchivedSession(sessionId)

    fun findViolation(dedupeKey: String, sessionId: String? = null): AccessibilityViolation? =
        historyController.findViolation(dedupeKey, sessionId)

    fun resolveEvidencePath(violation: AccessibilityViolation, sessionId: String? = null): String? =
        historyController.resolveEvidencePath(violation, sessionId)

    fun packageLabel(packageName: String): String = historyController.packageLabel(packageName)

    fun currentSessionId(): String? = historyController.currentSessionId()

    fun buildDynamicReport(sessionId: String? = null): List<DynamicScreenFrame> =
        historyController.buildDynamicReport(sessionId)

    fun loadScreenBitmapForFrame(frame: DynamicScreenFrame, sessionId: String? = null) =
        historyController.loadScreenBitmapForFrame(frame, sessionId)
}
