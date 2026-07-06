/**
 * Card riepilogo confronto tra ultima e penultima sessione archiviata.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.report.SessionComparison
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Mostra il delta numerico tra due sessioni: nuovi, risolti e variazione punteggio.
 *
 * @param comparison Dati di confronto; null per non mostrare nulla.
 * @param modifier Modifier Compose applicato al contenitore.
 */
@Composable
fun SessionComparisonCard(
    comparison: SessionComparison?,
    modifier: Modifier = Modifier,
) {
    if (comparison == null || !comparison.hasDelta) return

    val scoreSign = when {
        comparison.scoreDelta > 0 -> "+${comparison.scoreDelta}"
        comparison.scoreDelta < 0 -> "${comparison.scoreDelta}"
        else -> "0"
    }
    val scoreColor = when {
        comparison.scoreDelta > 0 -> Success
        comparison.scoreDelta < 0 -> MaterialTheme.colorScheme.error
        else -> contentSecondary()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandPrimary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Rispetto alla sessione precedente",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandPrimary,
        )
        Text(
            buildString {
                append("+${comparison.newCount} nuovi")
                append(" · −${comparison.resolvedCount} risolti")
                append(" · punteggio $scoreSign")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scoreColor,
        )
        if (comparison.unchangedCount > 0) {
            Text(
                "${comparison.unchangedCount} invariati",
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary(),
            )
        }
    }
}
