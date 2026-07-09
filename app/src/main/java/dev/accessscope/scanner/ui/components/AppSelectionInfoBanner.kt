/**
 * Banner informativo Material per le regole di selezione app.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.selection.AppSelectionPolicy
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Banner che spiega il limite di selezione e il comportamento del lancio automatico.
 *
 * @param autoLaunchEnabled Se true, evidenzia l'apertura automatica all'avvio scansione.
 * @param maxMonitoredApps Numero massimo di app monitorabili per sessione.
 * @param modifier Modifier esterno.
 */
@Composable
fun AppSelectionInfoBanner(
    autoLaunchEnabled: Boolean,
    maxMonitoredApps: Int = AppSelectionPolicy.MAX_MONITORED_APPS,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (autoLaunchEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (autoLaunchEnabled) Icons.Outlined.RocketLaunch else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (autoLaunchEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (autoLaunchEnabled) "Lancio automatico attivo" else "Selezione singola",
                    fontWeight = FontWeight.SemiBold,
                    color = if (autoLaunchEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    if (autoLaunchEnabled) {
                        "Puoi monitorare solo $maxMonitoredApps app per sessione. " +
                            "All'avvio della scansione verrà aperta automaticamente."
                    } else {
                        "Puoi monitorare solo $maxMonitoredApps app per sessione. " +
                            "Per cambiare app, deseleziona quella corrente prima di sceglierne un'altra."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoLaunchEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                    } else {
                        contentSecondary()
                    },
                )
            }
        }
    }
}
