/**
 * Pannello Compose per il debug live durante la scansione.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.LiveDebugFinding
import dev.accessscope.scanner.data.LiveScanSnapshot
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.brandHighlightContainer
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pannello debug live con schermata corrente, metriche e ultimi problemi.
 *
 * @param scanState Stato sessione corrente dal repository.
 * @param packageLabels Mappa package → etichetta leggibile.
 * @param modifier Modifier esterno.
 */
@Composable
fun LiveDebugPanel(
    scanState: ScanSessionState,
    packageLabels: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val snapshot = scanState.liveSnapshot
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.ITALY) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Debug live", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (snapshot != null) {
            LiveMetricCard(
                icon = { Icon(Icons.Outlined.Layers, null, tint = MaterialTheme.colorScheme.primary) },
                title = "In analisi ora",
                lines = listOf(
                    packageLabels[snapshot.packageName] ?: snapshot.packageName,
                    snapshot.screenTitle,
                    "Aggiornato alle ${timeFmt.format(Date(snapshot.analyzedAtMs))}",
                ),
            )
            if (snapshot.newViolationsInPass > 0) {
                Text(
                    "+${snapshot.newViolationsInPass} problemi nell'ultimo passaggio",
                    color = severityColor(snapshot.recentFindings.firstOrNull()?.severity
                        ?: dev.accessscope.scanner.data.ViolationSeverity.MODERATE),
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text(
                "In attesa del primo evento di accessibilità…",
                style = MaterialTheme.typography.bodyMedium,
                color = contentSecondary(),
            )
        }

        LiveMetricCard(
            icon = { Icon(Icons.Outlined.BugReport, null, tint = Success) },
            title = "Sessione",
            lines = listOf(
                "${scanState.violations.size} problemi totali",
                "${scanState.uniqueScreens} schermate · ${scanState.scanAnalyses} analisi",
                "${scanState.checkSummaries.sumOf { it.passedCount }} controlli OK",
            ),
        )

        val findings = snapshot?.recentFindings.orEmpty().ifEmpty {
            scanState.violations.takeLast(5).map { v ->
                LiveDebugFinding(
                    title = v.type.displayName,
                    detail = v.elementLabel ?: v.viewId ?: v.simpleExplanation,
                    severity = v.type.severity,
                    screenTitle = v.screenTitle,
                )
            }
        }

        if (findings.isNotEmpty()) {
            Text("Ultimi rilevamenti", fontWeight = FontWeight.SemiBold)
            findings.forEach { finding ->
                LiveFindingRow(finding)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        } else {
            Text("Nessun problema rilevato finora.", color = Success, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LiveMetricCard(
    icon: @Composable () -> Unit,
    title: String,
    lines: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(brandHighlightContainer().copy(alpha = 0.55f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        lines.forEach { line ->
            Text(line, style = if (line.contains('.')) CodeTextStyle else MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LiveFindingRow(finding: LiveDebugFinding) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(severityColor(finding.severity))
                .align(Alignment.Top)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = severityColor(finding.severity),
                )
                Text(
                    finding.title,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
            Text(finding.screenTitle, style = MaterialTheme.typography.labelSmall, color = contentSecondary())
        }
    }
}
