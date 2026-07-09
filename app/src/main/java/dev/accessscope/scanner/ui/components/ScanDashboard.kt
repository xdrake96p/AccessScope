/**
 * Dashboard live con metriche OK/KO della sessione di scansione.
 *
 * Mostra punteggio, violazioni per gravità, chip dei problemi principali
 * e pulsante per aprire il report completo al termine della scansione.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
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
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.AiPromptInput
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.report.SessionComparison
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.Warning
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor

/**
 * Card dashboard con statistiche live o dell'ultima sessione di scansione.
 *
 * @param violations Elenco grezzo delle violazioni rilevate.
 * @param screens Numero di schermate uniche visitate.
 * @param talkBackFindings Numero di note dalla simulazione TalkBack.
 * @param isScanning True se la scansione è in corso (attiva animazione e messaggi).
 * @param onOpenReport Callback per navigare al report dettagliato.
 * @param modifier Modifier Compose applicato alla card.
 * @param scanAnalyses Numero totale di analisi eseguite; mostrato se supera [screens].
 * @param isPartialScan True se la scansione non copre tutti gli ambiti.
 * @param scanScopeLabel Etichetta leggibile degli ambiti attivi.
 * @param screenReaderFindings Elenco note TalkBack per il prompt AI.
 * @param targetPackages Package analizzati nella sessione.
 * @param packageLabels Mappa package → nome app per il prompt.
 * @param onAiPromptCopied Callback dopo copia prompt negli appunti.
 * @param sessionComparison Confronto con la sessione precedente archiviata.
 */
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
    screenReaderFindings: List<ScreenReaderFinding> = emptyList(),
    targetPackages: Set<String> = emptySet(),
    packageLabels: Map<String, String> = emptyMap(),
    onAiPromptCopied: () -> Unit = {},
    sessionComparison: SessionComparison? = null,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
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

    val aiPromptInput = remember(
        violations,
        screenReaderFindings,
        targetPackages,
        packageLabels,
        screens,
        scanScopeLabel,
    ) {
        AiPromptInput(
            violations = violations,
            screenReaderFindings = screenReaderFindings,
            targetPackageNames = targetPackages,
            packageLabels = packageLabels,
            uniqueScreens = screens,
            scanScopeLabel = scanScopeLabel,
        )
    }
    val canExportAiPrompt = filtered.isNotEmpty() || screenReaderFindings.isNotEmpty()

    AccessScopeCard(
        modifier = modifier.fillMaxWidth(),
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
                    .background(if (isScanning) Success else contentSecondary()),
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
                "AccessScope è in ascolto. Apri l'app selezionata e naviga tra le schermate.",
                style = MaterialTheme.typography.bodyMedium,
                color = contentSecondary(),
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
                        contentSecondary(),
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
                SessionComparisonCard(
                    comparison = sessionComparison,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                CopyAiPromptButton(
                    input = aiPromptInput,
                    enabled = canExportAiPrompt,
                    onCopied = onAiPromptCopied,
                )
                if (canExportAiPrompt) {
                    Text(
                        "Incolla il prompt in ChatGPT, Claude, Gemini o altro assistente per ottenere fix mirati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentSecondary(),
                    )
                }
            }
        }
    }
}

/**
 * Dati di una singola metrica nella dashboard (icona, valore, etichetta, colore).
 *
 * @param icon Icona Material associata alla metrica.
 * @param value Valore formattato da mostrare.
 * @param label Etichetta breve sotto il valore.
 * @param tint Colore di icona e valore numerico.
 */
private data class StatData(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val tint: Color,
)

/**
 * Blocco con due righe di statistiche: metriche positive (OK) e negative (KO).
 *
 * @param okLabel Etichetta della sezione metriche positive.
 * @param koLabel Etichetta della sezione metriche negative.
 * @param okStats Elenco metriche positive da visualizzare.
 * @param koStats Elenco metriche negative da visualizzare.
 */
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

/**
 * Riga di statistiche con etichetta di sezione e valori affiancati.
 *
 * @param sectionLabel Titolo della sezione (es. "OK" o "KO").
 * @param sectionColor Colore tematico della sezione.
 * @param stats Elenco di [StatData] da mostrare in orizzontale.
 */
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

/**
 * Singola cella statistica con icona, valore numerico e etichetta.
 *
 * @param icon Icona Material della metrica.
 * @param value Valore formattato.
 * @param label Etichetta descrittiva breve.
 * @param tint Colore di icona e valore.
 */
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentSecondary())
    }
}

/**
 * Chip colorato che riassume un tipo di violazione e il relativo conteggio.
 *
 * @param type Tipo di violazione con nome e gravità.
 * @param count Numero di occorrenze di questo tipo.
 */
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
