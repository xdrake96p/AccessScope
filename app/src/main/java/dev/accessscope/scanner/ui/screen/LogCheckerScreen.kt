/**
 * Schermata log checker: visualizza i log AccessScope in tempo reale.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import dev.accessscope.scanner.export.DiagnosticLogExporter
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.LogEntry
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCheckerScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val entries by AppFileLogger.liveEntries.collectAsStateWithLifecycle()
    var followTail by remember { mutableStateOf(true) }
    var errorsOnly by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val visible = remember(entries, errorsOnly) {
        if (errorsOnly) entries.filter { it.level == "E" } else entries
    }

    LaunchedEffect(Unit) {
        AppFileLogger.preloadFromDisk()
    }

    LaunchedEffect(visible.size, followTail) {
        if (followTail && visible.isNotEmpty()) {
            listState.animateScrollToItem(visible.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layout.totalItemsCount - 2
        }.distinctUntilChanged().collect { atBottom ->
            if (!atBottom && followTail) followTail = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log checker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { followTail = !followTail }) {
                        Icon(
                            if (followTail) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (followTail) "Pausa auto-scroll" else "Segui in tempo reale",
                        )
                    }
                    IconButton(onClick = { AppFileLogger.clearLiveBuffer() }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Pulisci buffer")
                    }
                    IconButton(
                        onClick = {
                            if (exporting) return@IconButton
                            exporting = true
                            viewModel.exportDiagnosticLogs { result ->
                                exporting = false
                                result.fold(
                                    onSuccess = { path ->
                                        Toast.makeText(context, "Log salvati in $path", Toast.LENGTH_LONG).show()
                                        DiagnosticLogExporter.shareExportedFile(context, path)
                                    },
                                    onFailure = { e ->
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Export fallito",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                )
                            }
                        },
                        enabled = !exporting,
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = "Esporta log")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${visible.size} righe${if (!followTail) " · scroll in pausa" else " · live"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
                FilterChip(
                    selected = errorsOnly,
                    onClick = { errorsOnly = !errorsOnly },
                    label = { Text("Solo errori") },
                )
            }

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nessun log ancora", fontWeight = FontWeight.Medium)
                        Text(
                            "Avvia una scansione o usa l'app: i messaggi compariranno qui in tempo reale.",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary(),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(onClick = { AppFileLogger.preloadFromDisk() }) {
                            Text("Ricarica da file")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visible, key = { "${it.timestampMs}_${it.tag}_${it.message.hashCode()}" }) { entry ->
                        LogLine(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val color = when (entry.level) {
        "E" -> Color(0xFFDC2626)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = entry.formatLine(),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}
