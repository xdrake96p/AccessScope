/**
 * Schermata principale "Scanner & HUD": hero, ultima sessione, ricerca app (senza elenco completo).
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.components.AppListRow
import dev.accessscope.scanner.ui.components.HomeTopBar
import dev.accessscope.scanner.ui.components.PermissionsCard
import dev.accessscope.scanner.ui.components.ScanDashboard
import dev.accessscope.scanner.ui.screen.home.AppSelectionPanel
import dev.accessscope.scanner.ui.screen.home.HomeHeroCard
import dev.accessscope.scanner.ui.screen.home.HomeLastSessionCard
import dev.accessscope.scanner.ui.screen.home.PdfResultCard
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.PdfHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Schermata Home (tab zona principale).
 *
 * @param onOpenReport Apre il report dell'ultima sessione (tab Scansione, zona sessione).
 * @param onOpenDynamicReport Apre il report dinamico per schermate (tab Report).
 * @param onOpenHistory Apre la cronologia per il package indicato (tab Storico).
 * @param onOpenDrawer Apre il navigation drawer laterale.
 */
@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onOpenReport: () -> Unit,
    onOpenDynamicReport: () -> Unit = {},
    onOpenHistory: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appListState by viewModel.appListUiState.collectAsStateWithLifecycle()
    val scanUi by viewModel.scanDashboardUiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Tornando da Impostazioni accessibilità, aggiorna bind senza tap su «Aggiorna».
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val secondaryTextColor = contentSecondary()
    val toggleApp = viewModel::toggleApp
    val toggleFavorite = viewModel::toggleFavorite

    LaunchedEffect(query) {
        if (query.isBlank()) {
            debouncedQuery = ""
        } else {
            delay(120)
            debouncedQuery = query
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            snackbarHost.showSnackbar(message)
            viewModel.clearStatus()
        }
    }

    uiState.selectionLimitDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSelectionLimitDialog,
            title = { Text("Una sola app per sessione") },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSelectionLimitDialog) {
                    Text("OK")
                }
            },
        )
    }

    val isSearching = debouncedQuery.isNotBlank()

    /** Risultati ricerca: vuoto se la query è vuota (niente elenco completo in Home). */
    val searchResults = remember(debouncedQuery, appListState.apps) {
        if (debouncedQuery.isBlank()) emptyList()
        else appListState.apps.filter {
            it.label.contains(debouncedQuery, ignoreCase = true) ||
                it.packageName.contains(debouncedQuery, ignoreCase = true)
        }
    }

    /** App già selezionate, mostrate a riposo per deselezionare senza rieseguire la ricerca. */
    val selectedApps = remember(appListState.apps, appListState.selectedPackages) {
        if (appListState.selectedPackages.isEmpty()) emptyList()
        else appListState.apps.filter { it.packageName in appListState.selectedPackages }
    }

    val appsToShow = if (isSearching) searchResults else selectedApps

    val packageLabels = remember(appListState.apps) {
        appListState.apps.associate { it.packageName to it.label }
    }

    val showDashboard = scanUi.scanState.isScanning ||
        scanUi.scanState.violations.isNotEmpty() ||
        scanUi.scanState.uniqueScreens > 0 ||
        scanUi.scanState.screenReaderFindings.isNotEmpty()

    val canStartScan = appListState.selectedPackages.isNotEmpty() &&
        uiState.accessibilityGranted && uiState.overlayGranted
    val startHint = when {
        canStartScan -> null
        appListState.selectedPackages.isEmpty() -> "Seleziona almeno un'app da analizzare"
        !uiState.accessibilityGranted -> "Attiva il servizio di accessibilità"
        !uiState.overlayGranted -> "Concedi il permesso overlay"
        else -> "Impossibile avviare la scansione"
    }

    val latestSession = uiState.latestArchivedSession
    val passedTotal = remember(scanUi.scanState.checkSummaries) {
        ReportHelper.totalPassedChecks(scanUi.scanState.checkSummaries)
            .takeIf { scanUi.scanState.checkSummaries.isNotEmpty() }
    }
    val canOpenDynamicReport = scanUi.scanState.uniqueScreens > 0 ||
        scanUi.scanState.violations.isNotEmpty() ||
        latestSession != null

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = { HomeTopBar(onMenuClick = onOpenDrawer) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                HomeHeroCard(
                    selectedCount = appListState.selectedPackages.size,
                    isScanning = scanUi.scanState.isScanning,
                    canStart = canStartScan,
                    disabledHint = startHint,
                    onStart = viewModel::startScan,
                    onStop = viewModel::stopScan,
                )
            }

            if (showDashboard) {
                item(key = "dashboard") {
                    ScanDashboard(
                        violations = scanUi.scanState.violations,
                        screens = scanUi.scanState.uniqueScreens,
                        scanAnalyses = scanUi.scanState.scanAnalyses,
                        talkBackFindings = scanUi.scanState.screenReaderFindings.size,
                        screenReaderFindings = scanUi.scanState.screenReaderFindings,
                        targetPackages = scanUi.scanState.selectedPackages,
                        packageLabels = packageLabels,
                        isPartialScan = !scanUi.scanState.scanScope.isFullScan,
                        scanScopeLabel = scanUi.scanState.scanScope.label(),
                        isScanning = scanUi.scanState.isScanning,
                        onOpenReport = onOpenReport,
                        onOpenDynamicReport = onOpenDynamicReport,
                        onAiPromptCopied = {
                            scope.launch {
                                snackbarHost.showSnackbar("Prompt AI copiato negli appunti")
                            }
                        },
                        sessionComparison = scanUi.sessionComparison,
                    )
                }
            }

            if (!scanUi.scanState.isScanning && latestSession != null) {
                item(key = "last_session") {
                    HomeLastSessionCard(
                        session = latestSession,
                        passedTotal = passedTotal,
                        onOpenDetails = onOpenReport,
                    )
                }
                if (canOpenDynamicReport) {
                    item(key = "dynamic_report_cta") {
                        Button(
                            onClick = onOpenDynamicReport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(Icons.Outlined.Analytics, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                "  VEDI REPORT DINAMICO",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (!scanUi.scanState.isScanning) {
                scanUi.scanState.lastPdfPath?.let { path ->
                    item(key = "pdf_$path") {
                        PdfResultCard(
                            path = path,
                            onOpenPdf = { PdfHelper.openPdf(context, path) },
                        )
                    }
                }
            }

            item(key = "permissions") {
                PermissionsCard(
                    accessibilityGranted = uiState.accessibilityGranted,
                    accessibilityConnected = uiState.accessibilityConnected,
                    overlayGranted = uiState.overlayGranted,
                    onRefresh = viewModel::refreshPermissions,
                )
            }

            item(key = "selection_panel") {
                AppSelectionPanel(
                    query = query,
                    onQueryChange = { query = it },
                    appListState = appListState,
                    viewModel = viewModel,
                    isScanning = scanUi.scanState.isScanning,
                )
            }

            item(key = "apps_section_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            isSearching -> "Risultati (${searchResults.size})"
                            selectedApps.isNotEmpty() ->
                                "App selezionata (${selectedApps.size})"
                            else -> "Cerca un'app da analizzare"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (appListState.isLoadingApps) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (appsToShow.isEmpty() && !appListState.isLoadingApps) {
                item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isSearching) {
                                "Nessuna app trovata"
                            } else {
                                "Digita il nome o il package per trovare un'app installata"
                            },
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            items(
                items = appsToShow,
                key = { it.packageName },
                contentType = { "app_row" },
            ) { app ->
                AppListRow(
                    app = app,
                    selected = app.packageName in appListState.selectedPackages,
                    onTogglePackage = toggleApp,
                    onToggleFavoritePackage = toggleFavorite,
                    secondaryTextColor = secondaryTextColor,
                    interactionEnabled = !scanUi.scanState.isScanning,
                )
            }
        }
    }
}
