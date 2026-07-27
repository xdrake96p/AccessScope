/**
 * Editor step di un flusso Maestro (Beta): modifica, riordina, aggiungi azioni.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.ui.components.AccessScopeCard
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
    val flow = remember(flowId) { app.flowStore.getFlow(flowId) }
    val actions = remember { mutableStateListOf<RecordedAction>() }
    var flowName by remember { mutableStateOf(flow?.name.orEmpty()) }
    var addMenuExpanded by remember { mutableStateOf(false) }
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
        selectedIndex = null
    }

    fun insertAction(action: RecordedAction) {
        val sel = selectedIndex
        val insertAt = when {
            sel == null -> actions.size
            else -> (sel + 1).coerceIn(1, actions.size) // mai prima di launchApp (indice 0)
        }
        actions.add(insertAt, action)
        selectedIndex = insertAt
        addMenuExpanded = false
        Toast.makeText(context, "Inserito dopo step ${insertAt}", Toast.LENGTH_SHORT).show()
    }

    fun duplicateAction(index: Int) {
        val src = actions.getOrNull(index) ?: return
        if (src is RecordedAction.LaunchApp) return
        val copy = copyActionWithNewTimestamp(src)
        val insertAt = (index + 1).coerceAtMost(actions.size)
        actions.add(insertAt, copy)
        selectedIndex = insertAt
        Toast.makeText(context, "Step duplicato", Toast.LENGTH_SHORT).show()
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
                    IconButton(onClick = { addMenuExpanded = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aggiungi step")
                    }
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                    ) {
                        val pkg = flow?.appId.orEmpty()
                        fun add(action: RecordedAction) = insertAction(action)
                        // Interazione
                        DropdownMenuItem(
                            text = { Text("tapOn (id)") },
                            onClick = { add(RecordedAction.Tap(pkg, viewId = "button")) },
                        )
                        DropdownMenuItem(
                            text = { Text("tapOn (testo)") },
                            onClick = { add(RecordedAction.Tap(pkg, text = "OK")) },
                        )
                        DropdownMenuItem(
                            text = { Text("doubleTapOn") },
                            onClick = { add(RecordedAction.DoubleTap(pkg, text = "OK")) },
                        )
                        DropdownMenuItem(
                            text = { Text("longPressOn") },
                            onClick = { add(RecordedAction.LongPress(pkg, text = "OK")) },
                        )
                        DropdownMenuItem(
                            text = { Text("swipe") },
                            onClick = {
                                add(
                                    RecordedAction.Swipe(
                                        pkg,
                                        startPercentX = 50f,
                                        startPercentY = 80f,
                                        endPercentX = 50f,
                                        endPercentY = 20f,
                                    ),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("scroll") },
                            onClick = { add(RecordedAction.Scroll(pkg)) },
                        )
                        DropdownMenuItem(
                            text = { Text("scrollUntilVisible") },
                            onClick = {
                                add(RecordedAction.ScrollUntilVisible(pkg, visibleText = "OK"))
                            },
                        )
                        // Testo / tastiera
                        DropdownMenuItem(
                            text = { Text("inputText") },
                            onClick = { add(RecordedAction.InputText(pkg, text = "")) },
                        )
                        DropdownMenuItem(
                            text = { Text("eraseText") },
                            onClick = { add(RecordedAction.EraseText(pkg)) },
                        )
                        DropdownMenuItem(
                            text = { Text("hideKeyboard") },
                            onClick = { add(RecordedAction.HideKeyboard(pkg)) },
                        )
                        DropdownMenuItem(
                            text = { Text("pressKey") },
                            onClick = { add(RecordedAction.PressKey(pkg, key = "Enter")) },
                        )
                        DropdownMenuItem(
                            text = { Text("back") },
                            onClick = { add(RecordedAction.Back(pkg)) },
                        )
                        // Assert / wait
                        DropdownMenuItem(
                            text = { Text("assertVisible") },
                            onClick = { add(RecordedAction.AssertVisible(pkg, text = "OK")) },
                        )
                        DropdownMenuItem(
                            text = { Text("assertNotVisible") },
                            onClick = { add(RecordedAction.AssertNotVisible(pkg, text = "OK")) },
                        )
                        DropdownMenuItem(
                            text = { Text("wait") },
                            onClick = { add(RecordedAction.Wait(pkg, timeoutMs = 1_000L)) },
                        )
                        DropdownMenuItem(
                            text = { Text("waitForAnimation") },
                            onClick = { add(RecordedAction.WaitForAnimation(pkg)) },
                        )
                        // App / link / raw
                        DropdownMenuItem(
                            text = { Text("openLink") },
                            onClick = { add(RecordedAction.OpenLink(pkg, url = "https://")) },
                        )
                        DropdownMenuItem(
                            text = { Text("stopApp") },
                            onClick = { add(RecordedAction.StopApp(pkg)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Raw YAML") },
                            onClick = {
                                add(
                                    RecordedAction.RawMaestroYaml(
                                        pkg,
                                        yamlLines = "- tapOn: \"OK\"",
                                    ),
                                )
                            },
                        )
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
                onValueChange = { flowName = it },
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
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(actions, key = { index, a -> "$index-${a::class.simpleName}-${a.timestampMs}" }) { index, action ->
                    val isLaunch = action is RecordedAction.LaunchApp
                    val selected = selectedIndex == index
                    StepRow(
                        index = index,
                        summary = stepSummary(action),
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
                            insertAction(
                                RecordedAction.Wait(
                                    packageName = flow?.appId.orEmpty(),
                                    timeoutMs = 1_000L,
                                ),
                            )
                        },
                        onDelete = {
                            if (!isLaunch) {
                                actions.removeAt(index)
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
                        },
                        onMoveDown = {
                            if (isLaunch || index >= actions.lastIndex) return@StepRow
                            val tmp = actions[index]
                            actions[index] = actions[index + 1]
                            actions[index + 1] = tmp
                            selectedIndex = index + 1
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Annulla")
                }
                Button(
                    onClick = {
                        val updated = app.flowStore.updateFlow(
                            id = flowId,
                            actions = actions.toList(),
                            name = flowName,
                            optimize = false,
                        )
                        if (updated != null) {
                            Toast.makeText(context, "Flusso salvato (${updated.stepCount} step)", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Salvataggio fallito", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                ) {
                    Text("Salva")
                }
            }
        }
    }

    editIndex?.let { idx ->
        val action = actions.getOrNull(idx) ?: return@let
        StepEditDialog(
            action = action,
            onDismiss = { editIndex = null },
            onConfirm = { updated ->
                actions[idx] = updated
                editIndex = null
            },
        )
    }
}

@Composable
private fun StepRow(
    index: Int,
    summary: String,
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
                if (selected) {
                    Text(
                        "Selezionato · + inserisce sotto",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentSecondary(),
                    )
                }
            }
            IconButton(onClick = onInsertBelow, enabled = canDuplicate) {
                Icon(Icons.Outlined.Add, contentDescription = "Inserisci sotto")
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
                is RecordedAction.Swipe ->
                    "${action.startPercentX},${action.startPercentY} → ${action.endPercentX},${action.endPercentY}"
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica ${action::class.simpleName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (action) {
                    is RecordedAction.LaunchApp -> Text("launchApp non modificabile")
                    is RecordedAction.Tap,
                    is RecordedAction.DoubleTap,
                    is RecordedAction.LongPress,
                    -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id") }, singleLine = true)
                        OutlinedTextField(text, { text = it }, label = { Text("text") }, singleLine = true)
                    }
                    is RecordedAction.InputText -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id campo") }, singleLine = true)
                        OutlinedTextField(text, { text = it }, label = { Text("testo") }, singleLine = true)
                    }
                    is RecordedAction.EraseText -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id campo (opz.)") }, singleLine = true)
                    }
                    is RecordedAction.Wait,
                    is RecordedAction.ScrollUntilVisible,
                    -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("visible id") }, singleLine = true)
                        OutlinedTextField(text, { text = it }, label = { Text("visible text") }, singleLine = true)
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("timeout ms") }, singleLine = true)
                    }
                    is RecordedAction.AssertVisible,
                    is RecordedAction.AssertNotVisible,
                    -> {
                        OutlinedTextField(viewId, { viewId = it }, label = { Text("id") }, singleLine = true)
                        OutlinedTextField(text, { text = it }, label = { Text("text") }, singleLine = true)
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("timeout ms") }, singleLine = true)
                    }
                    is RecordedAction.WaitForAnimation -> {
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("timeout ms (opz.)") }, singleLine = true)
                    }
                    is RecordedAction.PressKey -> {
                        OutlinedTextField(text, { text = it }, label = { Text("key (Enter/Back/Home)") }, singleLine = true)
                    }
                    is RecordedAction.OpenLink -> {
                        OutlinedTextField(text, { text = it }, label = { Text("url") }, singleLine = true)
                    }
                    is RecordedAction.Swipe -> {
                        OutlinedTextField(swipeStart, { swipeStart = it }, label = { Text("start x,y %") }, singleLine = true)
                        OutlinedTextField(swipeEnd, { swipeEnd = it }, label = { Text("end x,y %") }, singleLine = true)
                    }
                    is RecordedAction.RawMaestroYaml -> {
                        OutlinedTextField(
                            text,
                            { text = it },
                            label = { Text("YAML grezzo") },
                            minLines = 3,
                        )
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
                    val updated = when (action) {
                        is RecordedAction.Tap -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                        )
                        is RecordedAction.DoubleTap -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                        )
                        is RecordedAction.LongPress -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                        )
                        is RecordedAction.InputText -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text,
                            // Se l’utente sostituisce **** con testo reale, abilita scrittura in Play.
                            isPassword = text == "****",
                        )
                        is RecordedAction.EraseText -> action.copy(
                            viewId = viewId.ifBlank { null },
                        )
                        is RecordedAction.Wait -> action.copy(
                            visibleId = viewId.ifBlank { null },
                            visibleText = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                        )
                        is RecordedAction.ScrollUntilVisible -> action.copy(
                            visibleId = viewId.ifBlank { null },
                            visibleText = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                        )
                        is RecordedAction.AssertVisible -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                        )
                        is RecordedAction.AssertNotVisible -> action.copy(
                            viewId = viewId.ifBlank { null },
                            text = text.ifBlank { null },
                            timeoutMs = timeout.toLongOrNull() ?: action.timeoutMs,
                        )
                        is RecordedAction.WaitForAnimation -> action.copy(
                            timeoutMs = timeout.toLongOrNull(),
                        )
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

/**
 * Riepilogo leggibile di uno step per lista editor.
 */
fun stepSummary(action: RecordedAction): String = when (action) {
    is RecordedAction.LaunchApp -> "launchApp"
    is RecordedAction.Tap -> {
        val id = action.viewId?.substringAfterLast('/')
        when {
            !id.isNullOrBlank() -> "tapOn id=$id"
            !action.text.isNullOrBlank() -> "tapOn \"${action.text}\""
            action.pointPercentX != null -> "tapOn point"
            else -> "tapOn"
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
            !id.isNullOrBlank() -> "scrollUntilVisible id=$id"
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
        when {
            !id.isNullOrBlank() -> "assertVisible id=$id"
            !action.text.isNullOrBlank() -> "assertVisible \"${action.text}\""
            else -> "assertVisible"
        }
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
