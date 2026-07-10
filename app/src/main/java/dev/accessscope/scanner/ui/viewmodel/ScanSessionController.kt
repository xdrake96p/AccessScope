package dev.accessscope.scanner.ui.viewmodel

import android.app.Activity
import android.app.Application
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.ui.theme.AppThemeMode
import dev.accessscope.scanner.util.AppLaunchHelper
import dev.accessscope.scanner.util.DebugTrace
import dev.accessscope.scanner.util.PermissionHelper
import dev.accessscope.scanner.util.ScanSettingsStore
import dev.accessscope.scanner.util.ThemePreferencesStore
import dev.accessscope.scanner.data.ScanSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestisce permessi, ambiti di scansione e avvio/arresto sessione.
 */
internal class ScanSessionController(
    private val application: Application,
    private val repository: ScanSessionRepository,
    private val scanSettingsStore: ScanSettingsStore,
    private val themePreferencesStore: ThemePreferencesStore,
    private val uiState: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
    private val appListController: ScanAppListController,
) {

    fun toggleReliabilityReport() {
        val enabled = !uiState.value.reliabilityReportEnabled
        scanSettingsStore.reliabilityReportEnabled = enabled
        uiState.update { it.copy(reliabilityReportEnabled = enabled) }
    }

    fun toggleScanArea(area: ViolationArea) {
        val current = uiState.value.scanScope.enabledAreas.toMutableSet()
        if (area in current) {
            if (current.size == 1) {
                uiState.update { it.copy(statusMessage = "Almeno un ambito deve restare attivo.") }
                return
            }
            current.remove(area)
        } else {
            current.add(area)
        }
        applyScanScope(ScanScope(current))
    }

    fun setFullScan() = applyScanScope(ScanScope.FULL)

    fun applyTalkBackOnlyPreset() = applyScanScope(ScanScope.talkBackOnly())

    fun applyLabelsOnlyPreset() = applyScanScope(ScanScope.labelsOnly())

    fun applyContrastOnlyPreset() = applyScanScope(ScanScope.contrastOnly())

    private fun applyScanScope(scope: ScanScope) {
        scanSettingsStore.setScanScope(scope)
        uiState.update { it.copy(scanScope = scope) }
    }

    fun refreshPermissions() {
        val context = application
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
        uiState.update {
            it.copy(
                accessibilityGranted = a11yEnabled,
                accessibilityConnected = a11yConnected,
                overlayGranted = PermissionHelper.canDrawOverlays(context),
            )
        }
    }

    fun startScan() {
        val context = application
        val current = uiState.value
        val selected = current.selectedPackages
        val monitored = AppSelectionPolicy.enforceMax(selected)
        if (monitored.size != selected.size) {
            appListController.logSelectionForStart(
                "start_scan_trimmed",
                mapOf(
                    "requested" to selected.joinToString(),
                    "monitored" to monitored.joinToString(),
                ),
            )
            uiState.update {
                it.copy(
                    selectedPackages = monitored,
                    selectionLimitDialog = AppSelectionLimitDialog(appListController.autoLaunchLimitMessage()),
                )
            }
            return
        }
        if (monitored.isEmpty()) {
            appListController.logSelectionForStart("start_scan_blocked_empty")
            uiState.update { it.copy(statusMessage = "Seleziona almeno una app da monitorare.") }
            return
        }
        refreshPermissions()
        val state = uiState.value
        if (!state.accessibilityGranted) {
            appListController.logSelectionForStart("start_scan_blocked_a11y")
            uiState.update { it.copy(statusMessage = "Abilita il servizio di accessibilità AccessScope.") }
            return
        }
        if (!state.overlayGranted) {
            appListController.logSelectionForStart("start_scan_blocked_overlay")
            uiState.update { it.copy(statusMessage = "Concedi il permesso di sovrapposizione.") }
            return
        }

        repository.startScan(monitored, state.scanScope)
        appListController.logSelectionForStart(
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

        var message = "Scansione attiva. AccessScope è in ascolto — apri l'app selezionata."
        if (state.autoLaunchEnabled) {
            val launched = AppLaunchHelper.launchFirstAvailable(context, monitored)
            message = if (launched != null) {
                val label = state.apps.find { it.packageName == launched }?.label ?: launched
                "Scansione avviata. Aperta: $label"
            } else {
                "Scansione attiva. Nessuna app apribile automaticamente — aprila manualmente."
            }
        } else {
            (context as? Activity)?.moveTaskToBack(true)
        }
        uiState.update { it.copy(statusMessage = message) }
    }

    fun stopScan() {
        appListController.logSelectionForStart("stop_scan_requested")
        (application as AccessScopeApp).stopScanSession(fromOverlay = false)
    }

    fun exportDiagnosticLogs(onResult: (Result<String>) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                dev.accessscope.scanner.export.DiagnosticLogExporter(application).export()
            }
            onResult(result)
        }
    }

    fun clearStatus() {
        uiState.update { it.copy(statusMessage = null) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themePreferencesStore.setThemeMode(mode)
        uiState.update { it.copy(themeMode = mode) }
    }

    fun loadInitialSettings() {
        uiState.update {
            it.copy(
                autoLaunchEnabled = scanSettingsStore.autoLaunchEnabled,
                scanScope = scanSettingsStore.getScanScope(),
                themeMode = themePreferencesStore.getThemeMode(),
                reliabilityReportEnabled = scanSettingsStore.reliabilityReportEnabled,
            )
        }
    }
}
