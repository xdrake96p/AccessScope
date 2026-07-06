package dev.accessscope.scanner.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.AppLaunchHelper
import dev.accessscope.scanner.util.DebugTrace
import dev.accessscope.scanner.util.PackageHelper
import dev.accessscope.scanner.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val statusMessage: String? = null,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AccessScopeApp).scanRepository
    private val favoriteAppsStore = (application as AccessScopeApp).favoriteAppsStore
    private val scanSettingsStore = (application as AccessScopeApp).scanSettingsStore

    private val _uiState = MutableStateFlow(HomeUiState())
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
        _uiState.update { it.copy(autoLaunchEnabled = scanSettingsStore.autoLaunchEnabled) }
        loadApps()
    }

    fun toggleAutoLaunch() {
        val enabled = !_uiState.value.autoLaunchEnabled
        scanSettingsStore.autoLaunchEnabled = enabled
        _uiState.update { it.copy(autoLaunchEnabled = enabled) }
    }

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

    fun toggleFavorite(packageName: String) {
        val favorites = favoriteAppsStore.toggle(packageName)
        _uiState.update { state ->
            val selected = state.selectedPackages.toMutableSet()
            if (packageName in favorites) {
                selected.add(packageName)
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

    fun toggleIncludeSystemApps() {
        _uiState.update { it.copy(includeSystemApps = !it.includeSystemApps) }
        loadApps()
    }

    fun toggleApp(packageName: String) {
        _uiState.update { state ->
            val updated = state.selectedPackages.toMutableSet()
            if (!updated.add(packageName)) updated.remove(packageName)
            state.copy(selectedPackages = updated)
        }
    }

    fun startScan() {
        val context = getApplication<Application>()
        val selected = _uiState.value.selectedPackages
        if (selected.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Seleziona almeno una app da monitorare.") }
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

        repository.startScan(selected)
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

    fun stopScan() {
        (getApplication<AccessScopeApp>()).stopScanSession(fromOverlay = false)
    }

    fun appIcon(packageName: String): android.graphics.drawable.Drawable? {
        return try {
            getApplication<Application>().packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun selectAllVisible() {
        _uiState.update { state ->
            state.copy(selectedPackages = state.apps.map { it.packageName }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
