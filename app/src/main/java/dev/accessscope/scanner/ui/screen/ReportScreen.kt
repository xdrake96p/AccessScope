/**
 * Schermata del report di accessibilità con riepilogo, filtri e dettaglio violazioni.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.AiPromptInput
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.components.CopyAiPromptButton
import dev.accessscope.scanner.ui.components.SessionComparisonCard
import dev.accessscope.scanner.ui.screen.report.CheckCoverageCard
import dev.accessscope.scanner.ui.screen.report.GlossaryCard
import dev.accessscope.scanner.ui.screen.report.PassedChecksCard
import dev.accessscope.scanner.ui.screen.report.ReportDonutOverview
import dev.accessscope.scanner.ui.screen.report.ScreenOverviewCard
import dev.accessscope.scanner.ui.screen.report.SectionHeaderCard
import dev.accessscope.scanner.ui.screen.report.SeverityFilterRow
import dev.accessscope.scanner.ui.screen.report.SeverityGroupHeader
import dev.accessscope.scanner.ui.screen.report.TalkBackFindingCard
import dev.accessscope.scanner.ui.screen.report.ViolationCard
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.launch

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
    onOpenViolationDetail: (String) -> Unit = {},
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

    fun sectionKey(section: dev.accessscope.scanner.report.ReportSectionGroup) =
        "${section.screenTitle}|${section.sectionTitle}"
    fun toggleSection(section: dev.accessscope.scanner.report.ReportSectionGroup) {
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
                ReportDonutOverview(
                    score = score,
                    totalChecks = passedTotal + violations.size,
                    passedChecks = passedTotal,
                    violationCount = violations.size,
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
                                ViolationCard(
                                    violation = violation,
                                    packageLabels = packageLabels,
                                    onOpen = { onOpenViolationDetail(violation.dedupeKey) },
                                )
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
