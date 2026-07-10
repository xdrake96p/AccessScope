package dev.accessscope.scanner.ui.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.theme.severityColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeverityFilterRow(
    selected: ViolationSeverity?,
    counts: Map<ViolationSeverity, Int>,
    onSelect: (ViolationSeverity?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Tutti") },
        )
        ViolationSeverity.entries.forEach { severity ->
            val count = counts[severity] ?: 0
            if (count == 0) return@forEach
            FilterChip(
                selected = selected == severity,
                onClick = { onSelect(if (selected == severity) null else severity) },
                label = { Text("${severity.label} ($count)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = severityColor(severity).copy(alpha = 0.18f),
                    selectedLabelColor = severityColor(severity),
                ),
            )
        }
    }
}

@Composable
internal fun SeverityGroupHeader(severity: ViolationSeverity, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(severityColor(severity).copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ReportHelper.severityEmoji(severity), style = MaterialTheme.typography.bodyMedium)
        Text(
            "${ReportHelper.severityGroupTitle(severity)} ($count)",
            fontWeight = FontWeight.SemiBold,
            color = severityColor(severity),
        )
    }
}
