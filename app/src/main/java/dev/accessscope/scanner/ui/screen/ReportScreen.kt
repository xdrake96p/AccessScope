package dev.accessscope.scanner.ui.screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.report.ReportSectionGroup
import dev.accessscope.scanner.ui.theme.BrandDark
import dev.accessscope.scanner.ui.theme.BrandLight
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.BrandSecondary
import dev.accessscope.scanner.ui.theme.TextSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val screenTotals = remember(violations) { ReportHelper.screenTotals(violations) }
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

    fun sectionKey(section: ReportSectionGroup) = "${section.screenTitle}|${section.sectionTitle}"
    fun toggleSection(section: ReportSectionGroup) {
        val key = sectionKey(section)
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }

    Scaffold(
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
        if (violations.isEmpty() && scan.screenReaderFindings.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Nessun problema rilevato nella sessione.", color = TextSecondary)
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
                )
            }

            item {
                SeverityFilterRow(
                    selected = severityFilter,
                    counts = violations.groupingBy { it.type.severity }.eachCount(),
                    onSelect = { severityFilter = it },
                )
            }

            if (screenTotals.isNotEmpty()) {
                item {
                    ScreenOverviewCard(screenTotals = screenTotals)
                }
            }

            sectionGroups.forEach { (section, sectionViolations) ->
                val talkBack = talkBackBySection[section].orEmpty()
                val key = sectionKey(section)
                val expanded = key in expandedSections

                item(key = "header_$key") {
                    SectionHeaderCard(
                        section = section,
                        violationCount = sectionViolations.size,
                        talkBackCount = talkBack.size,
                        expanded = expanded,
                        onToggle = { toggleSection(section) },
                    )
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

@Composable
private fun ReportSummaryCard(
    score: Int,
    scannedScreens: Int,
    scanAnalyses: Int,
    scanScopeLabel: String,
    appCount: Int,
    violationCount: Int,
    talkBackCount: Int,
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
            SummaryRow("Note screen reader", "$talkBackCount")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

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

@Composable
private fun ScreenOverviewCard(screenTotals: Map<String, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandLight),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Schermate con problemi", fontWeight = FontWeight.SemiBold)
            screenTotals.toSortedMap().forEach { (screen, total) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(screen, style = MaterialTheme.typography.bodyMedium)
                    Text("$total", fontWeight = FontWeight.Bold, color = BrandPrimary)
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderCard(
    section: ReportSectionGroup,
    violationCount: Int,
    talkBackCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
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
                    "$violationCount problemi · $talkBackCount note TalkBack",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Comprimi" else "Espandi",
            )
        }
    }
}

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
                    color = TextSecondary,
                )
            }
            Text(type.wcagRef, style = MaterialTheme.typography.labelSmall, color = BrandPrimary)
            Text(violation.simpleExplanation, style = MaterialTheme.typography.bodySmall)
            Text("Dettaglio: ${violation.details}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("App: $appLabel", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            val meta = buildList {
                add(violation.viewClassName.substringAfterLast('.'))
                violation.viewId?.let { add(it.substringAfterLast('/')) }
                violation.bounds?.let { add(it) }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
    }
}

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
            Text("Annuncio: \"$it\"", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(
            "${finding.screenTitle} · $appLabel",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

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
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Glossario rapido", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    terms.forEach { (term, definition) ->
                        Text(term, fontWeight = FontWeight.Medium, color = BrandPrimary)
                        Text(definition, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
