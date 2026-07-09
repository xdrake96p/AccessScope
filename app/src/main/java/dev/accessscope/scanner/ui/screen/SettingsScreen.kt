/**
 * Schermata impostazioni per auto-launch e configurazione degli ambiti di scansione.
 *
 * Permette di attivare preset (completa, solo TalkBack, solo etichette, solo contrasto)
 * o personalizzare singoli ambiti tramite switch.
 */
package dev.accessscope.scanner.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.ui.components.ThemeModeSelector
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel

/**
 * Schermata delle impostazioni di scansione e ambiti di analisi.
 *
 * @param viewModel ViewModel con stato auto-launch e ambiti di scansione.
 * @param onBack Callback per tornare alla schermata precedente.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = uiState.scanScope

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preferenze di visualizzazione", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Scegli come visualizzare l'interfaccia. La preferenza viene salvata sul dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentSecondary(),
                    )
                    ThemeModeSelector(
                        selected = uiState.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scansione", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Apri app automaticamente")
                            Text(
                                "All'avvio apre automaticamente l'app selezionata. Puoi monitorare solo ${ScanViewModel.MAX_APPS_WITH_AUTO_LAUNCH} app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentSecondary(),
                            )
                        }
                        Switch(
                            checked = uiState.autoLaunchEnabled,
                            onCheckedChange = { viewModel.toggleAutoLaunch() },
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Debug interno", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Strumenti per sviluppo e benchmark anti-allucinazione. I report non sono pensati per l'utente finale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentSecondary(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pannello ispezione in tempo reale")
                            Text(
                                "Durante la scansione mostra schermata corrente, problemi e metriche. Disponibile nell'overlay (icona occhio) e in Home.",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentSecondary(),
                            )
                        }
                        Switch(
                            checked = uiState.liveDebugPanelEnabled,
                            onCheckedChange = { viewModel.toggleLiveDebugPanel() },
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Report affidabilità (Markdown)")
                            Text(
                                "A fine scansione salva in Download un file AccessScope_Reliability_*.md con violazioni, confidenza, pattern sospetti e confronto benchmark Nexi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentSecondary(),
                            )
                        }
                        Switch(
                            checked = uiState.reliabilityReportEnabled,
                            onCheckedChange = { viewModel.toggleReliabilityReport() },
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ambiti di scansione", fontWeight = FontWeight.SemiBold)
                    if (!scope.isFullScan) {
                        Text(
                            "La prossima scansione analizzerà solo gli ambiti selezionati.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandPrimary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Analizza tutto")
                        Switch(
                            checked = scope.isFullScan,
                            onCheckedChange = { enabled ->
                                if (enabled) viewModel.setFullScan() else Unit
                            },
                        )
                    }
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
                    HorizontalDivider()
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
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
