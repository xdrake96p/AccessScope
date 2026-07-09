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
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.report.SessionComparison
import dev.accessscope.scanner.report.SessionComparisonHelper
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.AppIconCache
import dev.accessscope.scanner.util.AppLaunchHelper
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugTrace
import dev.accessscope.scanner.util.PackageHelper
import dev.accessscope.scanner.util.PermissionHelper
import dev.accessscope.scanner.util.ThemePreferencesStore
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stato immutabile dell'interfaccia della schermata Home.
 *
 * @param apps Elenco delle app installate sul dispositivo, arricchite con metadati e ordinamento.
 * @param selectedPackages Set dei package name selezionati per il monitoraggio durante la scansione.
 * @param favoritePackages Set dei package name contrassegnati come preferiti.
 * @param scanState Stato corrente della sessione di scansione (violazioni, schermate, PDF, ecc.).
 * @param accessibilityGranted Indica se il servizio di accessibilità AccessScope è abilitato nelle impostazioni di sistema.
 * @param accessibilityConnected Indica se il servizio di accessibilità è attualmente connesso e in esecuzione.
 * @param overlayGranted Indica se è stato concesso il permesso di disegnare sopra le altre app.
 * @param isLoadingApps True mentre l'elenco app viene caricato in background.
 * @param includeSystemApps Se true, include anche le app di sistema nell'elenco.
 * @param autoLaunchEnabled Se true, all'avvio della scansione viene aperta automaticamente la prima app selezionata.
 * @param scanScope Ambiti di analisi attivi per la prossima sessione (etichette, contrasto, TalkBack, ecc.).
 * @param statusMessage Messaggio temporaneo da mostrare all'utente (es. errori, conferme); null se assente.
 * @param themeMode Preferenza tema interfaccia (chiaro, scuro o sistema).
 * @param reliabilityReportEnabled Se true, genera report Markdown di affidabilità a fine scansione.
 * @param latestArchivedSession Ultima sessione archiviata per l'app principale selezionata.
 * @param sessionComparison Confronto numerico ultima vs penultima sessione archiviata.
 * @param historyPackageName Package usato per cronologia e confronto.
 * @param selectionLimitDialog Dialog da mostrare quando si supera il limite di app monitorabili.
 */
data class AppSelectionLimitDialog(
    val message: String,
    val blockedPackage: String? = null,
)

data class HomeUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val favoritePackages: Set<String> = emptySet(),
    val scanState: ScanSessionState = ScanSessionState(),
    val accessibilityGranted: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val overlayGranted: Boolean = false,
    val isLoadingApps: Boolean = true,
    val includeSystemApps: Boolean = false,
    val autoLaunchEnabled: Boolean = false,
    val scanScope: ScanScope = ScanScope.FULL,
    val statusMessage: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val reliabilityReportEnabled: Boolean = false,
    val latestArchivedSession: ArchivedScanSession? = null,
    val sessionComparison: SessionComparison? = null,
    val historyPackageName: String? = null,
    val selectionLimitDialog: AppSelectionLimitDialog? = null,
)

/**
 * Slice UI per l'elenco app — non include [ScanSessionState] per evitare recomposition durante la scansione.
 */
data class AppListUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val isLoadingApps: Boolean = true,
    val includeSystemApps: Boolean = false,
    val autoLaunchEnabled: Boolean = false,
)

/**
 * Slice UI per dashboard e barra azioni legata alla sessione di scansione.
 */
data class ScanDashboardUiState(
    val scanState: ScanSessionState = ScanSessionState(),
    val selectedPackages: Set<String> = emptySet(),
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val latestArchivedSession: ArchivedScanSession? = null,
    val sessionComparison: SessionComparison? = null,
    val historyPackageName: String? = null,
)

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

    private val _uiState = MutableStateFlow(HomeUiState())
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
        logSelection(
            "init",
            mapOf(
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
                    refreshScanHistory(scanState.selectedPackages)
                }
            }
        }
        refreshScanHistory(_uiState.value.selectedPackages)
        refreshPermissions()
        _uiState.update {
            it.copy(
                autoLaunchEnabled = scanSettingsStore.autoLaunchEnabled,
                scanScope = scanSettingsStore.getScanScope(),
                themeMode = themePreferencesStore.getThemeMode(),
                reliabilityReportEnabled = scanSettingsStore.reliabilityReportEnabled,
            )
        }
        loadApps()
    }

    /** Inverte l'export del report Markdown di affidabilità (debug interno). */
    fun toggleReliabilityReport() {
        val enabled = !_uiState.value.reliabilityReportEnabled
        scanSettingsStore.reliabilityReportEnabled = enabled
        _uiState.update { it.copy(reliabilityReportEnabled = enabled) }
    }

    /** Inverte lo stato dell'opzione "apri app automaticamente" e lo persiste nelle impostazioni. */
    fun toggleAutoLaunch() {
        val state = _uiState.value
        val enabled = !state.autoLaunchEnabled
        if (enabled) {
            val trimmed = AppSelectionPolicy.enforceMax(state.selectedPackages)
            scanSettingsStore.autoLaunchEnabled = true
            _uiState.update {
                it.copy(
                    autoLaunchEnabled = true,
                    selectedPackages = trimmed,
                    statusMessage = if (trimmed.size < state.selectedPackages.size) {
                        "Lancio automatico attivo: selezione limitata a $MAX_APPS_WITH_AUTO_LAUNCH app."
                    } else {
                        it.statusMessage
                    },
                )
            }
            logSelection("toggle_auto_launch", mapOf("enabled" to true, "trimmed" to trimmed.joinToString()))
            return
        }
        scanSettingsStore.autoLaunchEnabled = enabled
        _uiState.update { it.copy(autoLaunchEnabled = enabled) }
        logSelection("toggle_auto_launch", mapOf("enabled" to enabled))
    }

    /**
     * Verifica se si può aggiungere un'app alla selezione rispettando il limite auto-launch.
     */
    private fun autoLaunchLimitMessage() = AppSelectionPolicy.limitMessage()

    /**
     * Attiva o disattiva un singolo ambito di scansione.
     *
     * @param area Ambito da includere o escludere; almeno un ambito deve restare attivo.
     */
    fun toggleScanArea(area: ViolationArea) {
        val current = _uiState.value.scanScope.enabledAreas.toMutableSet()
        if (area in current) {
            if (current.size == 1) {
                _uiState.update { it.copy(statusMessage = "Almeno un ambito deve restare attivo.") }
                return
            }
            current.remove(area)
        } else {
            current.add(area)
        }
        applyScanScope(ScanScope(current))
    }

    /** Imposta la scansione completa con tutti gli ambiti abilitati. */
    fun setFullScan() = applyScanScope(ScanScope.FULL)

    /** Applica il preset che analizza solo la simulazione TalkBack. */
    fun applyTalkBackOnlyPreset() = applyScanScope(ScanScope.talkBackOnly())

    /** Applica il preset che analizza solo etichette e descrizioni. */
    fun applyLabelsOnlyPreset() = applyScanScope(ScanScope.labelsOnly())

    /** Applica il preset che analizza solo contrasto e colori. */
    fun applyContrastOnlyPreset() = applyScanScope(ScanScope.contrastOnly())

    /**
     * Persiste e applica un nuovo ambito di scansione nello stato UI.
     *
     * @param scope Configurazione degli ambiti abilitati.
     */
    private fun applyScanScope(scope: ScanScope) {
        scanSettingsStore.setScanScope(scope)
        _uiState.update { it.copy(scanScope = scope) }
    }

    /** Rilegge dallo stato di sistema i permessi di accessibilità e overlay. */
    fun refreshPermissions() {
        val context = getApplication<Application>()
        val a11yEnabled = PermissionHelper.isAccessibilityServiceEnabled(
            context,
            AccessScopeAccessibilityService::class.java,
        )
        val a11yConnected = PermissionHelper.isAccessibilityServiceConnected(
            context,
            AccessScopeAccessibilityService::class.java,
        )
        // #region agent log
        DebugTrace.log("H1", "ViewModel.refreshPermissions", "state", mapOf(
            "enabled" to a11yEnabled,
            "connected" to a11yConnected,
        ))
        // #endregion
        _uiState.update {
            it.copy(
                accessibilityGranted = a11yEnabled,
                accessibilityConnected = a11yConnected,
                overlayGranted = PermissionHelper.canDrawOverlays(context),
            )
        }
    }

    /** Carica l'elenco delle app installate, applica preferiti e precarica le icone. */
    fun loadApps() {
        viewModelScope.launch {
            val includeSystem = _uiState.value.includeSystemApps
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = withContext(Dispatchers.IO) {
                PackageHelper.loadInstalledApps(getApplication(), includeSystem)
            }
            val storedFavorites = favoriteAppsStore.getFavorites()
            val installedPackages = apps.map { it.packageName }.toSet()
            val beforeSelected = _uiState.value.selectedPackages
            val primaryFavorite = apps.firstOrNull { it.packageName in storedFavorites }?.packageName
            val (favorites, selected) = AppSelectionPolicy.sanitizeAgainstInstalled(
                selected = beforeSelected,
                favorites = storedFavorites,
                installed = installedPackages,
                preferredPrimary = primaryFavorite,
            )
            if (favorites != storedFavorites) {
                favoriteAppsStore.setFavorites(favorites)
            }
            val enriched = apps.map { app ->
                app.copy(isFavorite = app.packageName in favorites)
            }.sortedWith(
                compareByDescending<InstalledAppInfo> { it.isFavorite }
                    .thenBy { !it.isSystemApp }
                    .thenBy { it.label.lowercase() },
            )
            val pm = getApplication<Application>().packageManager
            withContext(Dispatchers.Default) {
                enriched.chunked(48).forEach { chunk ->
                    AppIconCache.preload(pm, chunk.map { it.packageName })
                }
            }
            _uiState.update { state ->
                state.copy(
                    apps = enriched,
                    favoritePackages = favorites,
                    selectedPackages = selected,
                    isLoadingApps = false,
                )
            }
            logSelection(
                "load_apps_done",
                mapOf(
                    "appCount" to enriched.size,
                    "favoriteCount" to favorites.size,
                    "selected" to _uiState.value.selectedPackages.joinToString(),
                ),
            )
        }
    }

    /**
     * Aggiunge o rimuove un'app dai preferiti e aggiorna la selezione di conseguenza.
     *
     * @param packageName Identificativo del pacchetto Android dell'app.
     */
    fun toggleFavorite(packageName: String) {
        val stateBefore = _uiState.value
        val wasFavorite = packageName in stateBefore.favoritePackages
        val favorites = favoriteAppsStore.toggle(packageName)
        val isNowFavorite = packageName in favorites
        _uiState.update { state ->
            val selected = when {
                isNowFavorite -> AppSelectionPolicy.selectOnFavoriteAdded(packageName)
                else -> AppSelectionPolicy.selectOnFavoriteRemoved(state.selectedPackages, packageName)
            }
            state.copy(
                favoritePackages = favorites,
                selectedPackages = selected,
                selectionLimitDialog = null,
                apps = state.apps.map { app ->
                    app.copy(isFavorite = app.packageName in favorites)
                }.sortedWith(
                    compareByDescending<InstalledAppInfo> { it.isFavorite }
                        .thenBy { !it.isSystemApp }
                        .thenBy { it.label.lowercase() },
                ),
            )
        }
        logSelection(
            "toggle_favorite",
            mapOf(
                "package" to packageName,
                "wasFavorite" to wasFavorite,
                "isFavorite" to isNowFavorite,
                "selectedAfter" to _uiState.value.selectedPackages.joinToString(),
            ),
        )
        refreshScanHistory(_uiState.value.selectedPackages)
    }

    /** Inverte il filtro per mostrare o nascondere le app di sistema e ricarica l'elenco. */
    fun toggleIncludeSystemApps() {
        _uiState.update { it.copy(includeSystemApps = !it.includeSystemApps) }
        loadApps()
    }

    /**
     * Inverte la selezione di monitoraggio per una singola app.
     *
     * @param packageName Identificativo del pacchetto Android dell'app.
     */
    fun toggleApp(packageName: String) {
        val stateBefore = _uiState.value
        if (packageName in stateBefore.selectedPackages &&
            AppSelectionPolicy.isFavoriteProtectedFromDeselect(packageName, stateBefore.favoritePackages)
        ) {
            logSelection(
                "toggle_app_blocked_favorite",
                mapOf("package" to packageName),
            )
            _uiState.update {
                it.copy(
                    selectionLimitDialog = AppSelectionLimitDialog(
                        message = AppSelectionPolicy.favoriteDeselectBlockedMessage(),
                        blockedPackage = packageName,
                    ),
                )
            }
            return
        }
        _uiState.update { state ->
            when (
                val result = AppSelectionPolicy.toggleSelection(
                    current = state.selectedPackages,
                    packageName = packageName,
                    replaceOnLimit = state.autoLaunchEnabled,
                )
            ) {
                is AppSelectionPolicy.ToggleResult.Updated ->
                    state.copy(selectedPackages = result.selected, selectionLimitDialog = null)
                is AppSelectionPolicy.ToggleResult.LimitReached ->
                    state.copy(
                        selectionLimitDialog = AppSelectionLimitDialog(
                            message = result.message,
                            blockedPackage = result.blockedPackage,
                        ),
                    )
            }
        }
        logSelection(
            "toggle_app",
            mapOf(
                "package" to packageName,
                "selectedAfter" to _uiState.value.selectedPackages.joinToString(),
                "dialog" to (_uiState.value.selectionLimitDialog != null),
            ),
        )
        refreshScanHistory(_uiState.value.selectedPackages)
    }

    /** Chiude il dialog mostrato quando si supera il limite di app monitorabili. */
    fun dismissSelectionLimitDialog() {
        _uiState.update { it.copy(selectionLimitDialog = null) }
        logSelection("dismiss_selection_dialog")
    }

    private fun logSelection(event: String, extras: Map<String, Any?> = emptyMap()) {
        val state = _uiState.value
        AppFileLogger.log(
            hypothesisId = "SEL",
            location = "AppSelection",
            message = event,
            data = extras + mapOf(
                "selected" to state.selectedPackages.joinToString().ifBlank { "-" },
                "favorites" to state.favoritePackages.joinToString().ifBlank { "-" },
                "autoLaunch" to state.autoLaunchEnabled,
                "scanning" to state.scanState.isScanning,
            ),
        )
    }

    /**
     * Avvia una sessione di scansione se permessi e selezione sono validi.
     *
     * Avvia anche l'overlay e, se abilitato, l'apertura automatica della prima app selezionata.
     */
    fun startScan() {
        val context = getApplication<Application>()
        val current = _uiState.value
        val selected = current.selectedPackages
        val monitored = AppSelectionPolicy.enforceMax(selected)
        if (monitored.size != selected.size) {
            logSelection(
                "start_scan_trimmed",
                mapOf(
                    "requested" to selected.joinToString(),
                    "monitored" to monitored.joinToString(),
                ),
            )
            _uiState.update {
                it.copy(
                    selectedPackages = monitored,
                    selectionLimitDialog = AppSelectionLimitDialog(autoLaunchLimitMessage()),
                )
            }
            return
        }
        if (monitored.isEmpty()) {
            logSelection("start_scan_blocked_empty")
            _uiState.update { it.copy(statusMessage = "Seleziona almeno una app da monitorare.") }
            return
        }
        refreshPermissions()
        val state = _uiState.value
        if (!state.accessibilityGranted) {
            logSelection("start_scan_blocked_a11y")
            _uiState.update { it.copy(statusMessage = "Abilita il servizio di accessibilità AccessScope.") }
            return
        }
        if (!state.overlayGranted) {
            logSelection("start_scan_blocked_overlay")
            _uiState.update { it.copy(statusMessage = "Concedi il permesso di sovrapposizione.") }
            return
        }

        repository.startScan(monitored, state.scanScope)
        logSelection(
            "start_scan",
            mapOf(
                "packages" to monitored.joinToString(),
                "scanScope" to state.scanScope.label(),
                "autoLaunch" to state.autoLaunchEnabled,
            ),
        )
        AccessScopeAccessibilityService.instance?.resetDynamicTracking()
        // #region agent log
        DebugTrace.log("H1", "ViewModel.startScan", "scan_requested", mapOf(
            "packages" to monitored.joinToString(","),
            "serviceConnected" to (AccessScopeAccessibilityService.instance != null),
            "accessibilityGranted" to state.accessibilityGranted,
        ))
        // #endregion
        ScanOverlayService.start(context)

        var message = "Scansione avviata. Apri le app selezionate e interagisci."
        if (state.autoLaunchEnabled) {
            val launched = AppLaunchHelper.launchFirstAvailable(context, monitored)
            message = if (launched != null) {
                val label = state.apps.find { it.packageName == launched }?.label ?: launched
                "Scansione avviata. Aperta: $label"
            } else {
                "Scansione avviata. Nessuna app apribile automaticamente — aprila manualmente."
            }
        }
        _uiState.update { it.copy(statusMessage = message) }
    }

    /** Interrompe la sessione di scansione corrente. */
    fun stopScan() {
        logSelection("stop_scan_requested")
        (getApplication<AccessScopeApp>()).stopScanSession(fromOverlay = false)
    }

    /**
     * Esporta i log diagnostici in Download e apre il chooser di condivisione.
     */
    fun exportDiagnosticLogs(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                dev.accessscope.scanner.export.DiagnosticLogExporter(getApplication()).export()
            }
            onResult(result)
        }
    }

    /** Restituisce l'icona bitmap di un'app, caricandola dalla cache se necessario.
     *
     * @param packageName Identificativo del pacchetto Android dell'app.
     * @return Bitmap dell'icona, oppure null se non disponibile.
     */
    fun appIconBitmap(packageName: String): androidx.compose.ui.graphics.ImageBitmap? =
        AppIconCache.getOrLoad(getApplication<Application>().packageManager, packageName)

    /** Seleziona tutte le app attualmente visibili nell'elenco. */
    fun selectAllVisible() {
        _uiState.update { state ->
            val first = state.apps.firstOrNull()?.packageName
            when {
                first == null -> state
                state.selectedPackages.isNotEmpty() -> state.copy(
                    selectionLimitDialog = AppSelectionLimitDialog(autoLaunchLimitMessage()),
                )
                else -> state.copy(selectedPackages = setOf(first))
            }
        }
        logSelection("select_all_visible", mapOf("selected" to _uiState.value.selectedPackages.joinToString()))
    }

    /** Azzera la selezione di monitoraggio. */
    fun clearSelection() {
        _uiState.update { state ->
            state.copy(selectedPackages = emptySet())
        }
        logSelection("clear_selection", mapOf("selected" to _uiState.value.selectedPackages.joinToString()))
        refreshScanHistory(_uiState.value.selectedPackages)
    }

    /** Azzera il messaggio di stato temporaneo mostrato all'utente. */
    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    /**
     * Imposta e persiste la preferenza tema dell'interfaccia.
     *
     * @param mode Modalità scelta (chiaro, scuro o sistema).
     */
    fun setThemeMode(mode: AppThemeMode) {
        themePreferencesStore.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    /**
     * Carica cronologia e confronto per il package principale tra quelli selezionati.
     *
     * @param packages Package attualmente selezionati; usa il primo come chiave cronologia.
     */
    fun refreshScanHistory(packages: Set<String> = _uiState.value.selectedPackages) {
        val primary = packages.firstOrNull()
        if (primary == null) {
            _uiState.update {
                it.copy(
                    latestArchivedSession = null,
                    sessionComparison = null,
                    historyPackageName = null,
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val latest = scanHistoryStore.getLatest(primary)
            val previous = scanHistoryStore.getPrevious(primary)
            val comparison = SessionComparisonHelper.compareLatestWithPrevious(latest, previous)
            withContext(Dispatchers.Main) {
                _uiState.update {
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
     * Restituisce la cronologia archiviata per un package (max 20 sessioni).
     *
     * @param packageName Package Android da consultare.
     */
    fun getScanHistory(packageName: String): List<ArchivedScanSession> =
        scanHistoryStore.getHistory(packageName)

    /**
     * Carica una sessione archiviata per ID.
     *
     * @param sessionId Identificatore univoco della sessione.
     */
    fun getArchivedSession(sessionId: String): ArchivedScanSession? =
        scanHistoryStore.getSession(sessionId)
}
