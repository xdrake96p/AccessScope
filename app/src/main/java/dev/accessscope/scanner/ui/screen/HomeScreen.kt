package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.ui.components.FeatureHighlights
import dev.accessscope.scanner.ui.components.HeroHeader
import dev.accessscope.scanner.ui.components.PermissionsCard
import dev.accessscope.scanner.ui.components.ScanOptionsCard
import dev.accessscope.scanner.ui.components.ScanDashboard
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.SurfaceLight
import dev.accessscope.scanner.ui.theme.TextSecondary
import dev.accessscope.scanner.util.PdfHelper
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel

@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onOpenReport: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            snackbarHost.showSnackbar(message)
            viewModel.clearStatus()
        }
    }

    val filteredApps = remember(uiState.apps, query) {
        if (query.isBlank()) uiState.apps
        else uiState.apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier.background(SurfaceLight),
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            ScanActionBar(
                modifier = Modifier.navigationBarsPadding(),
                isScanning = uiState.scanState.isScanning,
                canStart = uiState.selectedPackages.isNotEmpty() &&
                    uiState.accessibilityGranted &&
                    uiState.overlayGranted,
                onStart = viewModel::startScan,
                onStop = viewModel::stopScan,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroHeader(
                    selectedCount = uiState.selectedPackages.size,
                    isScanning = uiState.scanState.isScanning,
                )
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureHighlights()
                    PermissionsCard(
                        accessibilityGranted = uiState.accessibilityGranted,
                        accessibilityConnected = uiState.accessibilityConnected,
                        overlayGranted = uiState.overlayGranted,
                        onRefresh = viewModel::refreshPermissions,
                    )
                    ScanOptionsCard(
                        autoLaunchEnabled = uiState.autoLaunchEnabled,
                        onToggleAutoLaunch = viewModel::toggleAutoLaunch,
                    )
                }
            }

            if (uiState.scanState.isScanning) {
                item {
                    ScanDashboard(
                        violations = uiState.scanState.violations,
                        screens = uiState.scanState.scannedScreens,
                        talkBackFindings = uiState.scanState.screenReaderFindings.size,
                        isScanning = true,
                        onOpenReport = onOpenReport,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else if (
                uiState.scanState.violations.isNotEmpty() ||
                uiState.scanState.scannedScreens > 0 ||
                uiState.scanState.screenReaderFindings.isNotEmpty()
            ) {
                item {
                    ScanDashboard(
                        violations = uiState.scanState.violations,
                        screens = uiState.scanState.scannedScreens,
                        talkBackFindings = uiState.scanState.screenReaderFindings.size,
                        isScanning = false,
                        onOpenReport = onOpenReport,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (!uiState.scanState.isScanning) {
                uiState.scanState.lastPdfPath?.let { path ->
                    item {
                        PdfResultCard(
                            path = path,
                            onOpenPdf = { PdfHelper.openPdf(context, path) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Cerca") },
                        placeholder = { Text("Cerca app per nome o package…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "App installate (${uiState.selectedPackages.size}/${uiState.apps.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Tutte le app del telefono, non solo quelle in home",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        if (uiState.isLoadingApps) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
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
                            Text("Tutte")
                        }
                        TextButton(onClick = viewModel::clearSelection) {
                            Text("Nessuna")
                        }
                    }
                }
            }

            if (filteredApps.isEmpty() && !uiState.isLoadingApps) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Nessuna app trovata", color = TextSecondary)
                    }
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    selected = app.packageName in uiState.selectedPackages,
                    viewModel = viewModel,
                    onToggle = { viewModel.toggleApp(app.packageName) },
                    onToggleFavorite = { viewModel.toggleFavorite(app.packageName) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = BrandPrimary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Report PDF salvato", fontWeight = FontWeight.SemiBold)
                    Text(path, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            TextButton(onClick = onOpenPdf) {
                Text("Apri file PDF")
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledAppInfo,
    selected: Boolean,
    viewModel: ScanViewModel,
    onToggle: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BrandPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (app.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (app.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                    tint = if (app.isFavorite) Color(0xFFFFB300) else TextSecondary,
                )
            }
            val drawable = remember(app.packageName) { viewModel.appIcon(app.packageName) }
            if (drawable != null) {
                Image(
                    bitmap = drawable.toBitmap(width = 96, height = 96).asImageBitmap(),
                    contentDescription = "Icona ${app.label}",
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(BrandPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold, color = BrandPrimary)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle),
            ) {
                Text(app.label, fontWeight = FontWeight.Medium)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (app.isSystemApp) {
                    Text("App di sistema", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                if (app.isFavorite) {
                    Text("Preferita", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB300))
                }
            }
            FilterChip(
                selected = selected,
                onClick = onToggle,
                label = { Text(if (selected) "Monitorata" else "Esclusa") },
            )
        }
    }
}

@Composable
private fun ScanActionBar(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onStart,
            enabled = !isScanning && canStart,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Avvia scansione")
        }
        Button(
            onClick = onStop,
            enabled = isScanning,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Danger),
        ) {
            Icon(Icons.Outlined.Stop, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Stop")
        }
    }
}
