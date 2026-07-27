package dev.accessscope.scanner.ui.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.components.SeverityChip
import dev.accessscope.scanner.ui.components.ViolationDetailLine
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.accessScopeFocusRing
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import kotlin.math.roundToInt

@Composable
internal fun ViolationCard(
    violation: AccessibilityViolation,
    packageLabels: Map<String, String>,
    onOpen: () -> Unit,
) {
    val type = violation.type
    val appLabel = packageLabels[violation.packageName] ?: violation.packageName
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .accessScopeFocusRing(shape = cardShape, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen,
            ),
    ) {
        // Barra laterale di gravità
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(severityColor(type.severity)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(type.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Vedi dettaglio",
                    tint = contentSecondary(),
                    modifier = Modifier.size(20.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(severity = type.severity)
                Text(
                    type.wcagRef,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${(violation.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = contentSecondary(),
                )
            }
            Text(violation.simpleExplanation, style = MaterialTheme.typography.bodySmall)
            ReportHelper.violationDetailLines(violation).forEach { line ->
                ViolationDetailLine(
                    line = line,
                    violation = violation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("App: $appLabel", style = MaterialTheme.typography.labelSmall, color = contentSecondary())
        }
    }
}

@Composable
internal fun TalkBackFindingCard(
    finding: ScreenReaderFinding,
    packageLabels: Map<String, String>,
) {
    val appLabel = packageLabels[finding.packageName] ?: finding.packageName
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(finding.issue, style = MaterialTheme.typography.bodyMedium)
        finding.announcedText?.let {
            Text("Annuncio: \"$it\"", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        }
        Text(
            "${finding.screenTitle} · $appLabel",
            style = MaterialTheme.typography.labelSmall,
            color = contentSecondary(),
        )
    }
}
