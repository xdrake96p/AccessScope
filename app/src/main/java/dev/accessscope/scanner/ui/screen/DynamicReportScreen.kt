/**
 * Report dinamico navigabile per schermata, in stile Google Accessibility Scanner.
 */
package dev.accessscope.scanner.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.report.DynamicReportHelper
import dev.accessscope.scanner.report.DynamicScreenFrame
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.components.ScreenFilmstrip
import dev.accessscope.scanner.ui.components.ScreenIssueCanvas
import dev.accessscope.scanner.ui.components.ScreenIssueList
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bitmap schermata tenuti in cache contemporaneamente nel report dinamico. */
private const val MAX_CACHED_THUMBNAILS = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicReportScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenViolationDetail: (String) -> Unit,
    sessionId: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scan = uiState.scanState
    // Sessione archiviata: parse JSON una sola volta per sessionId, su IO (non in composizione).
    // Sessione live: ricostruisci solo quando cambiano i dati che contano, non a ogni tick
    // di scanState (il contatore analisi cambia a ogni evento a11y).
    val frames = if (sessionId != null) {
        produceState(initialValue = emptyList<DynamicScreenFrame>(), sessionId) {
            value = withContext(Dispatchers.IO) { viewModel.buildDynamicReport(sessionId) }
        }.value
    } else {
        remember(scan.visitedScreens, scan.violations, scan.screenReaderFindings, scan.checkSummaries) {
            viewModel.buildDynamicReport(null)
        }
    }
    val reportScore = remember(scan.violations, scan.uniqueScreens) {
        ReportHelper.computeScore(
            ReportHelper.filterViolations(scan.violations, uiState.includeLowConfidenceFindings),
            scan.uniqueScreens,
        )
    }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var severityFilter by rememberSaveable { mutableStateOf<ViolationSeverity?>(null) }
    var selectedDedupeKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Keyed su sessionId: cambiando sessione la cache riparte, niente bitmap stantii.
    val thumbnails = remember(sessionId) { mutableStateMapOf<String, Bitmap?>() }
    val thumbnailOrder = remember(sessionId) { ArrayDeque<String>() }
    val screenMaxDimPx = LocalContext.current.resources.displayMetrics.let {
        maxOf(it.widthPixels, it.heightPixels)
    }

    val safeIndex = selectedIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    val currentFrame = frames.getOrNull(safeIndex)
    val filteredViolations = remember(currentFrame, severityFilter) {
        currentFrame?.let { DynamicReportHelper.filterFrameViolations(it, severityFilter) }.orEmpty()
    }

    LaunchedEffect(frames, safeIndex, sessionId) {
        val frame = frames.getOrNull(safeIndex) ?: return@LaunchedEffect
        if (thumbnails.containsKey(frame.fingerprint)) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            viewModel.loadScreenBitmapForFrame(frame, sessionId, maxDimPx = screenMaxDimPx)
        }
        thumbnails[frame.fingerprint] = bitmap
        thumbnailOrder.addLast(frame.fingerprint)
        // Eviction FIFO: senza limite 20+ schermate accumulano centinaia di MB (OOM).
        while (thumbnailOrder.size > MAX_CACHED_THUMBNAILS) {
            thumbnails.remove(thumbnailOrder.removeFirst())
        }
    }

    LaunchedEffect(frames.size) {
        if (selectedIndex >= frames.size) {
            selectedIndex = (frames.size - 1).coerceAtLeast(0)
        }
    }

    Scaffold(
        topBar = {
            AccessScopeTopBar(
                title = "Report dinamico",
                onBack = onBack,
            )
        },
        modifier = Modifier.navigationBarsPadding(),
    ) { padding ->
        if (frames.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text(
                    "Nessuna schermata nel report",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (scan.isScanning) {
                        "Naviga nell'app analizzata per popolare il report dinamico."
                    } else {
                        "Avvia una scansione e visita le schermate dell'app target."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentSecondary(),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DynamicReportScoreCard(
                score = reportScore,
                violationCount = ReportHelper.filterViolations(
                    scan.violations,
                    uiState.includeLowConfidenceFindings,
                ).size,
                screensCount = frames.size,
            )

            ScreenFilmstrip(
                frames = frames,
                selectedIndex = safeIndex,
                thumbnails = thumbnails,
                onSelect = { index ->
                    selectedIndex = index
                    selectedDedupeKey = null
                },
            )

            SeverityFilterRow(
                violations = currentFrame?.violations.orEmpty(),
                talkBackCount = currentFrame?.talkBackFindings?.size ?: 0,
                selected = severityFilter,
                onSelect = { severityFilter = it },
            )

            currentFrame?.let { frame ->
                val bitmap = thumbnails[frame.fingerprint]
                ScreenIssueCanvas(
                    bitmap = bitmap,
                    violations = filteredViolations,
                    selectedDedupeKey = selectedDedupeKey,
                    onViolationClick = { violation ->
                        selectedDedupeKey = violation.dedupeKey
                    },
                    protectionReason = frame.protectionReason,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                ScreenIssueList(
                    frame = frame,
                    violations = filteredViolations,
                    selectedDedupeKey = selectedDedupeKey,
                    onViolationClick = { violation, _ ->
                        selectedDedupeKey = violation.dedupeKey
                    },
                    onOpenViolationDetail = { violation ->
                        onOpenViolationDetail(violation.dedupeKey)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Card riepilogo punteggio del report dinamico (tint secondary, stile mockup).
 */
@Composable
private fun DynamicReportScoreCard(
    score: Int,
    violationCount: Int,
    screensCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "PUNTEGGIO SESSIONE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$score",
                    fontFamily = HankenGroteskFamily,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    "/100",
                    style = MaterialTheme.typography.titleMedium,
                    color = contentSecondary(),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$violationCount PROBLEMI",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$screensCount SCHERMATE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeverityFilterRow(
    violations: List<AccessibilityViolation>,
    talkBackCount: Int = 0,
    selected: ViolationSeverity?,
    onSelect: (ViolationSeverity?) -> Unit,
) {
    val counts = violations.groupingBy { it.type.severity }.eachCount()
    if (violations.isEmpty() && talkBackCount == 0) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Tutti (${violations.size})") },
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
