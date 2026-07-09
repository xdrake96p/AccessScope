/**
 * Schermata principale di AccessScope: selezione app, permessi, dashboard live e avvio scansione.
 *
 * Layout adattivo: flusso orizzontale a card su schermi larghi, colonna singola su telefono.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.components.AppListRow
import dev.accessscope.scanner.ui.components.AppSearchField
import dev.accessscope.scanner.ui.components.AppSelectionInfoBanner
import dev.accessscope.scanner.ui.components.FeatureHighlights
import dev.accessscope.scanner.ui.components.HeroHeader
import dev.accessscope.scanner.ui.components.PermissionsCard
import dev.accessscope.scanner.ui.components.ScanDashboard
import dev.accessscope.scanner.ui.components.ScanHistoryEntryButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.accessscope.scanner.ui.accessibility.asSectionHeading
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.ControlShape
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.AppListUiState
import dev.accessscope.scanner.ui.viewmodel.ScanDashboardUiState
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.PdfHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WideLayoutBreakpoint = 720.dp

/**
 * Schermata Home con elenco app, permessi e controlli di scansione.
 */
@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenHistory: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appListState by viewModel.appListUiState.collectAsStateWithLifecycle()
    val scanUi by viewModel.scanDashboardUiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    val filteredApps by remember {
        derivedStateOf {
            if (debouncedQuery.isBlank()) appListState.apps
            else appListState.apps.filter {
                it.label.contains(debouncedQuery, ignoreCase = true) ||
                    it.packageName.contains(debouncedQuery, ignoreCase = true)
            }
        }
    }

    val packageLabels = remember(appListState.apps) {
        appListState.apps.associate { it.packageName to it.label }
    }

    val showDashboard = scanUi.scanState.isScanning ||
        scanUi.scanState.violations.isNotEmpty() ||
        scanUi.scanState.uniqueScreens > 0 ||
        scanUi.scanState.screenReaderFindings.isNotEmpty()

    val historyPackage = appListState.selectedPackages.firstOrNull()
    val historyAppLabel = historyPackage?.let { packageLabels[it] }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            HomeScanActionBar(
                scanUi = scanUi,
                modifier = Modifier.navigationBarsPadding(),
                onStart = viewModel::startScan,
                onStop = viewModel::stopScan,
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isWide = maxWidth >= WideLayoutBreakpoint
            val horizontalPad = if (isWide) 20.dp else 0.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isWide) {
                    item(key = "wide_top_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPad),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HeroHeader(
                                    selectedCount = appListState.selectedPackages.size,
                                    isScanning = scanUi.scanState.isScanning,
                                    onOpenSettings = onOpenSettings,
                                )
                                FeatureHighlights()
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PermissionsCard(
                                    accessibilityGranted = uiState.accessibilityGranted,
                                    accessibilityConnected = uiState.accessibilityConnected,
                                    overlayGranted = uiState.overlayGranted,
                                    onRefresh = viewModel::refreshPermissions,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppSelectionPanel(
                                    query = query,
                                    onQueryChange = { query = it },
                                    appListState = appListState,
                                    viewModel = viewModel,
                                    isScanning = scanUi.scanState.isScanning,
                                )
                            }
                        }
                    }
                } else {
                    item(key = "hero") {
                        HeroHeader(
                            selectedCount = appListState.selectedPackages.size,
                            isScanning = scanUi.scanState.isScanning,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                    item(key = "highlights") {
                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FeatureHighlights()
                            PermissionsCard(
                                accessibilityGranted = uiState.accessibilityGranted,
                                accessibilityConnected = uiState.accessibilityConnected,
                                overlayGranted = uiState.overlayGranted,
                                onRefresh = viewModel::refreshPermissions,
                            )
                        }
                    }
                }

                item(key = "scan_history_button") {
                    ScanHistoryEntryButton(
                        enabled = historyPackage != null,
                        appLabel = historyAppLabel,
                        onClick = { historyPackage?.let(onOpenHistory) },
                        modifier = Modifier.padding(horizontal = if (isWide) horizontalPad else 16.dp),
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
                            onAiPromptCopied = {
                                scope.launch {
                                    snackbarHost.showSnackbar("Prompt AI copiato negli appunti")
                                }
                            },
                            sessionComparison = scanUi.sessionComparison,
                            modifier = Modifier.padding(horizontal = if (isWide) horizontalPad else 16.dp),
                        )
                    }
                }

                if (!scanUi.scanState.isScanning) {
                    scanUi.scanState.lastPdfPath?.let { path ->
                        item(key = "pdf_$path") {
                            PdfResultCard(
                                path = path,
                                onOpenPdf = { PdfHelper.openPdf(context, path) },
                                modifier = Modifier.padding(horizontal = if (isWide) horizontalPad else 16.dp),
                            )
                        }
                    }
                }

                if (!isWide) {
                    item(key = "search_header") {
                        AppSelectionPanel(
                            query = query,
                            onQueryChange = { query = it },
                            appListState = appListState,
                            viewModel = viewModel,
                            isScanning = scanUi.scanState.isScanning,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                item(key = "apps_section_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isWide) horizontalPad else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Elenco app (${appListState.selectedPackages.size}/${appListState.apps.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (appListState.isLoadingApps) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                if (filteredApps.isEmpty() && !appListState.isLoadingApps) {
                    item(key = "empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Nessuna app trovata", color = contentSecondary())
                        }
                    }
                }

                items(
                    items = filteredApps,
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
                        modifier = Modifier
                            .padding(horizontal = if (isWide) horizontalPad else 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSelectionPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    appListState: AppListUiState,
    viewModel: ScanViewModel,
    isScanning: Boolean,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Text(
            "Seleziona app da analizzare",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.asSectionHeading(),
        )
        AppSearchField(query = query, onQueryChange = onQueryChange)
        AppSelectionInfoBanner(
            autoLaunchEnabled = appListState.autoLaunchEnabled,
        )
        Text(
            "La stella aggiunge ai preferiti e attiva subito il monitoraggio (una sola app per sessione).",
            style = MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mostra app di sistema", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = appListState.includeSystemApps,
                onCheckedChange = { viewModel.toggleIncludeSystemApps() },
                enabled = !isScanning,
            )
        }
        Row {
            TextButton(onClick = viewModel::selectAllVisible, enabled = !isScanning) {
                Icon(Icons.Outlined.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Prima visibile")
            }
            TextButton(onClick = viewModel::clearSelection, enabled = !isScanning) {
                Text("Nessuna")
            }
        }
    }
}

@Composable
private fun PdfResultCard(
    path: String,
    onOpenPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = BrandPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Report PDF salvato", fontWeight = FontWeight.SemiBold)
                Text(path, style = CodeTextStyle, color = contentSecondary())
            }
        }
        TextButton(onClick = onOpenPdf) {
            Text("Apri file PDF")
        }
    }
}

@Composable
private fun HomeScanActionBar(
    scanUi: ScanDashboardUiState,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isScanning = scanUi.scanState.isScanning
    val canStart = scanUi.selectedPackages.isNotEmpty() &&
        scanUi.accessibilityGranted &&
        scanUi.overlayGranted
    ScanActionBar(
        modifier = modifier,
        isScanning = isScanning,
        canStart = canStart,
        disabledHint = when {
            canStart -> null
            scanUi.selectedPackages.isEmpty() -> "Seleziona almeno un'app da analizzare"
            !scanUi.accessibilityGranted -> "Attiva il servizio di accessibilità"
            !scanUi.overlayGranted -> "Concedi il permesso overlay"
            else -> "Impossibile avviare la scansione"
        },
        onStart = onStart,
        onStop = onStop,
    )
}

@Composable
private fun ScanActionBar(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    canStart: Boolean,
    disabledHint: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.25f),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = isScanning,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(AccessScopeMotion.screenEnterTween) togetherWith fadeOut(AccessScopeMotion.screenExitTween)
            },
            label = "scan_primary_action",
        ) { scanning ->
            if (scanning) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Stop scansione", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(
                            if (!canStart && disabledHint != null) {
                                Modifier.semantics { stateDescription = disabledHint }
                            } else {
                                Modifier
                            },
                        ),
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Avvia scansione",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (isScanning) {
            Row(
                modifier = Modifier
                    .clip(ControlShape)
                    .background(Success.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = "Scansione in corso" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Success,
                )
                Spacer(Modifier.width(8.dp))
                Text("Live", style = MaterialTheme.typography.labelLarge, color = Success, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
