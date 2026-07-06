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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.TextSecondary
import dev.accessscope.scanner.ui.theme.severityColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanDashboard(
    violations: List<AccessibilityViolation>,
    screens: Int,
    talkBackFindings: Int,
    isScanning: Boolean,
    modifier: Modifier = Modifier,
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

    val bySeverity = violations.groupBy { it.type.severity }
    val topTypes = violations.groupBy { it.type }
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(Icons.Outlined.ViewCarousel, "$screens", "Schermate")
                StatItem(Icons.Outlined.BugReport, "${violations.size}", "Violazioni")
                StatItem(Icons.Outlined.RecordVoiceOver, "$talkBackFindings", "TalkBack")
                StatItem(Icons.Outlined.Screenshot, "${bySeverity[ViolationSeverity.CRITICAL]?.size ?: 0}", "Critiche")
            }

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
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
