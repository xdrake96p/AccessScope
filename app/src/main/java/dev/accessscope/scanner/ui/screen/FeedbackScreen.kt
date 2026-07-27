/**
 * Schermata per inviare suggerimenti e segnalazioni tramite GitHub Issues.
 */
package dev.accessscope.scanner.ui.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.FeedbackIssueBuilder
import dev.accessscope.scanner.util.FeedbackIssueLauncher
import kotlinx.coroutines.launch

/**
 * Form per suggerimenti e segnalazioni con apertura GitHub Issues precompilato.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var feedbackType by remember { mutableStateOf(FeedbackIssueBuilder.FeedbackType.BUG) }
    var description by remember { mutableStateOf("") }
    var includeScan by remember { mutableStateOf(true) }
    var includeDevice by remember { mutableStateOf(true) }

    val appVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "unknown"
        }.getOrDefault("unknown")
    }

    Scaffold(
        topBar = {
            AccessScopeTopBar(title = "Suggerimenti e segnalazioni", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Feedback, contentDescription = null, tint = BrandPrimary)
                Text(
                    "Invia feedback su GitHub",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Descrivi il problema o il suggerimento. Si aprirà il browser con una issue " +
                        "precompilata e verrà proposto l'allegato del report affidabilità `.md` " +
                        "(serve un account GitHub).",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }

            Text(
                "TIPO DI FEEDBACK",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
            // Segmented control stile mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CompactShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FeedbackIssueBuilder.FeedbackType.entries.forEach { type ->
                    val selected = feedbackType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CompactShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent,
                            )
                            .clickable { feedbackType = type }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            type.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else contentSecondary(),
                            maxLines = 1,
                        )
                    }
                }
            }

            Text(
                "DESCRIZIONE DETTAGLIATA",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = contentSecondary(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                shape = CompactShape,
                placeholder = {
                    Text("Descrivi i passaggi per riprodurre il problema o i dettagli del tuo suggerimento…")
                },
            )

            AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Includi ultima scansione")
                        Text(
                            "Package, sessionId, punteggio e fino a 3 violazioni esempio (no dati sensibili).",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                        )
                    }
                    Switch(checked = includeScan, onCheckedChange = { includeScan = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Includi info dispositivo")
                        Text(
                            "Modello, API Android e versione AccessScope.",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                        )
                    }
                    Switch(checked = includeDevice, onCheckedChange = { includeDevice = it })
                }
            }

            Button(
                onClick = {
                    if (description.isBlank()) {
                        Toast.makeText(context, "Inserisci una descrizione", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val scanState = uiState.scanState
                    val lastArchived = scanState.selectedPackages
                        .firstNotNullOfOrNull { pkg -> viewModel.getScanHistory(pkg).lastOrNull() }
                    val violations = scanState.violations.takeIf { it.isNotEmpty() }
                        ?: lastArchived?.violations.orEmpty()
                    val packages = scanState.selectedPackages.takeIf { it.isNotEmpty() }
                        ?: lastArchived?.targetPackages.orEmpty()
                    val sessionId = scanState.sessionId ?: lastArchived?.id
                    val score = if (scanState.violations.isNotEmpty()) {
                        ReportHelper.computeScore(
                            scanState.violations,
                            scanState.uniqueScreens.coerceAtLeast(1),
                        )
                    } else {
                        lastArchived?.score
                    }
                    val scanContext = if (includeScan && (packages.isNotEmpty() || sessionId != null)) {
                        FeedbackIssueBuilder.formatScanContext(
                            packages = packages,
                            sessionId = sessionId,
                            score = score,
                            sampleViolations = violations.take(3).map { violation ->
                                "${violation.type.name}: ${violation.details.take(120)}"
                            },
                        )
                    } else {
                        null
                    }
                    val deviceInfo = if (includeDevice) {
                        FeedbackIssueBuilder.formatDeviceInfo(
                            model = Build.MODEL,
                            apiLevel = Build.VERSION.SDK_INT,
                            appVersion = appVersion,
                        )
                    } else {
                        null
                    }
                    scope.launch {
                        viewModel.resolveReliabilityMdForFeedback { mdPath ->
                            val url = FeedbackIssueBuilder.buildUrl(
                                type = feedbackType,
                                description = description,
                                scanContext = scanContext,
                                deviceInfo = deviceInfo,
                                reliabilityMdFileName = mdPath?.substringAfterLast('/'),
                            )
                            FeedbackIssueLauncher.launch(
                                context = context,
                                issueUrl = url,
                                reliabilityMdPath = mdPath,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = PillShape,
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text(
                    "INVIA SU GITHUB",
                    modifier = Modifier.padding(start = 8.dp),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
