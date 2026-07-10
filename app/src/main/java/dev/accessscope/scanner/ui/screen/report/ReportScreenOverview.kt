package dev.accessscope.scanner.ui.screen.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.report.ReportSectionGroup
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.brandHighlightContainer
import dev.accessscope.scanner.ui.theme.contentSecondary

@Composable
internal fun ScreenOverviewCard(entries: List<ReportHelper.ScreenOverviewEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = brandHighlightContainer()),
        shape = CardShape,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Panoramica schermate", fontWeight = FontWeight.SemiBold)
            entries.forEach { entry ->
                val isClean = entry.violationCount == 0
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.screenTitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.passedCount > 0) {
                            Text(
                                "${entry.passedCount} OK",
                                fontWeight = FontWeight.Medium,
                                color = Success,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(
                            if (isClean) "0 problemi" else "${entry.violationCount}",
                            fontWeight = FontWeight.Bold,
                            color = if (isClean) Success else BrandPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeaderCard(
    section: ReportSectionGroup,
    violationCount: Int,
    talkBackCount: Int,
    passedCount: Int = 0,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    section.screenTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (section.hasSubsection) {
                    Text(
                        "Sezione: ${section.sectionTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandPrimary,
                    )
                }
                Text(
                    buildString {
                        append("$violationCount problemi")
                        if (passedCount > 0) append(" · $passedCount OK")
                        if (talkBackCount > 0) append(" · $talkBackCount note TalkBack")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentSecondary(),
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Comprimi" else "Espandi",
            )
        }
    }
}
