package dev.accessscope.scanner.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.export.PdfReportExporter
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.ScanOverlayService
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
    val overlayGranted: Boolean = false,
    val isLoadingApps: Boolean = true,
    val includeSystemApps: Boolean = false,
    val statusMessage: String? = null,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AccessScopeApp).scanRepository
    private val favoriteAppsStore = (application as AccessScopeApp).favoriteAppsStore
    private val pdfExporter = PdfReportExporter(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        repository.stopCallback = { stopScan() }
        val favorites = favoriteAppsStore.getFavorites()
        _uiState.update { it.copy(selectedPackages = favorites, favoritePackages = favorites) }
        viewModelScope.launch {
            repository.state.collect { scanState ->
                _uiState.update { it.copy(scanState = scanState) }
            }
        }
        refreshPermissions()
        loadApps()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                accessibilityGranted = PermissionHelper.isAccessibilityServiceEnabled(
                    context,
                    AccessScopeAccessibilityService::class.java,
                ),
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
        ScanOverlayService.start(context)
        _uiState.update { it.copy(statusMessage = "Scansione avviata. Apri le app selezionate e interagisci.") }
    }

    fun stopScan() {
        val context = getApplication<Application>()
        if (!repository.state.value.isScanning && repository.state.value.violations.isEmpty()) return

        repository.stopScan()
        ScanOverlayService.stop(context)

        viewModelScope.launch {
            val snapshot = repository.state.value
            val result = withContext(Dispatchers.IO) {
                pdfExporter.export(
                    targetPackages = snapshot.selectedPackages,
                    violations = snapshot.violations,
                    screenReaderFindings = snapshot.screenReaderFindings,
                    scannedScreens = snapshot.scannedScreens,
                )
            }
            result.fold(
                onSuccess = { path ->
                    repository.setPdfPath(path)
                    _uiState.update {
                        it.copy(statusMessage = "Report salvato in: $path")
                    }
                },
                onFailure = { error ->
                    repository.setError(error.message ?: "Errore export PDF")
                    _uiState.update {
                        it.copy(statusMessage = "Errore durante la generazione del PDF.")
                    }
                },
            )
        }
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
