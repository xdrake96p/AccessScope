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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.accessscope.scanner.ui.components.LiveDebugPanel
import dev.accessscope.scanner.ui.components.PermissionsCard
import dev.accessscope.scanner.ui.components.ScanDashboard
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.ControlShape
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.HomeUiState
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.PdfHelper
import kotlinx.coroutines.delay

private val WideLayoutBreakpoint = 720.dp

/**
 * Schermata Home con elenco app, permessi e controlli di scansione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var showLiveDebug by remember { mutableStateOf(false) }
    val appListState = rememberLazyListState()
    val selectedPackages by remember { derivedStateOf { uiState.selectedPackages } }
    val packageLabels = remember(uiState.apps) {
        uiState.apps.associate { it.packageName to it.label }
    }

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

    val filteredApps by remember {
        derivedStateOf {
            if (debouncedQuery.isBlank()) uiState.apps
            else uiState.apps.filter {
                it.label.contains(debouncedQuery, ignoreCase = true) ||
                    it.packageName.contains(debouncedQuery, ignoreCase = true)
            }
        }
    }

    val showDashboard = uiState.scanState.isScanning ||
        uiState.scanState.violations.isNotEmpty() ||
        uiState.scanState.uniqueScreens > 0 ||
        uiState.scanState.screenReaderFindings.isNotEmpty()

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            ScanActionBar(
                modifier = Modifier.navigationBarsPadding(),
                isScanning = uiState.scanState.isScanning,
                liveDebugEnabled = uiState.liveDebugPanelEnabled,
                canStart = uiState.selectedPackages.isNotEmpty() &&
                    uiState.accessibilityGranted &&
                    uiState.overlayGranted,
                onStart = viewModel::startScan,
                onStop = viewModel::stopScan,
                onOpenLiveDebug = { showLiveDebug = true },
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
                state = appListState,
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
                                    selectedCount = uiState.selectedPackages.size,
                                    isScanning = uiState.scanState.isScanning,
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
                                    uiState = uiState,
                                    viewModel = viewModel,
                                )
                            }
                        }
                    }
                } else {
                    item(key = "hero") {
                        HeroHeader(
                            selectedCount = uiState.selectedPackages.size,
                            isScanning = uiState.scanState.isScanning,
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

                if (showDashboard) {
                    item(key = "dashboard") {
                        ScanDashboard(
                            violations = uiState.scanState.violations,
                            screens = uiState.scanState.uniqueScreens,
                            scanAnalyses = uiState.scanState.scanAnalyses,
                            talkBackFindings = uiState.scanState.screenReaderFindings.size,
                            isPartialScan = !uiState.scanState.scanScope.isFullScan,
                            scanScopeLabel = uiState.scanState.scanScope.label(),
                            isScanning = uiState.scanState.isScanning,
                            onOpenReport = onOpenReport,
                            modifier = Modifier.padding(horizontal = if (isWide) horizontalPad else 16.dp),
                        )
                    }
                }

                if (!uiState.scanState.isScanning) {
                    uiState.scanState.lastPdfPath?.let { path ->
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
                            uiState = uiState,
                            viewModel = viewModel,
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
                            "Elenco app (${uiState.selectedPackages.size}/${uiState.apps.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (uiState.isLoadingApps) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                if (filteredApps.isEmpty() && !uiState.isLoadingApps) {
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
                        selected = app.packageName in selectedPackages,
                        onToggle = { viewModel.toggleApp(app.packageName) },
                        onToggleFavorite = { viewModel.toggleFavorite(app.packageName) },
                        modifier = Modifier
                            .padding(horizontal = if (isWide) horizontalPad else 16.dp),
                    )
                }
            }
        }

        if (showLiveDebug && uiState.liveDebugPanelEnabled && uiState.scanState.isScanning) {
            ModalBottomSheet(
                onDismissRequest = { showLiveDebug = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                LiveDebugPanel(
                    scanState = uiState.scanState,
                    packageLabels = packageLabels,
                )
            }
        }
    }
}

@Composable
private fun AppSelectionPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    uiState: HomeUiState,
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Text(
            "Seleziona app da analizzare",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        AppSearchField(query = query, onQueryChange = onQueryChange)
        AppSelectionInfoBanner(
            autoLaunchEnabled = uiState.autoLaunchEnabled,
            maxWithAutoLaunch = ScanViewModel.MAX_APPS_WITH_AUTO_LAUNCH,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mostra app di sistema", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = uiState.includeSystemApps,
                onCheckedChange = { viewModel.toggleIncludeSystemApps() },
            )
        }
        Row {
            TextButton(onClick = viewModel::selectAllVisible) {
                Icon(Icons.Outlined.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (uiState.autoLaunchEnabled) "Prima" else "Tutte")
            }
            TextButton(onClick = viewModel::clearSelection) {
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
private fun ScanActionBar(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    liveDebugEnabled: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenLiveDebug: () -> Unit,
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
        if (isScanning && liveDebugEnabled) {
            IconButton(
                onClick = onOpenLiveDebug,
                modifier = Modifier
                    .clip(ControlShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .size(56.dp),
            ) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = "Debug live",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AnimatedContent(
            targetState = isScanning,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(AccessScopeMotion.fadeInTween) togetherWith fadeOut(AccessScopeMotion.screenExitTween)
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
                        .height(56.dp),
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
