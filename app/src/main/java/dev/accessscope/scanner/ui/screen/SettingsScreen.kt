/**
 * Schermata impostazioni "Scanner & HUD": sezioni accordion e danger zone.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.components.SettingsAccordion
import dev.accessscope.scanner.ui.components.ThemeModeSelector
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.Warning
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.PermissionHelper

/**
 * Schermata delle impostazioni (tab zona principale) con sezioni espandibili.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenLogChecker: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = uiState.scanScope
    val context = LocalContext.current
    var exportingLogs by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Eliminare la cronologia?") },
            text = { Text("Tutte le sessioni archiviate e i confronti tra scansioni verranno eliminati definitivamente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearScanHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "Cronologia eliminata", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Annulla")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            AccessScopeTopBar(title = "Impostazioni", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // —— Permessi richiesti ——
            val a11yReady = uiState.accessibilityGranted && uiState.accessibilityConnected
            val grantedCount = listOf(a11yReady, uiState.overlayGranted).count { it }
            SettingsAccordion(
                title = "Permessi Richiesti",
                icon = Icons.Outlined.Security,
                badge = "$grantedCount/2",
            ) {
                PermissionRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = "Servizio di accessibilità",
                    subtitle = when {
                        a11yReady -> "Attivo e connesso"
                        uiState.accessibilityGranted ->
                            "ON ma non collegato — OFF → attendi → ON in Accessibilità"
                        else -> "Da abilitare nelle impostazioni di sistema"
                    },
                    granted = a11yReady,
                    warning = uiState.accessibilityGranted && !uiState.accessibilityConnected,
                )
                PermissionRow(
                    icon = Icons.Outlined.Layers,
                    title = "Mostra sopra altre app",
                    subtitle = "Overlay STOP disponibile durante la scansione",
                    granted = uiState.overlayGranted,
                )
                if (!a11yReady) {
                    OutlinedButton(
                        onClick = {
                            PermissionHelper.safeStartSettingsIntent(
                                context,
                                PermissionHelper.accessibilityServiceIntent(
                                    context,
                                    AccessScopeAccessibilityService::class.java,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (uiState.accessibilityGranted) {
                                "Ripristina collegamento accessibilità"
                            } else {
                                "Apri impostazioni accessibilità"
                            },
                        )
                    }
                }
                OutlinedButton(
                    onClick = viewModel::refreshPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Aggiorna stato permessi")
                }
            }

            // —— Ambiti di scansione ——
            SettingsAccordion(
                title = "Ambiti di Scansione",
                icon = Icons.Outlined.ViewQuilt,
                initiallyExpanded = false,
            ) {
                // Il vecchio switch "Analizza tutto" era one-way (spegnerlo non faceva nulla):
                // il preset «Completa» qui sotto copre lo stesso caso in modo onesto.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scope.isFullScan,
                        onClick = viewModel::setFullScan,
                        label = { Text("Completa") },
                    )
                    FilterChip(
                        selected = scope.enabledAreas == setOf(ViolationArea.SCREEN_READER),
                        onClick = viewModel::applyTalkBackOnlyPreset,
                        label = { Text("Solo TalkBack") },
                    )
                    FilterChip(
                        selected = scope.enabledAreas == setOf(ViolationArea.LABELS),
                        onClick = viewModel::applyLabelsOnlyPreset,
                        label = { Text("Solo etichette") },
                    )
                    FilterChip(
                        selected = scope.enabledAreas == setOf(ViolationArea.COLOR),
                        onClick = viewModel::applyContrastOnlyPreset,
                        label = { Text("Solo contrasto") },
                    )
                }
                if (!scope.isFullScan) {
                    Text(
                        "La prossima scansione analizzerà solo gli ambiti selezionati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // —— Categorie di controllo ——
            SettingsAccordion(
                title = "Categorie di Controllo",
                icon = Icons.Outlined.FactCheck,
                initiallyExpanded = false,
            ) {
                ViolationArea.entries.forEach { area ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("${area.emoji} ${area.title}", fontWeight = FontWeight.Medium)
                            Text(
                                area.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentSecondary(),
                            )
                        }
                        Switch(
                            checked = scope.includes(area),
                            onCheckedChange = { viewModel.toggleScanArea(area) },
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            // —— Preferenze app ——
            SettingsAccordion(
                title = "Preferenze App",
                icon = Icons.Outlined.SettingsSuggest,
                initiallyExpanded = false,
            ) {
                Text(
                    "VISUALIZZAZIONE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = contentSecondary(),
                )
                ThemeModeSelector(
                    selected = uiState.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Apri app automaticamente")
                        Text(
                            "All'avvio apre l'app selezionata (max ${ScanViewModel.MAX_APPS_WITH_AUTO_LAUNCH}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                        )
                    }
                    Switch(
                        checked = uiState.autoLaunchEnabled,
                        onCheckedChange = { viewModel.toggleAutoLaunch() },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Report affidabilità (MD)")
                        Text(
                            "Salva benchmark anti-allucinazione a fine scansione.",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                        )
                    }
                    Switch(
                        checked = uiState.reliabilityReportEnabled,
                        onCheckedChange = { viewModel.toggleReliabilityReport() },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Findings a bassa confidenza")
                        Text(
                            "Di default esclusi dal report (meno rumore). Attiva solo per debug.",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                        )
                    }
                    Switch(
                        checked = uiState.includeLowConfidenceFindings,
                        onCheckedChange = { viewModel.toggleIncludeLowConfidenceFindings() },
                    )
                }
            }

            // —— Diagnostica ——
            SettingsAccordion(
                title = "Diagnostica",
                icon = Icons.Outlined.Terminal,
                initiallyExpanded = false,
            ) {
                Text(
                    "Scarica i log dell'app per analizzare crash o comportamenti anomali durante le scansioni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
                Button(
                    onClick = onOpenLogChecker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null)
                    Text("Apri log checker (tempo reale)", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        if (exportingLogs) return@OutlinedButton
                        exportingLogs = true
                        viewModel.exportDiagnosticLogs { result ->
                            exportingLogs = false
                            result.fold(
                                onSuccess = { path ->
                                    Toast.makeText(context, "Log salvati in $path", Toast.LENGTH_LONG).show()
                                    dev.accessscope.scanner.export.DiagnosticLogExporter.shareExportedFile(context, path)
                                },
                                onFailure = { error ->
                                    Toast.makeText(context, error.message ?: "Impossibile esportare i log", Toast.LENGTH_LONG).show()
                                },
                            )
                        }
                    },
                    enabled = !exportingLogs,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (exportingLogs) "Esportazione…" else "Scarica log diagnostici")
                }
            }

            // —— Informazioni legali ——
            SettingsAccordion(
                title = "Informazioni Legali",
                icon = Icons.Outlined.Policy,
                initiallyExpanded = false,
            ) {
                Text(
                    "AccessScope analizza le app localmente sul dispositivo: nessun dato lascia il device senza azione esplicita dell'utente (esport PDF, feedback o log).",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
                Text(
                    "Repository e documentazione: github.com/xdrake96p/AccessScope",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // —— Suggerimenti e segnalazioni ——
            SettingsAccordion(
                title = "Suggerimenti e segnalazioni",
                icon = Icons.Outlined.Chat,
                initiallyExpanded = false,
            ) {
                Text(
                    "Segnala bug, scansioni imprecise o idee di miglioramento. Si apre GitHub Issues con i campi precompilati.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
                Button(
                    onClick = onOpenFeedback,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Feedback, contentDescription = null)
                    Text("Invia feedback su GitHub", modifier = Modifier.padding(start = 8.dp))
                }
            }

            // —— Danger zone ——
            Spacer(Modifier.height(8.dp))
            Text(
                "DANGER ZONE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                    .clickable { showClearHistoryDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Elimina Cronologia Scansioni",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Riga di stato permesso con icona e check. */
@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    warning: Boolean = false,
) {
    val tint = when {
        granted -> MaterialTheme.colorScheme.primary
        warning -> Warning
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        granted -> "OK"
        warning -> "SCOLLEGATO"
        else -> "MANCANTE"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}
