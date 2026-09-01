/**
 * Schermata Maestro (Beta): registrazione azioni e gestione YAML.
 */
package dev.accessscope.scanner.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.R
import dev.accessscope.scanner.recorder.MaestroImportResult
import dev.accessscope.scanner.recorder.MaestroYamlImporter
import dev.accessscope.scanner.recorder.PlayReportFormatter
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.PlayExecutionReport
import dev.accessscope.scanner.recorder.SavedFlow
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.components.AppSearchField
import dev.accessscope.scanner.ui.maestro.YamlDownloadHelper
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestione registrazioni Maestro (feature Beta).
 *
 * @param viewModel ViewModel condiviso (elenco app per picker).
 * @param onOpenDrawer Apre il navigation drawer Maestro.
 * @param onEditFlow Naviga all’editor step.
 * @param importYamlRequest Token incrementale dalla MainActivity (drawer Importa).
 * @param createYamlRequest Token incrementale dalla MainActivity (drawer Nuovo).
 */
@Composable
fun FlowsScreen(
    viewModel: ScanViewModel,
    onOpenDrawer: () -> Unit = {},
    onEditFlow: (flowId: String) -> Unit = {},
    importYamlRequest: Int = 0,
    createYamlRequest: Int = 0,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AccessScopeApp
    val recording by app.recordingController.state.collectAsStateWithLifecycle()
    val playback by app.playbackController.state.collectAsStateWithLifecycle()
    val appListState by viewModel.appListUiState.collectAsStateWithLifecycle()
    // Niente I/O in composizione: la lista si carica su IO al primo frame e a ogni refresh.
    var flows by remember { mutableStateOf<List<SavedFlow>>(emptyList()) }
    val ioScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createPkg by remember { mutableStateOf<String?>(null) }
    var createLabel by remember { mutableStateOf<String?>(null) }
    var previewFlow by remember { mutableStateOf<SavedFlow?>(null) }
    var previewYaml by remember { mutableStateOf<String?>(null) }
    var playCleanFlow by remember { mutableStateOf<SavedFlow?>(null) }
    var vaultPromptFlow by remember { mutableStateOf<SavedFlow?>(null) }
    var vaultPromptClear by remember { mutableStateOf(false) }
    var vaultPin by remember { mutableStateOf("") }
    var vaultPassword by remember { mutableStateOf("") }
    var renameFlow by remember { mutableStateOf<SavedFlow?>(null) }
    var renameName by remember { mutableStateOf("") }
    var reportFlow by remember { mutableStateOf<SavedFlow?>(null) }
    var reportText by remember { mutableStateOf<String?>(null) }
    var reportHistory by remember { mutableStateOf<List<PlayExecutionReport>>(emptyList()) }
    var showHowItWorks by remember { mutableStateOf(false) }

    fun refresh() {
        ioScope.launch {
            flows = withContext(Dispatchers.IO) { app.flowStore.listFlows() }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun startPlay(flow: SavedFlow, clearState: Boolean) {
        if (app.needsMaestroCredentials(flow)) {
            vaultPromptFlow = flow
            vaultPromptClear = clearState
            vaultPin = ""
            vaultPassword = ""
            return
        }
        val err = app.startFlowPlayback(flow, clearState = clearState)
        if (err != null) {
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        } else if (clearState) {
            Toast.makeText(
                context,
                "Play pulito: stopApp + cold launch (clear dati se consentito)",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val yaml = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("File vuoto")
            when (val result = MaestroYamlImporter.parse(yaml)) {
                is MaestroImportResult.Failure -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is MaestroImportResult.Success -> {
                    val flow = app.flowStore.saveFlow(
                        name = result.name,
                        appId = result.appId,
                        appLabel = result.appId,
                        actions = result.actions,
                        optimize = false,
                    )
                    refresh()
                    Toast.makeText(
                        context,
                        "Importato: ${flow.name} (${flow.stepCount} step)",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.onFailure {
            Toast.makeText(context, "Import fallito: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(importYamlRequest) {
        if (importYamlRequest > 0) {
            importLauncher.launch(arrayOf("text/*", "application/yaml", "application/x-yaml", "*/*"))
        }
    }

    LaunchedEffect(createYamlRequest) {
        if (createYamlRequest > 0) {
            createName = ""
            createPkg = null
            createLabel = null
            showCreateDialog = true
        }
    }

    LaunchedEffect(recording.isRecording, recording.statusMessage) {
        if (!recording.isRecording && recording.statusMessage != null) {
            refresh()
            recording.statusMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
            app.recordingController.clearStatus()
        }
    }

    LaunchedEffect(playback.statusMessage) {
        playback.statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            app.playbackController.clearStatus()
            refresh()
        }
    }

    Scaffold(
        topBar = {
            AccessScopeTopBar(
                title = "Maestro",
                onMenuClick = onOpenDrawer,
                actions = {
                    BetaChip()
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "banner") {
                AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "BETA — Anteprima",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Registra un flusso, poi riproducilo, modificalo o esportalo dal menu ⋮ di ogni card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentSecondary(),
                    )
                    TextButton(
                        onClick = { showHowItWorks = true },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("Come funziona") }
                }
            }

            item(key = "cta") {
                val realSteps = recording.actions.count { it !is RecordedAction.LaunchApp }
                if (recording.isRecording) {
                    Button(
                        onClick = {
                            val saved = app.stopRecordingSession(save = true)
                            val finalActions = app.recordingController.state.value.actions
                            val real = app.recordingController.realStepCount(finalActions)
                            val status = app.recordingController.state.value.statusMessage
                            refresh()
                            val msg = when {
                                real == 0 -> status
                                    ?: "Nessun tap/testo catturato. Disattiva e riattiva AccessScope in Accessibilità, poi riprova."
                                saved != null -> "YAML salvato: ${saved.stepCount} step"
                                else -> status ?: "Registrazione terminata"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Outlined.FiberManualRecord, contentDescription = null)
                        Text("  Stop e salva ($realSteps step)")
                    }
                } else {
                    Button(
                        onClick = { showPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = PillShape,
                        enabled = !playback.isPlaying,
                    ) {
                        Icon(Icons.Outlined.FiberManualRecord, contentDescription = null)
                        Text("  Registra con Maestro")
                    }
                }
            }

            item(key = "list_header") {
                Text(
                    "Registrazioni (${flows.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (flows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nessuna registrazione. Tocca «Registra con Maestro» oppure apri ☰ " +
                            "per importare o creare un YAML.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentSecondary(),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            items(flows, key = { it.id }) { flow ->
                FlowListCard(
                    flow = flow,
                    playbackBusy = playback.isPlaying,
                    onPlay = { startPlay(flow, clearState = false) },
                    onPlayClean = { playCleanFlow = flow },
                    onEdit = { onEditFlow(flow.id) },
                    onScanWithFlow = {
                        viewModel.clearSelection()
                        viewModel.toggleApp(flow.appId)
                        val err = app.startScanWithFlow(flow)
                        if (err != null) {
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Scan+Flusso avviato su ${flow.appLabel}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onPreview = {
                        previewFlow = flow
                        ioScope.launch {
                            previewYaml = withContext(Dispatchers.IO) { app.flowStore.readYaml(flow) }
                        }
                    },
                    onShare = {
                        val file = app.flowStore.yamlFile(flow)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/yaml"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, flow.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Condividi YAML Maestro"))
                    },
                    onDownload = {
                        val result = YamlDownloadHelper.downloadOrShare(context, app.flowStore, flow)
                        when {
                            result == null -> Toast.makeText(context, "YAML non disponibile", Toast.LENGTH_SHORT).show()
                            result.usedShareFallback -> Unit
                            else -> Toast.makeText(
                                context,
                                "Scaricato: ${result.displayPath}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onDelete = {
                        ioScope.launch {
                            withContext(Dispatchers.IO) { app.flowStore.deleteFlow(flow.id) }
                            refresh()
                        }
                    },
                    onRename = {
                        renameFlow = flow
                        renameName = flow.name
                    },
                    onReport = {
                        reportFlow = flow
                        reportText = null
                        ioScope.launch {
                            val reports = withContext(Dispatchers.IO) { app.listPlayReports(flow.id) }
                            reportHistory = reports
                            reportText = reports.firstOrNull()?.let { PlayReportFormatter.formatFull(it) }
                                ?: "Nessun test eseguito per questo flusso.\nAvvia Play o Valida dall'editor per generare un report."
                        }
                    },
                )
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            title = "Scegli app da registrare",
            query = query,
            onQueryChange = { query = it },
            apps = appListState.apps.filter {
                query.isBlank() ||
                    it.label.contains(query, true) ||
                    it.packageName.contains(query, true)
            }.take(40),
            onDismiss = { showPicker = false },
            onSelect = { pkg, label ->
                showPicker = false
                val err = app.startRecordingSession(pkg, label)
                if (err != null) {
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        "Registrazione avviata — usa STOP REC. Se i tap non compaiono, riattiva Accessibilità.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nuovo flusso YAML") },
            text = {
                Column {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        createLabel?.let { "App: $it" } ?: "Seleziona un’app target",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentSecondary(),
                    )
                    Spacer(Modifier.height(8.dp))
                    AppSearchField(query = query, onQueryChange = { query = it })
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            appListState.apps.filter {
                                query.isBlank() ||
                                    it.label.contains(query, true) ||
                                    it.packageName.contains(query, true)
                            }.take(30),
                            key = { it.packageName },
                        ) { installed ->
                            OutlinedButton(
                                onClick = {
                                    createPkg = installed.packageName
                                    createLabel = installed.label
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = CardShape,
                            ) {
                                Text(
                                    "${installed.label}\n${installed.packageName}",
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pkg = createPkg
                        val label = createLabel
                        if (pkg == null || label == null) {
                            Toast.makeText(context, "Seleziona un’app", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val name = createName.ifBlank { "Flusso $label" }
                        val actions = listOf(
                            RecordedAction.LaunchApp(pkg),
                            RecordedAction.WaitForAnimation(pkg),
                        )
                        val flow = app.flowStore.saveFlow(
                            name = name,
                            appId = pkg,
                            appLabel = label,
                            actions = actions,
                            optimize = false,
                        )
                        showCreateDialog = false
                        refresh()
                        onEditFlow(flow.id)
                    },
                ) { Text("Crea") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Annulla") }
            },
        )
    }

    playCleanFlow?.let { flow ->
        AlertDialog(
            onDismissRequest = { playCleanFlow = null },
            title = { Text("Play flusso") },
            text = {
                Text(
                    "«${flow.name}» — avvio normale o pulito (stopApp + cold launch, " +
                        "tentativo clearState per forzare il login).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playCleanFlow = null
                        startPlay(flow, clearState = true)
                    },
                ) { Text("Avvia pulito") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            playCleanFlow = null
                            startPlay(flow, clearState = false)
                        },
                    ) { Text("Avvia normale") }
                    TextButton(onClick = { playCleanFlow = null }) { Text("Annulla") }
                }
            },
        )
    }

    vaultPromptFlow?.let { flow ->
        AlertDialog(
            onDismissRequest = { vaultPromptFlow = null },
            title = { Text("Credenziali per Play") },
            text = {
                Column {
                    Text(
                        "Il flusso usa PIN/password. Salvali una volta (solo su questo device).",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentSecondary(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vaultPin,
                        onValueChange = { vaultPin = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vaultPassword,
                        onValueChange = { vaultPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        app.saveMaestroCredential(flow.appId, vaultPin, vaultPassword)
                        val clear = vaultPromptClear
                        vaultPromptFlow = null
                        startPlay(flow, clearState = clear)
                    },
                ) { Text("Salva e Play") }
            },
            dismissButton = {
                Row {
                    // Play senza credenziali: sicuro solo da stasera — FlowPlayer non salta più
                    // il SET_TEXT in silenzio su ${PIN}/${PASSWORD} non risolti, lo annota come
                    // nota CI nel messaggio finale ("· N note CI"). Prima di questo fix l'unica
                    // via era "Salva e Play" — un blocco vero, senza alternativa.
                    TextButton(
                        onClick = {
                            val clear = vaultPromptClear
                            vaultPromptFlow = null
                            app.startFlowPlayback(flow, clearState = clear)?.let { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        },
                    ) { Text("Play senza credenziali") }
                    TextButton(onClick = { vaultPromptFlow = null }) { Text("Annulla") }
                }
            },
        )
    }

    if (showHowItWorks) {
        AlertDialog(
            onDismissRequest = { showHowItWorks = false },
            title = { Text("Come funziona Maestro") },
            text = {
                Text(
                    "Registra passaggi, importa/crea YAML dal menu ☰, riproduci con Play, " +
                        "modifica gli step e avvia Scan+Flusso. " +
                        "Per timeout e selettori più affidabili, esegui prima una scan WCAG sull’app. " +
                        "Step opzionali (popup) sono esportati con optional: true nel YAML. " +
                        "Dopo un update APK: disattiva e riattiva AccessScope in Accessibilità.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showHowItWorks = false }) { Text("Chiudi") }
            },
        )
    }

    previewFlow?.let { flow ->
        AlertDialog(
            onDismissRequest = { previewFlow = null },
            title = { Text(flow.name) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        previewYaml.orEmpty(),
                        style = CodeTextStyle,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewFlow = null }) { Text("Chiudi") }
            },
        )
    }

    renameFlow?.let { flow ->
        AlertDialog(
            onDismissRequest = { renameFlow = null },
            title = { Text("Rinomina flusso") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ioScope.launch {
                            val updated = withContext(Dispatchers.IO) {
                                app.renameFlow(flow.id, renameName)
                            }
                            renameFlow = null
                            if (updated != null) {
                                refresh()
                                Toast.makeText(context, "Rinominato: ${updated.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Rinomina fallita", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = renameName.trim().isNotBlank(),
                ) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { renameFlow = null }) { Text("Annulla") }
            },
        )
    }

    reportFlow?.let { flow ->
        AlertDialog(
            onDismissRequest = { reportFlow = null },
            title = { Text("Report test — ${flow.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (reportHistory.size > 1) {
                        Text(
                            "Run recenti: ${reportHistory.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentSecondary(),
                        )
                        Spacer(Modifier.height(4.dp))
                        reportHistory.take(5).forEachIndexed { idx, r ->
                            val label = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)
                                .format(Date(r.finishedAtMs))
                            TextButton(
                                onClick = {
                                    reportText = PlayReportFormatter.formatFull(r)
                                },
                            ) {
                                Text(
                                    "${if (r.success) "OK" else "KO"} · $label · " +
                                        "${r.passedSteps}/${r.totalSteps} step",
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        reportText ?: "Caricamento…",
                        style = CodeTextStyle,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = reportText ?: return@TextButton
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Report Maestro — ${flow.name}")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(share, "Condividi report"))
                    },
                    enabled = reportText != null && reportHistory.isNotEmpty(),
                ) { Text("Condividi") }
            },
            dismissButton = {
                TextButton(onClick = { reportFlow = null }) { Text("Chiudi") }
            },
        )
    }
}

@Composable
private fun BetaChip() {
    Text(
        "BETA",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun FlowListCard(
    flow: SavedFlow,
    playbackBusy: Boolean,
    onPlay: () -> Unit,
    onPlayClean: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onReport: () -> Unit,
    onScanWithFlow: () -> Unit,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = remember(flow.createdAtMs) {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.ITALY).format(Date(flow.createdAtMs))
    }
    val lastPlayLabel = remember(flow.lastPlayAtMs, flow.lastPlaySuccess) {
        flow.lastPlayAtMs?.let { ts ->
            val whenStr = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY).format(Date(ts))
            val steps = "${flow.lastPlayPassedSteps ?: "?"}/${flow.lastPlayTotalSteps ?: flow.stepCount}"
            val ok = flow.lastPlaySuccess == true
            Triple(whenStr, steps, ok)
        }
    }
    val actionsEnabled = flow.hasActionsJson && !playbackBusy
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
        Text(flow.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(flow.appLabel, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        Text(
            "$date · ${flow.stepCount} step · ${flow.appId}",
            style = CodeTextStyle,
            color = contentSecondary(),
        )
        lastPlayLabel?.let { (whenStr, steps, ok) ->
            Text(
                "Ultimo test: ${if (ok) "OK" else "KO"} ($steps step) · $whenStr",
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (!flow.hasActionsJson) {
            Text(
                "Solo YAML — Play/Edit non disponibili (ri-registra o re-importa)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlay, enabled = actionsEnabled) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play flusso",
                    tint = if (actionsEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Altre azioni")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifica step") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        enabled = actionsEnabled,
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rinomina flusso") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Report test") },
                        leadingIcon = { Icon(Icons.Outlined.Assessment, contentDescription = null) },
                        onClick = { menuExpanded = false; onReport() },
                    )
                    DropdownMenuItem(
                        text = { Text("Scan+Flusso") },
                        leadingIcon = {
                            Image(
                                painter = painterResource(R.drawable.ic_access_scope_logo),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        enabled = actionsEnabled,
                        onClick = { menuExpanded = false; onScanWithFlow() },
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Play pulito")
                                Text(
                                    "Stop app + avvio a freddo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentSecondary(),
                                )
                            }
                        },
                        enabled = actionsEnabled,
                        onClick = { menuExpanded = false; onPlayClean() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Anteprima YAML") },
                        onClick = { menuExpanded = false; onPreview() },
                    )
                    DropdownMenuItem(
                        text = { Text("Scarica YAML") },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        onClick = { menuExpanded = false; onDownload() },
                    )
                    DropdownMenuItem(
                        text = { Text("Condividi") },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = { menuExpanded = false; onShare() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Elimina", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { menuExpanded = false; showDeleteConfirm = true },
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare «${flow.name}»?") },
            text = { Text("Il flusso registrato e il suo YAML verranno eliminati. L'azione non è reversibile.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    apps: List<dev.accessscope.scanner.data.InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSelect: (packageName: String, label: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppSearchField(query = query, onQueryChange = onQueryChange)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        OutlinedButton(
                            onClick = { onSelect(app.packageName, app.label) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CardShape,
                        ) {
                            Text(
                                "${app.label}\n${app.packageName}",
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
