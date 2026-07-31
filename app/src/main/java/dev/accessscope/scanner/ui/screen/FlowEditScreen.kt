/**
 * Editor step di un flusso Maestro (Beta): modifica, riordina, aggiungi azioni.
 *
 * UI: lista + dialog categorizzato [InsertStepDialog]; download via [YamlDownloadHelper].
 * Persistenza: [dev.accessscope.scanner.recorder.FlowStore] (optimize opzionale al save).
 */
package dev.accessscope.scanner.ui.screen

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.AccessScopeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.ScrollDirection
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.recorder.optimization.lint.FlowLintIssue
import dev.accessscope.scanner.recorder.optimization.lint.FlowLinter
import dev.accessscope.scanner.recorder.optimization.lint.LintSeverity
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.maestro.InsertStepDialog
import dev.accessscope.scanner.ui.maestro.YamlDownloadHelper
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Schermata modifica step flusso.
 *
 * @param flowId Id flusso in [dev.accessscope.scanner.recorder.FlowStore].
 * @param onBack Torna alla lista Maestro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowEditScreen(
    flowId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AccessScopeApp
    val scope = rememberCoroutineScope()
    val flow = remember(flowId) { app.flowStore.getFlow(flowId) }
    val actions = remember { mutableStateListOf<RecordedAction>() }
    var flowName by remember { mutableStateOf(flow?.name.orEmpty()) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var vaultPin by remember { mutableStateOf("") }
    var vaultPassword by remember { mutableStateOf("") }
    var showInsertDialog by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(flowId) {
        val loaded = app.flowStore.readActions(flowId)
        if (loaded == null || flow == null) {
            Toast.makeText(
                context,
                "Flusso non modificabile (manca actions.json). Ri-registra o importa YAML.",
                Toast.LENGTH_LONG,
            ).show()
            onBack()
            return@LaunchedEffect
        }
        actions.clear()
        actions.addAll(loaded)
        flowName = flow.name
        dirty = false
        selectedIndex = loaded.indexOfFirst { it !is RecordedAction.LaunchApp }
            .takeIf { it >= 0 }
            ?: loaded.lastIndex.takeIf { it >= 0 }
    }

    val lintReport = remember(actions.toList()) { FlowLinter.lint(actions.toList()) }
    val lintByStep = remember(lintReport) { lintReport.byStep() }

    fun insertAction(action: RecordedAction) {
        val sel = selectedIndex
        if (sel == null) {
            Toast.makeText(
                context,
                "Seleziona prima uno step: i nuovi vanno subito dopo",
                Toast.LENGTH_SHORT,
            ).show()
            showInsertDialog = false
            return
        }
        val insertAt = (sel + 1).coerceIn(1, actions.size)
        actions.add(insertAt, action)
        selectedIndex = insertAt
        dirty = true
        showInsertDialog = false
        Toast.makeText(
            context,
            "Inserito dopo lo step ${sel + 1} (posizione ${insertAt + 1})",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun duplicateAction(index: Int) {
        val src = actions.getOrNull(index) ?: return
        if (src is RecordedAction.LaunchApp) return
        val copy = copyActionWithNewTimestamp(src)
        val insertAt = (index + 1).coerceAtMost(actions.size)
        actions.add(insertAt, copy)
        selectedIndex = insertAt
        dirty = true
        Toast.makeText(context, "Step duplicato", Toast.LENGTH_SHORT).show()
    }

    fun saveFlow(optimize: Boolean, thenDownload: Boolean = false) {
        val updated = app.flowStore.updateFlow(
            id = flowId,
            actions = actions.toList(),
            name = flowName,
            optimize = optimize,
            enforceZeroEdit = optimize,
        )
        if (updated != null) {
            dirty = false
            val zeroMsg = app.flowStore.lastZeroEditReport?.userSummary()
            val base = "Flusso salvato (${updated.stepCount} step)"
            Toast.makeText(
                context,
                if (zeroMsg != null) "$base · $zeroMsg" else base,
                Toast.LENGTH_LONG,
            ).show()
            if (thenDownload) {
                val result = YamlDownloadHelper.downloadOrShare(context, app.flowStore, updated)
                if (result != null) {
                    val msg = if (result.usedShareFallback) {
                        "Condivisione YAML (fallback)"
                    } else {
                        "Scaricato: ${result.displayPath}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } else {
                onBack()
            }
        } else {
            Toast.makeText(context, "Salvataggio fallito", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadYaml() {
        val f = flow ?: return
        if (dirty) {
            saveFlow(optimize = false, thenDownload = true)
            return
        }
        val result = YamlDownloadHelper.downloadOrShare(context, app.flowStore, f)
        if (result == null) {
            Toast.makeText(context, "YAML non disponibile", Toast.LENGTH_SHORT).show()
        } else if (!result.usedShareFallback) {
            Toast.makeText(context, "Scaricato: ${result.displayPath}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedIndex != null) "Modifica · sel. ${(selectedIndex ?: 0) + 1}"
                        else "Modifica step",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { downloadYaml() }) {
                        Icon(Icons.Outlined.Download, contentDescription = "Scarica YAML")
                    }
                    IconButton(onClick = { showInsertDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aggiungi step")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = flowName,
                onValueChange = {
                    flowName = it
                    dirty = true
                },
                label = { Text("Nome flusso") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                flow?.appId.orEmpty(),
                style = CodeTextStyle,
                color = contentSecondary(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (selectedIndex != null) {
                    "Nuovi step: dopo #${(selectedIndex ?: 0) + 1}. Tocca + per scegliere il tipo."
                } else {
                    "Tocca uno step per selezionarlo — poi + per inserire sotto."
                },
                style = MaterialTheme.typography.labelMedium,
                color = contentSecondary(),
            )
            if (lintReport.errorCount > 0 || lintReport.warningCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        if (lintReport.errorCount > 0) append("⛔ ${lintReport.errorCount} errori")
                        if (lintReport.warningCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("⚠ ${lintReport.warningCount} avvisi")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lintReport.errorCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(
                    actions,
                    key = { index, a -> "$index-${a::class.simpleName}-${a.timestampMs}" },
                ) { index, action ->
                    val isLaunch = action is RecordedAction.LaunchApp
                    val selected = selectedIndex == index
                    StepRow(
                        index = index,
                        summary = stepSummary(action),
                        lintIssues = lintByStep[index].orEmpty(),
                        selected = selected,
                        canDelete = !isLaunch,
                        canDuplicate = !isLaunch,
                        canMoveUp = !isLaunch && index > 0 &&
                            actions.getOrNull(index - 1) !is RecordedAction.LaunchApp,
                        canMoveDown = !isLaunch && index < actions.lastIndex,
                        onSelect = { selectedIndex = index },
                        onEdit = { editIndex = index },
                        onDuplicate = { duplicateAction(index) },
                        onInsertBelow = {
                            selectedIndex = index
                            showInsertDialog = true
                        },
                        onDelete = {
                            if (!isLaunch) {
                                actions.removeAt(index)
                                dirty = true
                                selectedIndex = when {
                                    selectedIndex == null -> null
                                    selectedIndex == index -> null
                                    (selectedIndex ?: -1) > index -> selectedIndex!! - 1
                                    else -> selectedIndex
                                }
                            }
                        },
                        onMoveUp = {
                            if (isLaunch || index == 0) return@StepRow
                            if (index == 1 && actions[0] is RecordedAction.LaunchApp) return@StepRow
                            val tmp = actions[index]
                            actions[index] = actions[index - 1]
                            actions[index - 1] = tmp
                            selectedIndex = index - 1
                            dirty = true
                        },
                        onMoveDown = {
                            if (isLaunch || index >= actions.lastIndex) return@StepRow
                            val tmp = actions[index]
                            actions[index] = actions[index + 1]
                            actions[index + 1] = tmp
                            selectedIndex = index + 1
                            dirty = true
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val f = flow ?: return@launch
                            val msg = withContext(Dispatchers.Default) { app.validateFlow(f) }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Valida")
                }
                TextButton(
                    onClick = { showVaultDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Vault")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Annulla")
                }
                TextButton(
                    onClick = { saveFlow(optimize = true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Ottimizza")
                }
                Button(
                    onClick = { saveFlow(optimize = false) },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                ) {
                    Text("Salva")
                }
            }
        }
    }

    if (showInsertDialog) {
        InsertStepDialog(
            packageName = flow?.appId.orEmpty(),
            onDismiss = { showInsertDialog = false },
            onPick = { insertAction(it) },
        )
    }

    if (showVaultDialog) {
        AlertDialog(
            onDismissRequest = { showVaultDialog = false },
            title = { Text("Credenziali Maestro") },
            text = {
                Column {
                    Text(
                        "Salvate solo su device per Play (${'$'}{PIN} / ${'$'}{PASSWORD}).",
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
                        app.saveMaestroCredential(flow?.appId.orEmpty(), vaultPin, vaultPassword)
                        showVaultDialog = false
                        Toast.makeText(context, "Vault aggiornato", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showVaultDialog = false }) { Text("Annulla") }
            },
        )
    }

    editIndex?.let { idx ->
        val action = actions.getOrNull(idx) ?: return@let
        StepEditDialog(
            action = action,
            onDismiss = { editIndex = null },
            onConfirm = { updated ->
                actions[idx] = updated
                dirty = true
                editIndex = null
            },
        )
    }
}

@Composable
private fun StepRow(
    index: Int,
    summary: String,
    lintIssues: List<FlowLintIssue>,
    selected: Boolean,
    canDelete: Boolean,
    canDuplicate: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onInsertBelow: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    AccessScopeCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, PillShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
            ) {
                Text(
                    "${index + 1}. $summary",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                lintIssues.forEach { issue ->
                    val color = when (issue.severity) {
                        LintSeverity.ERROR -> MaterialTheme.colorScheme.error
                        LintSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                        LintSeverity.INFO -> contentSecondary()
                    }
                    val prefix = when (issue.severity) {
                        LintSeverity.ERROR -> "⛔ "
                        LintSeverity.WARNING -> "⚠ "
                        LintSeverity.INFO -> "ℹ "
                    }
                    Text(
                        prefix + issue.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
                if (selected) {
                    Text(
                        "Selezionato · + apre scelta tipo step",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentSecondary(),
                    )
                }
            }
            IconButton(onClick = onInsertBelow, enabled = canDuplicate) {
                Icon(Icons.Outlined.Add, contentDescription = "Inserisci step sotto")
            }
            IconButton(onClick = onDuplicate, enabled = canDuplicate) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplica step")
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Sposta su")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "Sposta giù")
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Elimina")
            }
        }
    }
}

/**
 * Copia un’azione con nuovo timestamp (per duplica in editor).
 *
 * @param action Azione sorgente.
 * @param now Timestamp ms.
 * @return Copia con timestamp aggiornato.
 */
fun copyActionWithNewTimestamp(action: RecordedAction, now: Long = System.currentTimeMillis()): RecordedAction =
    when (action) {
        is RecordedAction.LaunchApp -> action.copy(timestampMs = now)
        is RecordedAction.Tap -> action.copy(timestampMs = now)
        is RecordedAction.DoubleTap -> action.copy(timestampMs = now)
        is RecordedAction.LongPress -> action.copy(timestampMs = now)
        is RecordedAction.InputText -> action.copy(timestampMs = now)
        is RecordedAction.EraseText -> action.copy(timestampMs = now)
        is RecordedAction.Scroll -> action.copy(timestampMs = now)
        is RecordedAction.ScrollUntilVisible -> action.copy(timestampMs = now)
        is RecordedAction.Swipe -> action.copy(timestampMs = now)
        is RecordedAction.Back -> action.copy(timestampMs = now)
        is RecordedAction.PressKey -> action.copy(timestampMs = now)
        is RecordedAction.AssertVisible -> action.copy(timestampMs = now)
        is RecordedAction.AssertNotVisible -> action.copy(timestampMs = now)
        is RecordedAction.OpenLink -> action.copy(timestampMs = now)
        is RecordedAction.StopApp -> action.copy(timestampMs = now)
        is RecordedAction.Wait -> action.copy(timestampMs = now)
        is RecordedAction.WaitForAnimation -> action.copy(timestampMs = now)
        is RecordedAction.HideKeyboard -> action.copy(timestampMs = now)
        is RecordedAction.RawMaestroYaml -> action.copy(timestampMs = now)
    }

/**
 * Dialog di modifica campi per un singolo [RecordedAction].
 *
 * Completo sui tipi principali (optional, direction, contentDescription, point).
 */
@Composable
private fun StepEditDialog(
    action: RecordedAction,
    onDismiss: () -> Unit,
    onConfirm: (RecordedAction) -> Unit,
) {
    var viewId by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Tap -> action.viewId.orEmpty()
                is RecordedAction.DoubleTap -> action.viewId.orEmpty()
                is RecordedAction.LongPress -> action.viewId.orEmpty()
                is RecordedAction.InputText -> action.viewId.orEmpty()
                is RecordedAction.EraseText -> action.viewId.orEmpty()
                is RecordedAction.Wait -> action.visibleId.orEmpty()
                is RecordedAction.ScrollUntilVisible -> action.visibleId.orEmpty()
                is RecordedAction.AssertVisible -> action.viewId.orEmpty()
                is RecordedAction.AssertNotVisible -> action.viewId.orEmpty()
                else -> ""
            },
        )
    }
    var text by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Tap -> action.text.orEmpty()
                is RecordedAction.DoubleTap -> action.text.orEmpty()
                is RecordedAction.LongPress -> action.text.orEmpty()
                is RecordedAction.InputText -> action.text
                is RecordedAction.Wait -> action.visibleText.orEmpty()
                is RecordedAction.ScrollUntilVisible -> action.visibleText.orEmpty()
                is RecordedAction.AssertVisible -> action.text.orEmpty()
                is RecordedAction.AssertNotVisible -> action.text.orEmpty()
                is RecordedAction.PressKey -> action.key
                is RecordedAction.OpenLink -> action.url
                is RecordedAction.RawMaestroYaml -> action.yamlLines
                else -> ""
            },
        )
    }
    var contentDescription by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Tap -> action.contentDescription.orEmpty()
                is RecordedAction.DoubleTap -> action.contentDescription.orEmpty()
                is RecordedAction.LongPress -> action.contentDescription.orEmpty()
                else -> ""
            },
        )
    }
    var pointXy by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Tap ->
                    if (action.pointPercentX != null && action.pointPercentY != null) {
                        "${action.pointPercentX},${action.pointPercentY}"
                    } else {
                        ""
                    }
                else -> ""
            },
        )
    }
    var timeout by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Wait -> action.timeoutMs.toString()
                is RecordedAction.WaitForAnimation -> action.timeoutMs?.toString().orEmpty()
                is RecordedAction.AssertVisible -> action.timeoutMs.toString()
                is RecordedAction.AssertNotVisible -> action.timeoutMs.toString()
                is RecordedAction.ScrollUntilVisible -> action.timeoutMs.toString()
                else -> "1000"
            },
        )
    }
    var direction by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Scroll -> action.direction.name
                is RecordedAction.ScrollUntilVisible -> action.direction.name
                else -> ScrollDirection.DOWN.name
            },
        )
    }
    var swipeStart by remember {
        mutableStateOf(
            if (action is RecordedAction.Swipe) {
                "${action.startPercentX},${action.startPercentY}"
            } else {
                "50,80"
            },
        )
    }
    var swipeEnd by remember {
        mutableStateOf(
            if (action is RecordedAction.Swipe) {
                "${action.endPercentX},${action.endPercentY}"
            } else {
                "50,20"
            },
        )
    }
    var optional by remember {
        mutableStateOf(
            when (action) {
                is RecordedAction.Tap -> action.executionMode == StepExecutionMode.Optional
                is RecordedAction.AssertVisible -> action.executionMode == StepExecutionMode.Optional
                else -> false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica ${action::class.simpleName}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (action) {
                    is RecordedAction.LaunchApp -> Text("launchApp non modificabile")
                    is RecordedAction.Tap,
                    is RecordedAction.DoubleTap,
                    is RecordedAction.LongPress,
                    -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(text, { text = it }, label = { Text("text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            contentDescription,
                            { contentDescription = it },
                            label = { Text("contentDescription") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (action is RecordedAction.Tap) {
                            OutlinedTextField(
                                pointXy,
                                { pointXy = it },
                                label = { Text("point x,y % (opz.)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OptionalCheckbox(optional) { optional = it }
                        }
                    }
                    is RecordedAction.InputText -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id campo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(text, { text = it }, label = { Text("testo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    is RecordedAction.EraseText -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id campo (opz.)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    is RecordedAction.Scroll -> {
                        OutlinedTextField(
                            direction,
                            { direction = it.uppercase() },
                            label = { Text("direction UP/DOWN/LEFT/RIGHT") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is RecordedAction.Wait,
                    is RecordedAction.ScrollUntilVisible,
                    -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("visible id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(text, { text = it }, label = { Text("visible text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("timeout ms") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (action is RecordedAction.ScrollUntilVisible) {
                            OutlinedTextField(
                                direction,
                                { direction = it.uppercase() },
                                label = { Text("direction UP/DOWN/LEFT/RIGHT") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    is RecordedAction.AssertVisible,
                    is RecordedAction.AssertNotVisible,
                    -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(text, { text = it }, label = { Text("text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("timeout ms") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (action is RecordedAction.AssertVisible) {
                            OptionalCheckbox(optional) { optional = it }
                        }
                    }
                    is RecordedAction.WaitForAnimation -> {
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("timeout ms (opz.)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    is RecordedAction.PressKey -> {
                        OutlinedTextField(text, { text = it }, label = { Text("key (Enter/Back/Home)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    is RecordedAction.OpenLink -> {
                        OutlinedTextField(text, { text = it }, label = { Text("url") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    is RecordedAction.Swipe -> {
                        OutlinedTextField(swipeStart, { swipeStart = it }, label = { Text("start x,y %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(swipeEnd, { swipeEnd = it }, label = { Text("end x,y %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    is RecordedAction.RawMaestroYaml -> {
                        OutlinedTextField(text, { text = it }, label = { Text("YAML grezzo") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    }
                    else -> Text(stepSummary(action))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    fun parsePctPair(raw: String, fallbackX: Float, fallbackY: Float): Pair<Float, Float> {
                        val parts = raw.split(",").map { it.trim().removeSuffix("%") }
                        val x = parts.getOrNull(0)?.toFloatOrNull() ?: fallbackX
                        val y = parts.getOrNull(1)?.toFloatOrNull() ?: fallbackY
                        return x to y
                    }
                    fun parsePoint(raw: String): Pair<Float?, Float?> {
                        if (raw.isBlank()) return null to null
                        val parts = raw.split(",").map { it.trim().removeSuffix("%") }
                        return parts.getOrNull(0)?.toFloatOrNull() to parts.getOrNull(1)?.toFloatOrNull()
                    }
                    fun parseDir(raw: String, fallback: ScrollDirection): ScrollDirection =
                        runCatching { ScrollDirection.valueOf(raw.trim().uppercase()) }.getOrDefault(fallback)

                    val mode = if (optional) StepExecutionMode.Optional else StepExecutionMode.Required
                    val updated = when (action) {
                        is RecordedAction.Tap -> {
                            val (px, py) = parsePoint(pointXy)
                            action.copy(
                                viewId = viewId.ifBlank { null },
                                text = text.ifBlank { null },
                                contentDescription = contentDescription.ifBlank { null },
                                pointPercentX = px,
                                pointPercentY = py,
                                executionMode = mode,
                                weakSelector = viewId.isBlank() && text.isBlank() && contentDescription.isBlank(),
                            )
                        }
                        is RecordedAction.DoubleTap -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                            contentDescription = contentDescription.ifBlank { null },
                        )
                        is RecordedAction.LongPress -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                            contentDescription = contentDescription.ifBlank { null },
                        )
                        is RecordedAction.InputText -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text,
                            isPassword = text == "****",
                        )
                        is RecordedAction.EraseText -> action.copy(viewId = viewId.ifBlank { null })
                        is RecordedAction.Scroll -> action.copy(direction = parseDir(direction, action.direction))
                        is RecordedAction.Wait -> action.copy(
                            visibleId = viewId.ifBlank { null },
                            visibleText = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                        )
                        is RecordedAction.ScrollUntilVisible -> action.copy(
                            visibleId = viewId.ifBlank { null },
                            visibleText = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                            direction = parseDir(direction, action.direction),
                        )
                        is RecordedAction.AssertVisible -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                            executionMode = mode,
                        )
                        is RecordedAction.AssertNotVisible -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                        )
                        is RecordedAction.WaitForAnimation -> action.copy(timeoutMs = timeout.toLongOrNull())
                        is RecordedAction.PressKey -> action.copy(key = text.ifBlank { "Enter" })
                        is RecordedAction.OpenLink -> action.copy(url = text)
                        is RecordedAction.Swipe -> {
                            val (sx, sy) = parsePctPair(swipeStart, action.startPercentX, action.startPercentY)
                            val (ex, ey) = parsePctPair(swipeEnd, action.endPercentX, action.endPercentY)
                            action.copy(
                                startPercentX = sx,
                                startPercentY = sy,
                                endPercentX = ex,
                                endPercentY = ey,
                            )
                        }
                        is RecordedAction.RawMaestroYaml -> action.copy(yamlLines = text)
                        else -> action
                    }
                    onConfirm(updated)
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}

@Composable
private fun OptionalCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(
            "Opzionale (optional: true) — popup/permission non bloccano il test",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Riepilogo leggibile di uno step per lista editor.
 *
 * @param action Step da riassumere.
 * @return Stringa corta per la riga.
 */
fun stepSummary(action: RecordedAction): String = when (action) {
    is RecordedAction.LaunchApp -> "launchApp"
    is RecordedAction.Tap -> {
        val id = action.viewId?.substringAfterLast('/')
        val base = when {
            !id.isNullOrBlank() -> "tapOn id=$id"
            !action.text.isNullOrBlank() -> "tapOn \"${action.text}\""
            !action.contentDescription.isNullOrBlank() -> "tapOn cd=\"${action.contentDescription}\""
            action.pointPercentX != null -> "tapOn point"
            else -> "tapOn"
        }
        buildString {
            append(base)
            if (action.executionMode == StepExecutionMode.Optional) append(" · optional")
            if (action.weakSelector) append(" · debole")
        }
    }
    is RecordedAction.DoubleTap -> {
        val id = action.viewId?.substringAfterLast('/')
        when {
            !id.isNullOrBlank() -> "doubleTapOn id=$id"
            !action.text.isNullOrBlank() -> "doubleTapOn \"${action.text}\""
            else -> "doubleTapOn"
        }
    }
    is RecordedAction.LongPress -> "longPressOn"
    is RecordedAction.InputText ->
        if (action.isPassword) "inputText ****" else "inputText \"${action.text.take(24)}\""
    is RecordedAction.EraseText ->
        action.viewId?.let { "eraseText id=${it.substringAfterLast('/')}" } ?: "eraseText"
    is RecordedAction.Scroll -> "scroll ${action.direction.name}"
    is RecordedAction.ScrollUntilVisible -> {
        val id = action.visibleId?.substringAfterLast('/')
        when {
            !id.isNullOrBlank() -> "scrollUntilVisible id=$id ${action.direction.name}"
            !action.visibleText.isNullOrBlank() -> "scrollUntilVisible \"${action.visibleText}\""
            else -> "scrollUntilVisible"
        }
    }
    is RecordedAction.Swipe ->
        "swipe ${action.startPercentX},${action.startPercentY}→${action.endPercentX},${action.endPercentY}"
    is RecordedAction.Back -> "back"
    is RecordedAction.PressKey -> "pressKey ${action.key}"
    is RecordedAction.AssertVisible -> {
        val id = action.viewId?.substringAfterLast('/')
        val base = when {
            !id.isNullOrBlank() -> "assertVisible id=$id"
            !action.text.isNullOrBlank() -> "assertVisible \"${action.text}\""
            else -> "assertVisible"
        }
        if (action.executionMode == StepExecutionMode.Optional) "$base · optional" else base
    }
    is RecordedAction.AssertNotVisible -> {
        val id = action.viewId?.substringAfterLast('/')
        when {
            !id.isNullOrBlank() -> "assertNotVisible id=$id"
            !action.text.isNullOrBlank() -> "assertNotVisible \"${action.text}\""
            else -> "assertNotVisible"
        }
    }
    is RecordedAction.OpenLink -> "openLink ${action.url.take(40)}"
    is RecordedAction.StopApp -> "stopApp"
    is RecordedAction.HideKeyboard -> "hideKeyboard"
    is RecordedAction.WaitForAnimation -> "waitForAnimationToEnd"
    is RecordedAction.Wait -> when {
        !action.visibleId.isNullOrBlank() -> "waitUntil id=${action.visibleId}"
        !action.visibleText.isNullOrBlank() -> "waitUntil text=${action.visibleText}"
        else -> "wait ${action.timeoutMs}ms"
    }
    is RecordedAction.RawMaestroYaml -> "raw YAML (${action.yamlLines.lineSequence().count()} linee)"
}
