/**
 * Bento "Ultima sessione" della home: donut punteggio, problemi, controlli OK.
 */
package dev.accessscope.scanner.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.contentSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sezione "Ultima sessione" con donut compliance e metriche, stile bento.
 *
 * @param session Ultima sessione archiviata (sorgente di punteggio e conteggi).
 * @param passedTotal Controlli superati, se disponibili dalla sessione live; altrimenti null.
 * @param onOpenDetails Apre il report dettagliato della sessione (zona sessione).
 */
@Composable
internal fun HomeLastSessionCard(
    session: ArchivedScanSession,
    passedTotal: Int?,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ULTIMA SESSIONE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
            TextButton(onClick = onOpenDetails) {
                Text("VEDI DETTAGLI", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Donut compliance
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CompactShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeWidth = 7.dp,
                    )
                    CircularProgressIndicator(
                        progress = { session.score / 100f },
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 7.dp,
                    )
                    Text(
                        "${session.score}%",
                        fontFamily = HankenGroteskFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Compliance", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily, color = contentSecondary())
            }
            // Metriche
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(label = "PROBLEMI", value = "${session.violations.size}", accent = MaterialTheme.colorScheme.error)
                MetricCard(label = "CONTROLLI OK", value = passedTotal?.toString() ?: "—", accent = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${session.uniqueScreens} schermate · ${formatSessionDate(session.completedAtMs)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = contentSecondary(),
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompactShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily, color = contentSecondary())
        }
        Text(
            value,
            fontFamily = HankenGroteskFamily,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

private fun formatSessionDate(ms: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", Locale.ITALY).format(Date(ms))
