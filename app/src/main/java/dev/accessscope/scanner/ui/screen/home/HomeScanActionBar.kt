package dev.accessscope.scanner.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.ControlShape
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.viewmodel.ScanDashboardUiState

@Composable
internal fun HomeScanActionBar(
    scanUi: ScanDashboardUiState,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenDynamicReport: () -> Unit = {},
) {
    val isScanning = scanUi.scanState.isScanning
    val canStart = scanUi.selectedPackages.isNotEmpty() &&
        scanUi.accessibilityGranted &&
        scanUi.overlayGranted
    val canOpenDynamicReport = scanUi.scanState.uniqueScreens > 0 ||
        scanUi.scanState.violations.isNotEmpty()
    ScanActionBar(
        modifier = modifier,
        isScanning = isScanning,
        canStart = canStart,
        canOpenDynamicReport = canOpenDynamicReport,
        disabledHint = when {
            canStart -> null
            scanUi.selectedPackages.isEmpty() -> "Seleziona almeno un'app da analizzare"
            !scanUi.accessibilityGranted -> "Attiva il servizio di accessibilità"
            !scanUi.overlayGranted -> "Concedi il permesso overlay"
            else -> "Impossibile avviare la scansione"
        },
        onStart = onStart,
        onStop = onStop,
        onOpenDynamicReport = onOpenDynamicReport,
    )
}

@Composable
internal fun ScanActionBar(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    canStart: Boolean,
    canOpenDynamicReport: Boolean = false,
    disabledHint: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenDynamicReport: () -> Unit = {},
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
            OutlinedButton(
                onClick = onOpenDynamicReport,
                enabled = canOpenDynamicReport,
                modifier = Modifier.semantics {
                    contentDescription = "Report dinamico"
                    if (!canOpenDynamicReport) {
                        stateDescription = "Naviga nell'app analizzata per abilitare il report"
                    }
                },
                shape = ControlShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Outlined.ViewCarousel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Report dinamico", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
