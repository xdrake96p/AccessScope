/**
 * Lista problemi e note TalkBack per la schermata corrente del report dinamico.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.report.DynamicScreenFrame
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor

/**
 * Elenco problemi della schermata selezionata con evidenziazione della voce attiva.
 */
@Composable
fun ScreenIssueList(
    frame: DynamicScreenFrame,
    violations: List<AccessibilityViolation>,
    selectedDedupeKey: String?,
    onViolationClick: (AccessibilityViolation, Int) -> Unit,
    onOpenViolationDetail: (AccessibilityViolation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                frame.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (frame.passedCount > 0) {
                Text(
                    "${frame.passedCount} controlli superati",
                    style = MaterialTheme.typography.labelMedium,
                    color = Success,
                )
            }
        }

        if (violations.isEmpty() && frame.talkBackFindings.isEmpty()) {
            Text(
                "Nessun problema rilevato su questa schermata.",
                style = MaterialTheme.typography.bodyMedium,
                color = Success,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        violations.forEachIndexed { index, violation ->
            val isSelected = violation.dedupeKey == selectedDedupeKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(IntrinsicSize.Min)
                    .clip(CardShape)
                    .background(
                        if (isSelected) {
                            severityColor(violation.type.severity).copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    )
                    .clickable {
                        onViolationClick(violation, index)
                        onOpenViolationDetail(violation)
                    },
            ) {
                // Barra laterale di gravità
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(severityColor(violation.type.severity)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            violation.type.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Vedi dettaglio",
                            tint = contentSecondary(),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SeverityChip(severity = violation.type.severity)
                        Text(
                            violation.type.wcagRef,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    violation.details.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        if (frame.talkBackFindings.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Text(
                "TalkBack",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            frame.talkBackFindings.forEach { finding ->
                TalkBackFindingRow(finding = finding)
            }
        }
    }
}

@Composable
private fun TalkBackFindingRow(finding: ScreenReaderFinding) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.RecordVoiceOver,
                contentDescription = null,
                tint = contentSecondary(),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp),
            )
            Column {
                Text(finding.issue, style = MaterialTheme.typography.bodyMedium)
                finding.announcedText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Annunciato: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentSecondary(),
                    )
                }
            }
        }
    }
}
