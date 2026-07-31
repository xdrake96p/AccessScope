package dev.accessscope.scanner.ui.viewmodel

import android.app.Activity
import android.app.Application
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.recorder.ScanRecorderMutexPolicy
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.ui.theme.AppThemeMode
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.export.ScanReliabilityReportExporter
import dev.accessscope.scanner.report.SessionComparisonHelper
import dev.accessscope.scanner.util.AppLaunchHelper
import dev.accessscope.scanner.util.FeedbackIssueLauncher
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

    fun toggleIncludeLowConfidenceFindings() {
        val enabled = !uiState.value.includeLowConfidenceFindings
        scanSettingsStore.includeLowConfidenceFindings = enabled
        uiState.update { it.copy(includeLowConfidenceFindings = enabled) }
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
        val app = context as AccessScopeApp
        if (!ScanRecorderMutexPolicy.canStartScan(app.recordingController.isRecording)) {
            uiState.update {
                it.copy(statusMessage = "Ferma prima la registrazione Maestro (Beta).")
            }
            return
        }
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
        if (!state.accessibilityConnected) {
            appListController.logSelectionForStart("start_scan_blocked_a11y_unbound")
            PermissionHelper.safeStartSettingsIntent(
                context,
                PermissionHelper.accessibilityServiceIntent(
                    context,
                    AccessScopeAccessibilityService::class.java,
                ),
            )
            uiState.update {
                it.copy(
                    statusMessage = "Accessibilità ON ma servizio non collegato. " +
                        "In Accessibilità: OFF → attendi → ON → Consenti.",
                )
            }
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

    /**
     * Risolve il percorso del report affidabilità `.md` per il feedback GitHub.
     * Usa l'ultimo export se presente, altrimenti rigenera dai dati di sessione.
     */
    fun resolveReliabilityMdForFeedback(onResult: (String?) -> Unit) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { resolveReliabilityMdPath() }
            onResult(path)
        }
    }

    private fun resolveReliabilityMdPath(): String? {
        val app = application as AccessScopeApp
        val scanState = repository.state.value

        scanState.lastReliabilityMdPath?.let { existing ->
            if (FeedbackIssueLauncher.fileExists(existing)) return existing
        }

        if (scanState.violations.isNotEmpty() || scanState.visitedScreenTitles.isNotEmpty()) {
            exportReliabilityFromScanState(scanState, app)?.let { return it }
        }

        val history = app.scanHistoryStore
        val archived = scanState.selectedPackages
            .firstNotNullOfOrNull { pkg -> history.getLatest(pkg) }
            ?: history.allSessionIds().firstNotNullOfOrNull { id -> history.getSession(id) }

        return archived?.let { exportReliabilityFromArchived(it, app) }
    }

    private fun exportReliabilityFromScanState(
        scanState: ScanSessionState,
        app: AccessScopeApp,
    ): String? {
        val comparison = scanState.selectedPackages.firstOrNull()?.let { pkg ->
            SessionComparisonHelper.compareLatestWithPrevious(
                app.scanHistoryStore.getLatest(pkg),
                app.scanHistoryStore.getPrevious(pkg),
            )
        }
        return ScanReliabilityReportExporter(application).export(
            targetPackages = scanState.selectedPackages,
            violations = scanState.violations,
            screenReaderFindings = scanState.screenReaderFindings,
            uniqueScreens = scanState.uniqueScreens,
            scanAnalyses = scanState.scanAnalyses,
            scanScopeLabel = scanState.scanScope.label(),
            scannedScreens = scanState.visitedScreenTitles,
            checkSummaries = scanState.checkSummaries,
            sessionComparison = comparison,
            appVersion = appVersionName(),
        ).getOrNull()?.also { path ->
            repository.setReliabilityMdPath(path)
        }
    }

    private fun exportReliabilityFromArchived(
        archived: ArchivedScanSession,
        app: AccessScopeApp,
    ): String? = ScanReliabilityReportExporter(application).export(
        targetPackages = archived.targetPackages,
        violations = archived.violations,
        screenReaderFindings = archived.screenReaderFindings,
        uniqueScreens = archived.uniqueScreens,
        scanAnalyses = archived.scanAnalyses,
        scanScopeLabel = archived.scanScopeLabel,
        scannedScreens = archived.visitedScreens.map { it.title }.ifEmpty {
            listOf("Sessione ${archived.id}")
        },
        checkSummaries = emptyList(),
        sessionComparison = null,
        appVersion = appVersionName(),
    ).getOrNull()

    private fun appVersionName(): String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

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
                includeLowConfidenceFindings = scanSettingsStore.includeLowConfidenceFindings,
            )
        }
    }
}
