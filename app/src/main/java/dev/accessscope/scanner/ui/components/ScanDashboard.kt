package dev.accessscope.scanner.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.TextSecondary
import dev.accessscope.scanner.ui.theme.Warning
import dev.accessscope.scanner.ui.theme.severityColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanDashboard(
    violations: List<AccessibilityViolation>,
    screens: Int,
    talkBackFindings: Int,
    isScanning: Boolean,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
    scanAnalyses: Int = 0,
    isPartialScan: Boolean = false,
    scanScopeLabel: String = "Completa",
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val filtered = remember(violations) { ReportHelper.filterViolations(violations) }
    val score = ReportHelper.computeScore(filtered, screens)
    val cleanAreas = ReportHelper.cleanAreaCount(filtered, talkBackFindings)
    val bySeverity = filtered.groupBy { it.type.severity }
    val topTypes = filtered.groupBy { it.type }
        .entries
        .sortedByDescending { it.value.size }
        .take(6)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = BrandPrimary.copy(alpha = if (isScanning) 0.10f else 0.06f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(if (isScanning) pulse else 1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isScanning) Color(0xFF43A047) else TextSecondary),
                )
                Text(
                    if (isScanning) "Scansione in corso" else "Ultima sessione",
                    fontWeight = FontWeight.SemiBold,
                )
                if (isPartialScan) {
                    Text(
                        "Parziale: $scanScopeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning,
                    )
                }
            }

            val hasLiveData = screens > 0 || filtered.isNotEmpty() || talkBackFindings > 0

            if (isScanning && !hasLiveData) {
                Text(
                    "In attesa dell'app selezionata. Apri l'app e naviga tra le schermate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
            SessionStatsBlock(
                okLabel = "OK",
                koLabel = "KO",
                okStats = listOf(
                    StatData(Icons.Outlined.ViewCarousel, "$screens", "Schermate", Success),
                    StatData(Icons.Outlined.Star, "$score", "Punteggio", Success),
                    StatData(Icons.Outlined.CheckCircle, "$cleanAreas/8", "Aree ok", Success),
                ).let { stats ->
                    if (scanAnalyses > screens) stats + StatData(
                        Icons.Outlined.ViewCarousel,
                        "$scanAnalyses",
                        "Analisi",
                        TextSecondary,
                    ) else stats
                },
                koStats = listOf(
                    StatData(Icons.Outlined.BugReport, "${filtered.size}", "Violazioni", Danger),
                    StatData(Icons.Outlined.RecordVoiceOver, "$talkBackFindings", "TalkBack", Warning),
                    StatData(
                        Icons.Outlined.Error,
                        "${bySeverity[ViolationSeverity.CRITICAL]?.size ?: 0}",
                        "Critiche",
                        Danger,
                    ),
                    StatData(
                        Icons.Outlined.Warning,
                        "${bySeverity[ViolationSeverity.SERIOUS]?.size ?: 0}",
                        "Gravi",
                        Warning,
                    ),
                ),
            )

            if (topTypes.isNotEmpty()) {
                Text("Principali problemi", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topTypes.forEach { (type, items) ->
                        ViolationChip(type = type, count = items.size)
                    }
                }
            }

            if (!isScanning) {
                Button(
                    onClick = onOpenReport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Icon(Icons.Outlined.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Vedi report completo")
                }
            }
            }
        }
    }
}

private data class StatData(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val tint: Color,
)

@Composable
private fun SessionStatsBlock(
    okLabel: String,
    koLabel: String,
    okStats: List<StatData>,
    koStats: List<StatData>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatsRow(
            sectionLabel = okLabel,
            sectionColor = Success,
            stats = okStats,
        )
        StatsRow(
            sectionLabel = koLabel,
            sectionColor = Danger,
            stats = koStats,
        )
    }
}

@Composable
private fun StatsRow(
    sectionLabel: String,
    sectionColor: Color,
    stats: List<StatData>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(sectionColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            sectionLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = sectionColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            stats.forEach { stat ->
                StatItem(
                    icon = stat.icon,
                    value = stat.value,
                    label = stat.label,
                    tint = stat.tint,
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun ViolationChip(type: ViolationType, count: Int) {
    Text(
        text = "${type.displayName} ($count)",
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(severityColor(type.severity).copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = severityColor(type.severity),
    )
}
