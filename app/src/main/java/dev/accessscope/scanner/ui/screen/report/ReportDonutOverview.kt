/**
 * Panoramica a donut "Distribuzione Risultati" del report (tab Scansione).
 */
package dev.accessscope.scanner.ui.screen.report

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Sezione panoramica con donut del punteggio e strip TOTALE / OK / KO.
 *
 * @param score Punteggio di conformità 0–100 (centro del donut).
 * @param totalChecks Controlli totali eseguiti (OK + KO).
 * @param passedChecks Controlli superati.
 * @param violationCount Problemi rilevati.
 */
@Composable
internal fun ReportDonutOverview(
    score: Int,
    totalChecks: Int,
    passedChecks: Int,
    violationCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScoreDonut(score = score)
        Spacer(Modifier.height(16.dp))
        Text(
            "Distribuzione Risultati",
            fontFamily = HankenGroteskFamily,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Analisi granulare basata sui controlli eseguiti durante la sessione di scansione.",
            style = MaterialTheme.typography.bodyMedium,
            color = contentSecondary(),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("TOTALE", "$totalChecks", MaterialTheme.colorScheme.onSurface)
            StatCell("OK", "$passedChecks", MaterialTheme.colorScheme.primary)
            StatCell("KO", "$violationCount", MaterialTheme.colorScheme.error)
        }
    }
}

/** Donut spesso 12dp con track, arco primary e punteggio al centro. */
@Composable
private fun ScoreDonut(score: Int, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progressColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score%",
                fontFamily = HankenGroteskFamily,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "CONFORME",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = contentSecondary(),
        )
        Text(
            value,
            fontFamily = HankenGroteskFamily,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}
