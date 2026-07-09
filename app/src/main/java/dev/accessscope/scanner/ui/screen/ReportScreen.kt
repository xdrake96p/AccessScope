/**
 * Schermata del report di accessibilità con riepilogo, filtri e dettaglio violazioni.
 *
 * Presenta punteggio, copertura controlli, sezioni espandibili per schermata
 * e glossario dei termini usati nel report.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.successContainer
import dev.accessscope.scanner.ui.theme.successOnContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.PassedCheck
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.AiPromptInput
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.components.CopyAiPromptButton
import dev.accessscope.scanner.ui.components.SessionComparisonCard
import dev.accessscope.scanner.report.ReportSectionGroup
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.BrandDark
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.BrandSecondary
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.brandHighlightContainer
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Schermata completa del report di accessibilità della sessione corrente.
 *
 * @param viewModel ViewModel con lo stato della scansione e l'elenco app per le etichette.
 * @param onBack Callback per tornare alla schermata precedente.
 * @param onOpenPdf Callback che riceve il percorso del PDF e lo apre nel viewer di sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scan = uiState.scanState
    val violations = remember(scan.violations) { ReportHelper.filterViolations(scan.violations) }
    val score = ReportHelper.computeScore(violations, scan.uniqueScreens)
    var severityFilter by rememberSaveable { mutableStateOf<ViolationSeverity?>(null) }
    val filteredViolations = remember(violations, severityFilter) {
        severityFilter?.let { s -> violations.filter { it.type.severity == s } } ?: violations
    }
    val checkSummaries = remember(scan.checkSummaries) { scan.checkSummaries }
    val passedTotal = remember(checkSummaries) { ReportHelper.totalPassedChecks(checkSummaries) }
    val coverage = remember(checkSummaries, violations) {
        ReportHelper.globalCheckCoverage(checkSummaries, violations)
    }
    val screenOverview = remember(violations, checkSummaries) {
        ReportHelper.screenOverview(violations, checkSummaries)
    }
    val sectionGroups = remember(filteredViolations) {
        ReportHelper.groupViolationsBySection(filteredViolations)
    }
    val talkBackBySection = remember(scan.screenReaderFindings) {
        ReportHelper.groupTalkBackBySection(scan.screenReaderFindings)
    }
    val packageLabels = remember(uiState.apps) {
        uiState.apps.associate { it.packageName to it.label }
    }
    var expandedSections by rememberSaveable { mutableStateOf(setOf<String>()) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val aiPromptInput = remember(scan, violations, packageLabels) {
        AiPromptInput(
            violations = scan.violations,
            screenReaderFindings = scan.screenReaderFindings,
            targetPackageNames = scan.selectedPackages,
            packageLabels = packageLabels,
            uniqueScreens = scan.uniqueScreens,
            scanScopeLabel = scan.scanScope.label(),
        )
    }

    fun sectionKey(section: ReportSectionGroup) = "${section.screenTitle}|${section.sectionTitle}"
    fun toggleSection(section: ReportSectionGroup) {
        val key = sectionKey(section)
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Report accessibilità") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    scan.lastPdfPath?.let { path ->
                        IconButton(onClick = { onOpenPdf(path) }) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Apri PDF")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.navigationBarsPadding(),
    ) { padding ->
        if (violations.isEmpty() && scan.screenReaderFindings.isEmpty() && passedTotal == 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Nessun problema rilevato nella sessione.", color = contentSecondary())
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ReportSummaryCard(
                    score = score,
                    scannedScreens = scan.uniqueScreens,
                    scanAnalyses = scan.scanAnalyses,
                    scanScopeLabel = scan.scanScope.label(),
                    appCount = scan.selectedPackages.size,
                    violationCount = violations.size,
                    talkBackCount = scan.screenReaderFindings.size,
                    passedCheckCount = passedTotal,
                )
            }

            uiState.sessionComparison?.let { comparison ->
                item(key = "session_comparison") {
                    SessionComparisonCard(comparison = comparison)
                }
            }

            item(key = "copy_ai_prompt") {
                CopyAiPromptButton(
                    input = aiPromptInput,
                    enabled = violations.isNotEmpty() || scan.screenReaderFindings.isNotEmpty(),
                    onCopied = {
                        scope.launch {
                            snackbarHost.showSnackbar("Prompt AI copiato negli appunti")
                        }
                    },
                )
            }

            if (coverage.isNotEmpty()) {
                item {
                    CheckCoverageCard(coverage = coverage)
                }
            }

            item {
                SeverityFilterRow(
                    selected = severityFilter,
                    counts = violations.groupingBy { it.type.severity }.eachCount(),
                    onSelect = { severityFilter = it },
                )
            }

            if (screenOverview.isNotEmpty()) {
                item(key = "screen_overview") {
                    ScreenOverviewCard(entries = screenOverview)
                }
            }

            sectionGroups.forEach { (section, sectionViolations) ->
                val talkBack = talkBackBySection[section].orEmpty()
                val screenChecks = ReportHelper.checksForScreen(checkSummaries, section.screenTitle)
                val key = sectionKey(section)
                val expanded = key in expandedSections

                item(key = "header_$key") {
                    SectionHeaderCard(
                        section = section,
                        violationCount = sectionViolations.size,
                        talkBackCount = talkBack.size,
                        passedCount = screenChecks.sumOf { it.passedCount },
                        expanded = expanded,
                        onToggle = { toggleSection(section) },
                    )
                }

                if (expanded && screenChecks.isNotEmpty()) {
                    item(key = "passed_$key") {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(AccessScopeMotion.fadeInTween) + expandVertically(),
                        ) {
                            PassedChecksCard(summaries = screenChecks)
                        }
                    }
                }

                if (expanded) {
                    ReportHelper.SEVERITY_ORDER.forEach { severity ->
                        val severityItems = sectionViolations.filter { it.type.severity == severity }
                        if (severityItems.isEmpty()) return@forEach

                        item(key = "severity_${key}_$severity") {
                            SeverityGroupHeader(severity = severity, count = severityItems.size)
                        }
                        severityItems.forEachIndexed { index, violation ->
                            item(key = "violation_${key}_${violation.dedupeKey}_$index") {
                                ViolationCard(violation, packageLabels)
                            }
                        }
                    }

                    if (talkBack.isNotEmpty()) {
                        item(key = "talkback_header_$key") {
                            Text(
                                "Simulazione TalkBack",
                                fontWeight = FontWeight.SemiBold,
                                color = BrandPrimary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            )
                        }
                        talkBack.take(50).forEachIndexed { index, finding ->
                            item(key = "talkback_${key}_$index") {
                                TalkBackFindingCard(finding, packageLabels)
                            }
                        }
                    }
                }
            }

            item {
                GlossaryCard()
            }
        }
    }
}

/**
 * Card di riepilogo con punteggio, gradiente brand e metriche della sessione.
 *
 * @param score Punteggio stimato di accessibilità (0–100).
 * @param scannedScreens Numero di schermate uniche analizzate.
 * @param scanAnalyses Numero totale di analisi eseguite (può superare le schermate uniche).
 * @param scanScopeLabel Etichetta leggibile degli ambiti analizzati.
 * @param appCount Numero di app monitorate nella sessione.
 * @param violationCount Numero totale di violazioni rilevate.
 * @param talkBackCount Numero di note dalla simulazione TalkBack.
 * @param passedCheckCount Numero di controlli superati con successo.
 */
@Composable
private fun ReportSummaryCard(
    score: Int,
    scannedScreens: Int,
    scanAnalyses: Int,
    scanScopeLabel: String,
    appCount: Int,
    violationCount: Int,
    talkBackCount: Int,
    passedCheckCount: Int = 0,
) {
    val date = remember {
        SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ITALY).format(Date())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(BrandDark, BrandPrimary, BrandSecondary)),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AccessScope", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Punteggio stimato: $score/100",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    ReportHelper.scoreLabel(score),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRow("Data scansione", date)
            SummaryRow("App controllate", "$appCount")
            SummaryRow("Ambiti analizzati", scanScopeLabel)
            SummaryRow("Schermate uniche", "$scannedScreens")
            if (scanAnalyses > scannedScreens) {
                SummaryRow("Analisi eseguite", "$scanAnalyses")
            }
            SummaryRow("Problemi trovati", "$violationCount")
            if (passedCheckCount > 0) {
                SummaryRow("Controlli superati", "$passedCheckCount")
            }
            SummaryRow("Note screen reader", "$talkBackCount")
        }
    }
}

/**
 * Riga etichetta-valore nel riepilogo del report.
 *
 * @param label Etichetta descrittiva della metrica.
 * @param value Valore formattato da mostrare a destra.
 */
@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = contentSecondary(), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/**
 * Fila di chip per filtrare le violazioni per livello di gravità.
 *
 * @param selected Gravità attualmente selezionata; null mostra tutte le violazioni.
 * @param counts Mappa gravità → conteggio violazioni per popolare le etichette dei chip.
 * @param onSelect Callback invocato al cambio filtro; passa null per "Tutti".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeverityFilterRow(
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

/**
 * Card con panoramica delle schermate e conteggio problemi/OK per ciascuna.
 *
 * @param entries Elenco delle voci di panoramica prodotte da [ReportHelper.screenOverview].
 */
@Composable
private fun ScreenOverviewCard(entries: List<ReportHelper.ScreenOverviewEntry>) {
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

/**
 * Intestazione espandibile di una sezione del report (schermata o sotto-sezione).
 *
 * @param section Gruppo di sezione con titolo schermata e eventuale sotto-sezione.
 * @param violationCount Numero di violazioni in questa sezione.
 * @param talkBackCount Numero di note TalkBack in questa sezione.
 * @param passedCount Numero di controlli superati in questa sezione.
 * @param expanded True se il contenuto della sezione è espanso.
 * @param onToggle Callback per espandere o comprimere la sezione.
 */
@Composable
private fun SectionHeaderCard(
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

/**
 * Intestazione colorata per un gruppo di violazioni della stessa gravità.
 *
 * @param severity Livello di gravità del gruppo.
 * @param count Numero di violazioni nel gruppo.
 */
@Composable
private fun SeverityGroupHeader(severity: ViolationSeverity, count: Int) {
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

/**
 * Card di dettaglio per una singola violazione di accessibilità.
 *
 * @param violation Oggetto violazione con tipo, spiegazione e metadati WCAG.
 * @param packageLabels Mappa package name → etichetta leggibile dell'app.
 */
@Composable
private fun ViolationCard(
    violation: AccessibilityViolation,
    packageLabels: Map<String, String>,
) {
    val type = violation.type
    val appLabel = packageLabels[violation.packageName] ?: violation.packageName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(severityColor(type.severity).copy(alpha = 0.10f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(type.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${(violation.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentSecondary(),
                )
            }
            Text(type.wcagRef, style = MaterialTheme.typography.labelSmall, color = BrandPrimary)
            Text(violation.simpleExplanation, style = MaterialTheme.typography.bodySmall)
            ReportHelper.violationDetailLines(violation).forEach { line ->
                Text(line, style = MaterialTheme.typography.labelSmall, color = contentSecondary())
            }
            Text("App: $appLabel", style = MaterialTheme.typography.labelSmall, color = contentSecondary())
    }
}

/**
 * Card per un singolo risultato della simulazione TalkBack.
 *
 * @param finding Rilevamento screen reader con problema e testo annunciato.
 * @param packageLabels Mappa package name → etichetta leggibile dell'app.
 */
@Composable
private fun TalkBackFindingCard(
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

/**
 * Card che mostra la copertura dei controlli per ambito (OK vs problemi).
 *
 * @param coverage Coppie ambito → (controlli superati, controlli falliti).
 */
@Composable
private fun CheckCoverageCard(
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
                    Text("${area.emoji} ${area.title}", style = MaterialTheme.typography.bodyMedium)
                    Text("OK $passed · Problemi $failed", color = successOnContainer(), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Card con elenco dei controlli superati per ambito, con campioni di esempio.
 *
 * @param summaries Riepiloghi dei controlli superati raggruppati per area.
 */
@Composable
private fun PassedChecksCard(summaries: List<CheckAreaSummary>) {
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
                    style = MaterialTheme.typography.bodySmall,
                )
                summary.samples.take(3).forEach { sample ->
                    Text(
                        ReportHelper.passedCheckLine(sample),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentSecondary(),
                    )
                }
            }
        }
    }
}

/** Card espandibile con glossario dei termini di accessibilità usati nel report. */
@Composable
private fun GlossaryCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val terms = listOf(
        "Colori nel report" to "🔴 critico · 🟠 grave · 🟡 medio · ⚪ lieve. Il verde è solo per le metriche OK in dashboard.",
        "TalkBack" to "Lettore vocale di Android: legge ad alta voce cosa tocchi.",
        "WCAG" to "Linee guida internazionali per rendere siti e app usabili da tutti.",
        "Contrasto" to "Differenza tra colore testo e sfondo: più alto = più leggibile.",
        "Target di tocco" to "Area premibile: deve essere abbastanza grande (circa 48×48 px).",
        "contentDescription" to "Testo che TalkBack legge al posto di un'icona o immagine.",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = CardShape,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Glossario rapido", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(AccessScopeMotion.fadeInTween),
                exit = fadeOut(AccessScopeMotion.screenExitTween),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    terms.forEach { (term, definition) ->
                        Text(term, fontWeight = FontWeight.Medium, color = BrandPrimary)
                        Text(definition, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
