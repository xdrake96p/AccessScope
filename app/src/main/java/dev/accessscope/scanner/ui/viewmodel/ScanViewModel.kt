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
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.AppIconCache
import dev.accessscope.scanner.util.AppLaunchHelper
import dev.accessscope.scanner.util.DebugTrace
import dev.accessscope.scanner.util.PackageHelper
import dev.accessscope.scanner.util.PermissionHelper
import dev.accessscope.scanner.util.ThemePreferencesStore
import dev.accessscope.scanner.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @param liveDebugPanelEnabled Se true, mostra il pannello debug live durante la scansione.
 */
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
    val liveDebugPanelEnabled: Boolean = false,
)

/**
 * ViewModel che espone [uiState] e le azioni per configurare e avviare le scansioni di accessibilità.
 *
 * @param application Contesto applicativo usato per accedere a repository, store e package manager.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Massimo numero di app monitorabili con lancio automatico attivo. */
        const val MAX_APPS_WITH_AUTO_LAUNCH = 1
    }

    private val repository = (application as AccessScopeApp).scanRepository
    private val favoriteAppsStore = (application as AccessScopeApp).favoriteAppsStore
    private val scanSettingsStore = (application as AccessScopeApp).scanSettingsStore
    private val themePreferencesStore = (application as AccessScopeApp).themePreferencesStore

    private val _uiState = MutableStateFlow(HomeUiState())
    /** Flusso osservabile dello stato UI della Home; aggiornato da repository, permessi e azioni utente. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val favorites = favoriteAppsStore.getFavorites()
        _uiState.update { it.copy(selectedPackages = favorites, favoritePackages = favorites) }
        viewModelScope.launch {
            repository.state.collect { scanState ->
                _uiState.update { state ->
                    val status = when {
                        scanState.lastPdfPath != null && !scanState.isScanning ->
                            "Report salvato in: ${scanState.lastPdfPath}"
                        scanState.errorMessage != null -> scanState.errorMessage
                        else -> state.statusMessage
                    }
                    state.copy(scanState = scanState, statusMessage = status)
                }
            }
        }
        refreshPermissions()
        _uiState.update {
            it.copy(
                autoLaunchEnabled = scanSettingsStore.autoLaunchEnabled,
                scanScope = scanSettingsStore.getScanScope(),
                themeMode = themePreferencesStore.getThemeMode(),
                liveDebugPanelEnabled = scanSettingsStore.liveDebugPanelEnabled,
            )
        }
        loadApps()
    }

    /** Inverte lo stato dell'opzione "apri app automaticamente" e lo persiste nelle impostazioni. */
    fun toggleAutoLaunch() {
        val state = _uiState.value
        val enabled = !state.autoLaunchEnabled
        if (enabled && state.selectedPackages.size > MAX_APPS_WITH_AUTO_LAUNCH) {
            val trimmed = state.selectedPackages.take(MAX_APPS_WITH_AUTO_LAUNCH).toSet()
            scanSettingsStore.autoLaunchEnabled = true
            _uiState.update {
                it.copy(
                    autoLaunchEnabled = true,
                    selectedPackages = trimmed,
                    statusMessage = "Lancio automatico attivo: selezione limitata a $MAX_APPS_WITH_AUTO_LAUNCH app.",
                )
            }
            return
        }
        scanSettingsStore.autoLaunchEnabled = enabled
        _uiState.update { it.copy(autoLaunchEnabled = enabled) }
    }

    /** Inverte il pannello debug live e persiste la preferenza. */
    fun toggleLiveDebugPanel() {
        val enabled = !_uiState.value.liveDebugPanelEnabled
        scanSettingsStore.liveDebugPanelEnabled = enabled
        _uiState.update { it.copy(liveDebugPanelEnabled = enabled) }
    }

    /**
     * Verifica se si può aggiungere un'app alla selezione rispettando il limite auto-launch.
     */
    private fun canSelectMore(selected: Set<String>, packageName: String): Boolean {
        if (packageName in selected) return true
        if (!_uiState.value.autoLaunchEnabled) return true
        return selected.size < MAX_APPS_WITH_AUTO_LAUNCH
    }

    private fun autoLaunchLimitMessage() =
        "Con lancio automatico attivo puoi selezionare solo $MAX_APPS_WITH_AUTO_LAUNCH app."

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
            val favorites = favoriteAppsStore.getFavorites()
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
                    selectedPackages = state.selectedPackages.union(favorites),
                    isLoadingApps = false,
                )
            }
        }
    }

    /**
     * Aggiunge o rimuove un'app dai preferiti e aggiorna la selezione di conseguenza.
     *
     * @param packageName Identificativo del pacchetto Android dell'app.
     */
    fun toggleFavorite(packageName: String) {
        val favorites = favoriteAppsStore.toggle(packageName)
        _uiState.update { state ->
            val selected = state.selectedPackages.toMutableSet()
            if (packageName in favorites) {
                if (state.autoLaunchEnabled) {
                    selected.clear()
                    selected.add(packageName)
                } else if (!canSelectMore(selected, packageName)) {
                    return@update state.copy(
                        favoritePackages = favorites,
                        statusMessage = autoLaunchLimitMessage(),
                        apps = state.apps.map { app ->
                            app.copy(isFavorite = app.packageName in favorites)
                        }.sortedWith(
                            compareByDescending<InstalledAppInfo> { it.isFavorite }
                                .thenBy { !it.isSystemApp }
                                .thenBy { it.label.lowercase() },
                        ),
                    )
                } else {
                    selected.add(packageName)
                }
            } else {
                selected.remove(packageName)
            }
            state.copy(
                favoritePackages = favorites,
                selectedPackages = selected,
                apps = state.apps.map { app ->
                    app.copy(isFavorite = app.packageName in favorites)
                }.sortedWith(
                    compareByDescending<InstalledAppInfo> { it.isFavorite }
                        .thenBy { !it.isSystemApp }
                        .thenBy { it.label.lowercase() },
                ),
            )
        }
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
        _uiState.update { state ->
            val updated = state.selectedPackages.toMutableSet()
            if (packageName in updated) {
                updated.remove(packageName)
            } else if (state.autoLaunchEnabled) {
                updated.clear()
                updated.add(packageName)
            } else {
                updated.add(packageName)
            }
            state.copy(selectedPackages = updated)
        }
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
        if (selected.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Seleziona almeno una app da monitorare.") }
            return
        }
        if (current.autoLaunchEnabled && selected.size > MAX_APPS_WITH_AUTO_LAUNCH) {
            _uiState.update { it.copy(statusMessage = autoLaunchLimitMessage()) }
            return
        }
        refreshPermissions()
        val state = _uiState.value
        if (!state.accessibilityGranted) {
            _uiState.update { it.copy(statusMessage = "Abilita il servizio di accessibilità AccessScope.") }
            return
        }
        if (!state.overlayGranted) {
            _uiState.update { it.copy(statusMessage = "Concedi il permesso di sovrapposizione.") }
            return
        }

        repository.startScan(selected, state.scanScope)
        AccessScopeAccessibilityService.instance?.resetDynamicTracking()
        // #region agent log
        DebugTrace.log("H1", "ViewModel.startScan", "scan_requested", mapOf(
            "packages" to selected.joinToString(","),
            "serviceConnected" to (AccessScopeAccessibilityService.instance != null),
            "accessibilityGranted" to state.accessibilityGranted,
        ))
        // #endregion
        ScanOverlayService.start(context)

        var message = "Scansione avviata. Apri le app selezionate e interagisci."
        if (state.autoLaunchEnabled) {
            val launched = AppLaunchHelper.launchFirstAvailable(context, selected)
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
        (getApplication<AccessScopeApp>()).stopScanSession(fromOverlay = false)
    }

    /**
     * Restituisce l'icona bitmap di un'app, caricandola dalla cache se necessario.
     *
     * @param packageName Identificativo del pacchetto Android dell'app.
     * @return Bitmap dell'icona, oppure null se non disponibile.
     */
    fun appIconBitmap(packageName: String): androidx.compose.ui.graphics.ImageBitmap? =
        AppIconCache.getOrLoad(getApplication<Application>().packageManager, packageName)

    /** Seleziona tutte le app attualmente visibili nell'elenco. */
    fun selectAllVisible() {
        _uiState.update { state ->
            val all = state.apps.map { it.packageName }
            val selected = if (state.autoLaunchEnabled) {
                all.take(MAX_APPS_WITH_AUTO_LAUNCH).toSet()
            } else {
                all.toSet()
            }
            val message = if (state.autoLaunchEnabled && all.size > MAX_APPS_WITH_AUTO_LAUNCH) {
                autoLaunchLimitMessage()
            } else {
                null
            }
            state.copy(selectedPackages = selected, statusMessage = message)
        }
    }

    /** Deseleziona tutte le app dal monitoraggio. */
    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
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
}
