/**
 * Schermata cronologia delle sessioni di scansione archiviate per un'app.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.SessionComparisonHelper
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.components.AppIconAsync
import dev.accessscope.scanner.ui.components.SessionComparisonCard
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Elenco fino a 20 sessioni archiviate per package con dettaglio readonly al tap.
 *
 * @param packageName Package Android della cronologia.
 * @param appLabel Nome visualizzato dell'app.
 * @param viewModel ViewModel con accesso allo store cronologia.
 * @param onBack Torna alla schermata precedente.
 * @param onOpenPdf Apre il PDF associato a una sessione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(
    packageName: String,
    appLabel: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val sessions = remember(packageName) { viewModel.getScanHistory(packageName) }
    var selectedSession by remember { mutableStateOf<ArchivedScanSession?>(null) }
    val dateFormat = remember {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ITALY)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cronologia scansioni")
                        Text(
                            appLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSession != null) selectedSession = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (selectedSession != null) {
            HistorySessionDetail(
                session = selectedSession!!,
                previousSession = sessions.getOrNull(sessions.indexOf(selectedSession!!) - 1),
                dateFormat = dateFormat,
                onOpenPdf = onOpenPdf,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sessions.isEmpty()) {
                    item {
                        Text(
                            "Nessuna sessione archiviata per questa app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentSecondary(),
                        )
                    }
                } else {
                    val reversed = sessions.reversed()
                    items(reversed.size, key = { reversed[it].id }) { index ->
                        val session = reversed[index]
                        // Sessione cronologicamente precedente (per il delta di punteggio)
                        val previousScore = reversed.getOrNull(index + 1)?.score
                        HistorySessionRow(
                            session = session,
                            appLabel = appLabel,
                            previousScore = previousScore,
                            dateFormat = dateFormat,
                            onClick = { selectedSession = session },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Riga sessione stile mockup: icona app, titolo, data mono, trend delta e chip punteggio.
 */
@Composable
private fun HistorySessionRow(
    session: ArchivedScanSession,
    appLabel: String,
    previousScore: Int?,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }
    val delta = previousScore?.let { session.score - it }
    val scoreOk = session.score >= 70

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        session.targetPackages.firstOrNull()?.let { pkg ->
            AppIconAsync(
                packageName = pkg,
                label = appLabel,
                packageManager = packageManager,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                appLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                dateFormat.format(Date(session.completedAtMs)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
            Text(
                "${session.violations.size} problemi · ${session.uniqueScreens} schermate",
                style = MaterialTheme.typography.labelSmall,
                color = contentSecondary(),
            )
        }
        if (delta != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                val (trendIcon, trendColor) = when {
                    delta > 0 -> Icons.AutoMirrored.Outlined.TrendingUp to MaterialTheme.colorScheme.primary
                    delta < 0 -> Icons.AutoMirrored.Outlined.TrendingDown to MaterialTheme.colorScheme.error
                    else -> Icons.AutoMirrored.Outlined.TrendingFlat to contentSecondary()
                }
                Icon(
                    trendIcon,
                    contentDescription = if (delta > 0) "Migliorato" else if (delta < 0) "Peggiorato" else "Invariato",
                    tint = trendColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (delta > 0) "+$delta" else "$delta",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = trendColor,
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(
                    if (scoreOk) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                "${session.score}/100",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = if (scoreOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HistorySessionDetail(
    session: ArchivedScanSession,
    previousSession: ArchivedScanSession?,
    dateFormat: SimpleDateFormat,
    onOpenPdf: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comparison = remember(session, previousSession) {
        if (previousSession != null) {
            SessionComparisonHelper.compare(session, previousSession)
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                dateFormat.format(Date(session.completedAtMs)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            AccessScopeCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Punteggio: ${session.score}", fontWeight = FontWeight.SemiBold, color = BrandPrimary)
                    Text("${session.violations.size} violazioni · ${session.uniqueScreens} schermate")
                    Text("${session.scanAnalyses} analisi · ${session.scanScopeLabel}", color = contentSecondary())
                }
            }
        }
        if (comparison != null) {
            item {
                SessionComparisonCard(comparison = comparison)
            }
        }
        item {
            Text("Violazioni per gravità", style = MaterialTheme.typography.labelLarge)
        }
        val bySeverity = session.violations.groupBy { it.type.severity }
        item {
            Text(
                "Critiche: ${bySeverity[ViolationSeverity.CRITICAL]?.size ?: 0} · " +
                    "Gravi: ${bySeverity[ViolationSeverity.SERIOUS]?.size ?: 0} · " +
                    "Moderate: ${bySeverity[ViolationSeverity.MODERATE]?.size ?: 0}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        session.pdfPath?.let { path ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPdf(path) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = BrandPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Apri report PDF", color = BrandPrimary)
                    }
                }
            }
        }
        item { HorizontalDivider() }
        item { Text("Elenco violazioni", style = MaterialTheme.typography.labelLarge) }
        items(session.violations.take(50), key = { it.dedupeKey + it.timestampMs }) { violation ->
            Column(Modifier.fillMaxWidth()) {
                Text(violation.type.displayName, fontWeight = FontWeight.Medium)
                Text(
                    "${violation.reportSection} · ${violation.viewClassName.substringAfterLast('.')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
        }
        if (session.violations.size > 50) {
            item {
                Text(
                    "… e altre ${session.violations.size - 50} violazioni",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
        }
    }
}
