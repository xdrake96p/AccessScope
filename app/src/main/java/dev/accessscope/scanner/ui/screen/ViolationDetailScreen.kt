/**
 * Dettaglio singola violazione con evidenza visiva annotata.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.accessibility.asSectionHeading
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.components.ViolationEvidenceViewer
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ViolationDetailScreen(
    dedupeKey: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    sessionId: String? = null,
) {
    val violation = remember(dedupeKey, sessionId) {
        viewModel.findViolation(dedupeKey, sessionId)
    }
    var imagePath by remember(violation) { mutableStateOf(violation?.evidenceImagePath) }

    LaunchedEffect(violation, sessionId) {
        val v = violation ?: return@LaunchedEffect
        if (imagePath.isNullOrBlank() || !java.io.File(imagePath!!).exists()) {
            imagePath = withContext(Dispatchers.IO) {
                viewModel.resolveEvidencePath(v, sessionId)
            }
        }
    }

    Scaffold(
        topBar = {
            AccessScopeTopBar(
                title = violation?.type?.displayName ?: "Dettaglio problema",
                onBack = onBack,
            )
        },
        modifier = Modifier.navigationBarsPadding(),
    ) { padding ->
        if (violation == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text("Violazione non trovata nella sessione corrente.")
            }
            return@Scaffold
        }

        ViolationDetailContent(
            violation = violation,
            imagePath = imagePath,
            packageLabel = viewModel.packageLabel(violation.packageName),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

@Composable
private fun ViolationDetailContent(
    violation: AccessibilityViolation,
    imagePath: String?,
    packageLabel: String,
    modifier: Modifier = Modifier,
) {
    val type = violation.type
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            type.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = severityColor(type.severity),
            modifier = Modifier.asSectionHeading(),
        )
        Text(type.wcagRef, style = MaterialTheme.typography.labelLarge, color = BrandPrimary)
        Text(
            "Confidenza ${(violation.confidence * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
        )
        Text(type.plainHint, style = MaterialTheme.typography.bodyLarge)

        Text(
            "Evidenza visiva",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.asSectionHeading(),
        )
        ViolationEvidenceViewer(
            violation = violation,
            imagePath = imagePath,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!violation.hasSpatialBounds()) {
            Text(
                "Problema a livello schermata: l'evidenza mostra la schermata completa.",
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary(),
            )
        }

        Spacer(Modifier.height(4.dp))
        Text("Dettagli tecnici", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ReportHelper.violationDetailLines(violation).forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        }
        Text("App: $packageLabel", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        Text("Schermata: ${violation.screenTitle}", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
    }
}
