package dev.accessscope.scanner.ui.screen.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.successContainer
import dev.accessscope.scanner.ui.theme.successOnContainer

@Composable
internal fun CheckCoverageCard(
    coverage: List<Pair<ViolationArea, Pair<Int, Int>>>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = successContainer()),
        shape = CardShape,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Copertura controlli per ambito", fontWeight = FontWeight.SemiBold)
            coverage.forEach { (area, counts) ->
                val (passed, failed) = counts
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${area.emoji} ${area.title}", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Text("OK $passed · Problemi $failed", color = successOnContainer(), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun PassedChecksCard(summaries: List<CheckAreaSummary>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = successContainer()),
        shape = CompactShape,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✅ Controlli superati", fontWeight = FontWeight.SemiBold, color = successOnContainer())
            summaries.forEach { summary ->
                Text(
                    "${summary.area.emoji} ${summary.area.title}: ${summary.passedCount} OK",
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                summary.samples.take(3).forEach { sample ->
                    Text(
                        ReportHelper.passedCheckLine(sample),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = contentSecondary(),
                    )
                }
            }
        }
    }
}
