/**
 * Riproduzione in-app di flussi Maestro via AccessibilityService (Beta).
 */
package dev.accessscope.scanner.recorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.recorder.capture.FieldInputTargetResolver
import dev.accessscope.scanner.recorder.model.PlayOutcome
import dev.accessscope.scanner.recorder.model.PlayStepResult
import dev.accessscope.scanner.recorder.model.PlayStepStatus
import dev.accessscope.scanner.recorder.model.SelectorCandidate
import dev.accessscope.scanner.recorder.model.SelectorWin
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugSessionLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Esegue sequenzialmente [RecordedAction] sull’app target.
 */
class FlowPlayer(
    private val context: Context,
    private val serviceProvider: () -> AccessibilityService?,
    private val credentialVault: CredentialVault = CredentialVault(context),
) {

    private val selectorWins = mutableListOf<SelectorWin>()
    private var currentStepIndex: Int = 0
    /** Dati usati nell'ultimo step (per report cliente, mascherati). */
    private var lastStepDataUsed: String? = null
    private val stepResults = mutableListOf<PlayStepResult>()

    /**
     * Note di divergenza rispetto al `maestro` CLI, raccolte durante `play()`. Il Play in-app ha
     * rami "morbidi" che il CLI non ha (fallback selettori, skip di segreti non risolti, wait
     * soft-fail): un flusso può risultare tutto verde in-app pur non avendo davvero verificato
     * ciò che verificherebbe `maestro test`. Non cambiano l'esito (verde resta verde: demo
     * sicura), ma vengono riportate nel [PlayOutcome] così l'utente sa cosa il CLI reale
     * potrebbe non riprodurre allo stesso modo.
     */
    private val divergences = mutableListOf<String>()

    private fun noteDivergence(message: String) {
        divergences += "step ${currentStepIndex + 1}: $message"
    }

    /**
     * Riproduce le azioni.
     *
     * @param actions Lista da `actions.json`.
     * @param clearState Se true, stopApp + tentativo clear + cold launch prima del flusso.
     * @param onStep Callback (indice 0-based, totale) a ogni step.
     * @return [PlayOutcome] con errore e selettori vincenti.
     */
    suspend fun play(
        actions: List<RecordedAction>,
        clearState: Boolean = false,
        onStep: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): PlayOutcome {
        selectorWins.clear()
        divergences.clear()
        stepResults.clear()
        if (actions.isEmpty()) return PlayOutcome(error = "Flusso vuoto")
        val appId = actions.firstOrNull()?.packageName?.takeIf { it.isNotBlank() }
            ?: actions.filterIsInstance<RecordedAction.LaunchApp>().firstOrNull()?.packageName
        if (clearState && !appId.isNullOrBlank()) {
            val prep = prepareCleanLaunch(appId)
            if (prep != null) {
                AppFileLogger.info("FlowPlayer", "clearState_note $prep")
            }
        }
        val total = actions.size
        // #region agent log
        val pinSlotInputs = actions.count {
            it is RecordedAction.InputText &&
                MaestroSelectorHeuristics.isPinPadDigitSlot(it.viewId)
        }
        val pinPadTaps = actions.count {
            it is RecordedAction.Tap &&
                (
                    MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) ||
                        MaestroSelectorHeuristics.isPinPadDigitTap(it.text, it.viewId)
                    )
        }
        val optionalPadTaps = actions.count {
            it is RecordedAction.Tap &&
                it.executionMode == StepExecutionMode.Optional &&
                (
                    MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) ||
                        MaestroSelectorHeuristics.isPinPadDigitTap(it.text, it.viewId)
                    )
        }
        DebugSessionLog.log(
            "E",
            "FlowPlayer.play",
            "pin_flow_summary",
            mapOf(
                "total" to total,
                "pinSlotInputs" to pinSlotInputs,
                "pinPadTaps" to pinPadTaps,
                "optionalPadTaps" to optionalPadTaps,
            ),
        )
        AppFileLogger.info(
            "FlowPlayer",
            "pin_flow_summary slots=$pinSlotInputs pads=$pinPadTaps optPads=$optionalPadTaps total=$total",
        )
        // #endregion
        for ((index, action) in actions.withIndex()) {
            currentStepIndex = index
            onStep(index, total)
            // #region agent log
            DebugSessionLog.log(
                "H7",
                "FlowPlayer.play",
                "step_begin",
                mapOf(
                    "index" to index,
                    "total" to total,
                    "type" to action::class.simpleName,
                    "clearState" to clearState,
                    "execMode" to when (action) {
                        is RecordedAction.Tap -> action.executionMode.name
                        else -> null
                    },
                    "viewId" to when (action) {
                        is RecordedAction.InputText -> action.viewId
                        is RecordedAction.EraseText -> action.viewId
                        is RecordedAction.Tap -> action.viewId
                        is RecordedAction.DoubleTap -> action.viewId
                        is RecordedAction.LongPress -> action.viewId
                        is RecordedAction.AssertVisible -> action.viewId
                        is RecordedAction.AssertNotVisible -> action.viewId
                        is RecordedAction.Wait -> action.visibleId
                        is RecordedAction.ScrollUntilVisible -> action.visibleId
                        else -> null
                    },
                    "text" to when (action) {
                        is RecordedAction.Tap -> action.text
                        is RecordedAction.DoubleTap -> action.text
                        is RecordedAction.AssertVisible -> action.text
                        is RecordedAction.AssertNotVisible -> action.text
                        is RecordedAction.Wait -> action.visibleText
                        is RecordedAction.ScrollUntilVisible -> action.visibleText
                        else -> null
                    },
                    "timeoutMs" to when (action) {
                        is RecordedAction.Wait -> action.timeoutMs
                        is RecordedAction.WaitForAnimation -> action.timeoutMs
                        is RecordedAction.AssertVisible -> action.timeoutMs
                        is RecordedAction.AssertNotVisible -> action.timeoutMs
                        is RecordedAction.ScrollUntilVisible -> action.timeoutMs
                        else -> null
                    },
                    "isPassword" to ((action as? RecordedAction.InputText)?.isPassword ?: false),
                    "textLen" to ((action as? RecordedAction.InputText)?.text?.length ?: 0),
                    "textMasked" to ((action as? RecordedAction.InputText)?.text == "****"),
                ),
            )
            // #endregion
            val err = runCatching {
                lastStepDataUsed = null
                when {
                    clearState && action is RecordedAction.LaunchApp ->
                        launchApp(action.packageName, coldStart = true)
                    else -> execute(action)
                }
            }.exceptionOrNull()?.message
            if (err != null) {
                // #region agent log
                DebugSessionLog.log(
                    "D",
                    "FlowPlayer.play",
                    "step_fail",
                    mapOf("index" to index, "err" to err, "type" to action::class.simpleName),
                )
                // #endregion
                if (isOptionalStep(action)) {
                    AppFileLogger.info("FlowPlayer", "skip_optional i=$index err=$err")
                    recordStepResult(action, index, PlayStepStatus.SKIPPED_OPTIONAL, err)
                    continue
                }
                AppFileLogger.info("FlowPlayer", "step_fail i=$index err=$err")
                recordStepResult(action, index, PlayStepStatus.FAILED, err)
                stepResults += notRunSteps(actions, fromIndex = index + 1)
                return PlayOutcome(
                    error = "Step ${index + 1}: $err",
                    selectorWins = selectorWins.toList(),
                    divergences = divergences.toList(),
                    stepResults = stepResults.toList(),
                )
            }
            recordStepResult(action, index, PlayStepStatus.PASSED, note = stepDivergenceNote(index))
            delay(BETWEEN_STEPS_MS)
        }
        return PlayOutcome(
            selectorWins = selectorWins.toList(),
            divergences = divergences.toList(),
            stepResults = stepResults.toList(),
        )
    }

    /**
     * Dry-run: verifica che i target Tap/Assert siano trovabili senza gesture.
     */
    suspend fun validate(actions: List<RecordedAction>): PlayOutcome {
        stepResults.clear()
        val failures = mutableListOf<Int>()
        val svc = service() ?: return PlayOutcome(error = "Servizio accessibilità non collegato")
        for ((index, action) in actions.withIndex()) {
            val ok = when (action) {
                is RecordedAction.Tap -> canResolveTap(svc, action)
                is RecordedAction.AssertVisible -> {
                    val node = findNode(svc, action.viewId, action.text, null)
                    val found = node != null
                    node?.recycle()
                    found
                }
                else -> true
            }
            if (!ok) {
                failures += index
                recordStepResult(
                    action,
                    index,
                    PlayStepStatus.FAILED,
                    "Target non trovato (validate)",
                )
            } else {
                recordStepResult(action, index, PlayStepStatus.PASSED)
            }
            delay(50)
        }
        return PlayOutcome(
            validateFailures = failures,
            stepResults = stepResults.toList(),
            error = if (failures.isNotEmpty()) {
                "Validate: ${failures.size} step non trovati"
            } else {
                null
            },
        )
    }

    /**
     * Stop + tentativo clear dati + attesa prima del cold launch.
     *
     * @return Nota utente se clear non consentito, altrimenti `null`.
     */
    private suspend fun prepareCleanLaunch(packageName: String): String? {
        stopApp(packageName)
        delay(400)
        val cleared = tryClearApplicationData(packageName)
        delay(600)
        return if (cleared) {
            null
        } else {
            "Clear non consentito: chiudi sessione manualmente se serve login fresco"
        }
    }

    /**
     * Tenta di azzerare i dati dell’app target (spesso fallisce senza privilegi di sistema).
     */
    private fun tryClearApplicationData(packageName: String): Boolean {
        val ok = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val observerClass = Class.forName("android.content.pm.IPackageDataObserver")
            val method = android.app.ActivityManager::class.java.getMethod(
                "clearApplicationUserData",
                String::class.java,
                observerClass,
            )
            (method.invoke(am, packageName, null) as? Boolean) == true
        }.onFailure { e ->
            AppFileLogger.info("FlowPlayer", "clearApplicationUserData_fail ${e.message}")
        }.getOrDefault(false)
        AppFileLogger.info("FlowPlayer", "clearApplicationUserData pkg=$packageName ok=$ok")
        return ok
    }

    private fun isOptionalStep(action: RecordedAction): Boolean =
        when (action) {
            is RecordedAction.Tap -> action.executionMode == StepExecutionMode.Optional
            is RecordedAction.AssertVisible -> action.executionMode == StepExecutionMode.Optional
            is RecordedAction.InputText -> action.executionMode == StepExecutionMode.Optional
            else -> false
        }

    private fun recordStepResult(
        action: RecordedAction,
        index: Int,
        status: PlayStepStatus,
        error: String? = null,
        note: String? = null,
    ) {
        stepResults += PlayStepResult(
            index = index,
            summary = RecordingLivePreview.summarize(action),
            actionType = action::class.simpleName.orEmpty(),
            status = status,
            dataUsed = lastStepDataUsed ?: dataUsedHint(action),
            error = error,
            note = note,
        )
    }

    private fun notRunSteps(actions: List<RecordedAction>, fromIndex: Int): List<PlayStepResult> =
        actions.drop(fromIndex).mapIndexed { offset, action ->
            PlayStepResult(
                index = fromIndex + offset,
                summary = RecordingLivePreview.summarize(action),
                actionType = action::class.simpleName.orEmpty(),
                status = PlayStepStatus.NOT_RUN,
                dataUsed = dataUsedHint(action),
            )
        }

    private fun stepDivergenceNote(index: Int): String? {
        val prefix = "step ${index + 1}:"
        return divergences.lastOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun dataUsedHint(action: RecordedAction): String? = when (action) {
        is RecordedAction.InputText -> when {
            action.isPassword || action.text == "****" -> "****"
            action.text == CredentialVault.PLACEHOLDER_PIN -> "\${PIN}"
            action.text == CredentialVault.PLACEHOLDER_PASSWORD -> "\${PASSWORD}"
            else -> "\"${action.text.take(32)}\""
        }
        is RecordedAction.Tap -> action.text?.takeIf { it.isNotBlank() }?.let { "\"$it\"" }
        is RecordedAction.AssertVisible -> action.text?.takeIf { it.isNotBlank() }
            ?: MaestroSelectorHeuristics.shortViewId(action.viewId)?.let { "id=$it" }
        else -> null
    }

    private fun maskDataForReport(resolved: String, action: RecordedAction.InputText): String = when {
        action.isPassword || action.text == "****" || resolved == "****" -> "****"
        resolved == CredentialVault.PLACEHOLDER_PIN || action.text == CredentialVault.PLACEHOLDER_PIN ->
            "\${PIN} (${resolved.length} cifre)".takeIf { resolved != CredentialVault.PLACEHOLDER_PIN }
                ?: "\${PIN} (non risolto)"
        resolved == CredentialVault.PLACEHOLDER_PASSWORD ||
            action.text == CredentialVault.PLACEHOLDER_PASSWORD ->
            "\${PASSWORD} (non risolto)"
        resolved.all { it.isDigit() } && resolved.length in 4..12 -> "PIN (${resolved.length} cifre)"
        else -> "\"${resolved.take(32)}\""
    }

    private suspend fun execute(action: RecordedAction) {
        when (action) {
            is RecordedAction.LaunchApp -> launchApp(action.packageName)
            is RecordedAction.Tap -> {
                if (MaestroSelectorHeuristics.isNoiseTap(action)) {
                    AppFileLogger.info("FlowPlayer", "skip_noise_tap id=${action.viewId}")
                    return
                }
                tap(action)
            }
            is RecordedAction.DoubleTap -> doubleTap(action)
            is RecordedAction.LongPress -> longPress(action)
            is RecordedAction.InputText -> inputText(action)
            is RecordedAction.EraseText -> eraseText(action)
            is RecordedAction.Scroll -> scroll(action.packageName, action.direction)
            is RecordedAction.ScrollUntilVisible -> scrollUntilVisible(action)
            is RecordedAction.Swipe -> swipe(action)
            is RecordedAction.Back -> {
                service()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    ?: error("Servizio accessibilità non collegato")
            }
            is RecordedAction.PressKey -> pressKey(action)
            is RecordedAction.AssertVisible -> assertVisible(action)
            is RecordedAction.AssertNotVisible -> assertNotVisible(action)
            is RecordedAction.OpenLink -> openLink(action.url)
            is RecordedAction.StopApp -> stopApp(action.packageName)
            is RecordedAction.HideKeyboard -> hideKeyboardSoft()
            is RecordedAction.WaitForAnimation -> waitForAnimationToEnd(action)
            is RecordedAction.Wait -> waitUntil(action)
            is RecordedAction.RawMaestroYaml -> {
                AppFileLogger.info(
                    "FlowPlayer",
                    "skip_raw_yaml lines=${action.yamlLines.lineSequence().count()}",
                )
            }
        }
    }

    /**
     * Attende fine animazione: UI stabile (fingerprint root) per ≥650ms, entro timeout.
     * Evita tap sul frame successivo mentre la schermata sta ancora animando.
     */
    private suspend fun waitForAnimationToEnd(action: RecordedAction.WaitForAnimation) {
        val timeout = action.timeoutMs?.coerceIn(400L, 10_000L) ?: DEFAULT_ANIM_MS
        val quietNeededMs = 650L
        val deadline = System.currentTimeMillis() + timeout
        var lastSig: String? = null
        var stableSince = System.currentTimeMillis()
        // #region agent log
        DebugSessionLog.log(
            "H1",
            "FlowPlayer.waitForAnimationToEnd",
            "start",
            mapOf("timeoutMs" to timeout),
        )
        // #endregion
        while (System.currentTimeMillis() < deadline) {
            val sig = uiStabilitySignature()
            val now = System.currentTimeMillis()
            if (sig != lastSig) {
                lastSig = sig
                stableSince = now
            } else if (now - stableSince >= quietNeededMs) {
                return
            }
            delay(100)
        }
    }

    /** Firma leggera della UI attiva (titolo + childCount + testi top) per quiescenza. */
    private fun uiStabilitySignature(): String {
        val svc = service() ?: return "no-svc"
        val root = svc.rootInActiveWindow ?: return "no-root"
        return try {
            val title = root.window?.title?.toString().orEmpty()
            val texts = StringBuilder()
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 2) return
                n.text?.toString()?.take(24)?.let { texts.append(it).append('|') }
                for (i in 0 until minOf(n.childCount, 6)) {
                    val c = n.getChild(i) ?: continue
                    walk(c, depth + 1)
                    c.recycle()
                }
            }
            walk(root, 0)
            "$title#${root.childCount}#$texts"
        } finally {
            root.recycle()
        }
    }

    /**
     * Chiude la tastiera senza BACK (evita navigazione indietro su login/form).
     */
    private suspend fun hideKeyboardSoft() {
        hideSoftInputBestEffort()
        delay(200)
    }

    private fun launchApp(packageName: String, coldStart: Boolean = false) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("Impossibile aprire $packageName")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (coldStart) {
            launch.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
        context.startActivity(launch)
        AppFileLogger.info("FlowPlayer", "launchApp pkg=$packageName cold=$coldStart")
    }

    private suspend fun tap(action: RecordedAction.Tap) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        if (!action.text.isNullOrBlank() &&
            FieldInputTargetResolver.looksLikePickerListItem(action.text)
        ) {
            ensurePickerListItemVisible(svc, action.text!!)
        }
        val chain = action.selectorChain.ifEmpty {
            listOf(
                SelectorCandidate(
                    viewId = action.viewId,
                    text = action.text,
                    contentDescription = action.contentDescription,
                    pointPercentX = action.pointPercentX,
                    pointPercentY = action.pointPercentY,
                ),
            ).filterNot { it.isBlank() }
        }
        for ((ci, candidate) in chain.withIndex()) {
            val probe = action.copy(
                viewId = candidate.viewId,
                text = candidate.text,
                contentDescription = candidate.contentDescription,
                pointPercentX = candidate.pointPercentX,
                pointPercentY = candidate.pointPercentY,
                selectorChain = emptyList(),
            )
            val hasLogical = !candidate.viewId.isNullOrBlank() ||
                !candidate.text.isNullOrBlank() ||
                !candidate.contentDescription.isNullOrBlank()
            val isPinPad = MaestroSelectorHeuristics.isPinPadKey(candidate.viewId, candidate.text) ||
                MaestroSelectorHeuristics.isPinPadDigitTap(candidate.text, candidate.viewId)
            val findTimeout = when {
                isPinPad -> PIN_PAD_FIND_TIMEOUT_MS
                action.executionMode == StepExecutionMode.Optional -> OPTIONAL_TAP_FIND_TIMEOUT_MS
                else -> TAP_FIND_TIMEOUT_MS
            }
            val waitStarted = System.currentTimeMillis()
            val node = if (hasLogical) {
                waitForTapTarget(svc, probe, findTimeout)
            } else {
                null
            }
            val waitElapsed = System.currentTimeMillis() - waitStarted
            // #region agent log
            DebugSessionLog.log(
                "A",
                "FlowPlayer.tap",
                "tap_target",
                mapOf(
                    "chainIndex" to ci,
                    "viewId" to candidate.viewId,
                    "text" to candidate.text,
                    "found" to (node != null),
                    "nodeText" to node?.text?.toString()?.take(40),
                    "nodeViewId" to node?.viewIdResourceName?.substringAfterLast('/'),
                    "clickable" to node?.isClickable,
                    "hasPoint" to (candidate.pointPercentX != null),
                    "isPinPad" to isPinPad,
                    "optional" to (action.executionMode == StepExecutionMode.Optional),
                    "findTimeoutMs" to findTimeout,
                    "waitElapsedMs" to waitElapsed,
                ),
            )
            if (isPinPad) {
                AppFileLogger.info(
                    "FlowPlayer",
                    "pin_tap_wait id=${candidate.viewId} text=${candidate.text} " +
                        "found=${node != null} elapsed=${waitElapsed}ms timeout=${findTimeout}ms " +
                        "opt=${action.executionMode == StepExecutionMode.Optional}",
                )
            }
            // #endregion
            if (node != null) {
                try {
                    performTapOnNode(svc, probe, node)
                    recoverFromAccidentalPickerOverlay(svc, action)
                    if (ci > 0) {
                        selectorWins += SelectorWin(
                            stepIndex = currentStepIndex,
                            originalViewId = action.viewId,
                            originalText = action.text,
                            candidate = candidate,
                            chainIndex = ci,
                        )
                    }
                    return
                } finally {
                    node.recycle()
                }
            }
            if (!hasLogical && candidate.pointPercentX != null && candidate.pointPercentY != null) {
                // #region agent log
                DebugSessionLog.log(
                    "F3",
                    "FlowPlayer.tap",
                    "point_only",
                    mapOf("chainIndex" to ci, "hasPoint" to true),
                )
                // #endregion
                clickPointFallback(svc, candidate.pointPercentX, candidate.pointPercentY, null)
                if (ci > 0) {
                    selectorWins += SelectorWin(
                        stepIndex = currentStepIndex,
                        originalViewId = action.viewId,
                        originalText = action.text,
                        candidate = candidate,
                        chainIndex = ci,
                    )
                }
                noteDivergence(
                    "risolto via fallback coordinate (${action.viewId ?: action.text ?: "?"}) → " +
                        "il CLI userebbe solo il selettore",
                )
                return
            }
        }
        // #region agent log
        DebugSessionLog.log(
            "F3",
            "FlowPlayer.tap",
            "skip_stale_point_fallback",
            mapOf("text" to action.text, "viewId" to action.viewId, "chainSize" to chain.size),
        )
        // #endregion
        AppFileLogger.info(
            "FlowPlayer",
            "tap_skip_not_found id=${action.viewId} text=${action.text} chain=${chain.size}",
        )
        tryPickerIconTap(svc, action)?.let { icon ->
            try {
                AppFileLogger.info(
                    "FlowPlayer",
                    "field_label_picker_icon_fallback label=${action.text} icon=${icon.viewIdResourceName}",
                )
                performTapOnNode(svc, action, icon)
                noteDivergence(
                    "tap su icona picker al posto del campo (${action.text}) → il CLI userebbe tapOn testo",
                )
                return
            } finally {
                icon.recycle()
            }
        }
        // Pad custom assente dall’albero: digita la cifra sul prossimo EditText editN.
        val isPinPadTap = MaestroSelectorHeuristics.isPinPadKey(action.viewId, action.text) ||
            MaestroSelectorHeuristics.isPinPadDigitTap(action.text, action.viewId)
        if (isPinPadTap) {
            val digit = action.text?.trim()?.takeIf { it.length == 1 && it[0].isDigit() }
                ?: pinPadKeyToDigit(action.viewId)
            val ok = !digit.isNullOrBlank() && inputPinDigitOnSlots(svc, digit.orEmpty())
            // #region agent log
            DebugSessionLog.log(
                "C",
                "FlowPlayer.tap",
                "pin_digit_fallback",
                mapOf(
                    "digit" to digit,
                    "ok" to ok,
                    "viewId" to action.viewId,
                    "text" to action.text,
                    "optional" to (action.executionMode == StepExecutionMode.Optional),
                    "step" to currentStepIndex,
                ),
            )
            // #endregion
            if (ok) {
                AppFileLogger.info("FlowPlayer", "pin_digit_fallback digit=$digit ok=true")
                noteDivergence(
                    "tasto pad assente, cifra $digit digitata su slot edit (${action.viewId ?: action.text}) → " +
                        "il CLI userebbe solo il selettore originale",
                )
                return
            }
            AppFileLogger.info("FlowPlayer", "pin_digit_fallback digit=$digit ok=false")
            error("Cifra pad non inserita ($digit) id=${action.viewId} text=${action.text}")
        }
        if (action.executionMode == StepExecutionMode.Optional) return
        error("Tap non trovato id=${action.viewId} text=${action.text}")
    }

    /**
     * Mappa id pad IT (`uno`…`nove`/`zero`) → cifra.
     */
    private fun pinPadKeyToDigit(viewId: String?): String? {
        val short = MaestroSelectorHeuristics.shortViewId(viewId)?.lowercase().orEmpty()
        return when (short) {
            "zero", "key_0", "btn_0", "num_0", "digit_0" -> "0"
            "uno", "key_1", "btn_1", "num_1", "digit_1" -> "1"
            "due", "key_2", "btn_2", "num_2", "digit_2" -> "2"
            "tre", "key_3", "btn_3", "num_3", "digit_3" -> "3"
            "quattro", "key_4", "btn_4", "num_4", "digit_4" -> "4"
            "cinque", "key_5", "btn_5", "num_5", "digit_5" -> "5"
            "sei", "key_6", "btn_6", "num_6", "digit_6" -> "6"
            "sette", "key_7", "btn_7", "num_7", "digit_7" -> "7"
            "otto", "key_8", "btn_8", "num_8", "digit_8" -> "8"
            "nove", "key_9", "btn_9", "num_9", "digit_9" -> "9"
            else -> Regex("(\\d)$").find(short)?.groupValues?.get(1)
        }
    }

    /**
     * Scrive il codice PIN/OTP una cifra per slot (`edit1`…`editN`).
     * Necessario perché SET_TEXT del codice intero su `edit1` lascia solo 1 char.
     *
     * @return `true` se tutte le cifre sono state scritte.
     */
    private fun inputPinCodeOnSlots(
        svc: AccessibilityService,
        packageName: String,
        code: String,
        hintViewId: String?,
    ): Boolean {
        val pkg = packageName.ifBlank {
            hintViewId?.substringBefore(":id/")?.takeIf { it.isNotBlank() }
        } ?: return false
        val roots = collectRoots(svc)
        if (roots.isEmpty()) return false
        try {
            var wrote = 0
            for ((idx, ch) in code.withIndex()) {
                val n = idx + 1
                val fullId = "$pkg:id/edit$n"
                var done = false
                for (root in roots) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: continue
                    if (nodes.isEmpty()) {
                        nodes.forEach { it.recycle() }
                        continue
                    }
                    val node = AccessibilityNodeInfo.obtain(nodes.first())
                    nodes.forEach { it.recycle() }
                    try {
                        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                ch.toString(),
                            )
                        }
                        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                            wrote++
                            done = true
                            // #region agent log
                            DebugSessionLog.log(
                                "B",
                                "FlowPlayer.inputPinCodeOnSlots",
                                "wrote_digit",
                                mapOf("slot" to n, "digit" to ch.toString()),
                            )
                            // #endregion
                            break
                        }
                    } finally {
                        node.recycle()
                    }
                }
                if (!done) {
                    AppFileLogger.info("FlowPlayer", "pin_code_slot_miss n=$n")
                    return false
                }
            }
            return wrote == code.length
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    /**
     * Inserisce una cifra nel primo slot `edit1`…`edit6` vuoto.
     *
     * @return `true` se almeno uno slot ha accettato il testo.
     */
    private fun inputPinDigitOnSlots(svc: AccessibilityService, digit: String): Boolean {
        if (digit.isBlank()) return false
        val roots = collectRoots(svc)
        if (roots.isEmpty()) return false
        try {
            for (root in roots) {
                val pkg = root.packageName?.toString() ?: continue
                val slotSnapshot = mutableListOf<String>()
                for (n in 1..8) {
                    val fullId = "$pkg:id/edit$n"
                    val nodes = root.findAccessibilityNodeInfosByViewId(fullId) ?: continue
                    if (nodes.isEmpty()) {
                        nodes.forEach { it.recycle() }
                        continue
                    }
                    val node = AccessibilityNodeInfo.obtain(nodes.first())
                    nodes.forEach { it.recycle() }
                    try {
                        val current = node.text?.toString().orEmpty()
                        slotSnapshot += "e$n:${current.length}"
                        if (current.isNotBlank()) continue
                        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                digit,
                            )
                        }
                        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                            // #region agent log
                            DebugSessionLog.log(
                                "C",
                                "FlowPlayer.inputPinDigitOnSlots",
                                "wrote_slot",
                                mapOf(
                                    "digit" to digit,
                                    "slot" to n,
                                    "slots" to slotSnapshot.joinToString(","),
                                ),
                            )
                            // #endregion
                            return true
                        }
                    } finally {
                        node.recycle()
                    }
                }
                // #region agent log
                DebugSessionLog.log(
                    "C",
                    "FlowPlayer.inputPinDigitOnSlots",
                    "no_empty_slot",
                    mapOf("digit" to digit, "slots" to slotSnapshot.joinToString(","), "pkg" to pkg),
                )
                AppFileLogger.info(
                    "FlowPlayer",
                    "pin_slots_full digit=$digit slots=${slotSnapshot.joinToString(",")}",
                )
                // #endregion
            }
            // Campo PIN singolo (pincode) senza slot editN: append cifra.
            if (appendPinDigitOnPinLikeField(roots, digit)) {
                return true
            }
            return false
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    /**
     * Append su EditText pin-like (es. `pincode`) quando non ci sono slot edit1…editN.
     */
    private fun appendPinDigitOnPinLikeField(roots: List<AccessibilityNodeInfo>, digit: String): Boolean {
        for (root in roots) {
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            collectEditables(root, candidates)
            val pinField = candidates.firstOrNull { node ->
                MaestroSelectorHeuristics.isPinLikeField(node.viewIdResourceName)
            } ?: candidates.firstOrNull { it.isPassword }
            candidates.forEach { if (it !== pinField) it.recycle() }
            val editable = pinField ?: continue
            try {
                val viewId = editable.viewIdResourceName
                val current = editable.text?.toString().orEmpty()
                val next = current + digit
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        next,
                    )
                }
                if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    DebugSessionLog.log(
                        "C",
                        "FlowPlayer.appendPinDigitOnPinLikeField",
                        "appended",
                        mapOf("viewId" to viewId, "len" to next.length),
                    )
                    AppFileLogger.info("FlowPlayer", "pin_field_append id=$viewId len=${next.length}")
                    return true
                }
            } finally {
                editable.recycle()
            }
        }
        return false
    }

    private fun collectEditables(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable) {
            out += AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditables(child, out)
            child.recycle()
        }
    }

    private suspend fun recoverFromAccidentalPickerOverlay(
        svc: AccessibilityService,
        action: RecordedAction.Tap,
    ) {
        val label = action.text ?: return
        if (FieldInputTargetResolver.isPickerOpeningTap(action.viewId, action.text)) return
        if (FieldInputTargetResolver.isPickerListLabel(label, action.contentDescription)) return
        if (!FieldInputTargetResolver.looksLikeFieldLabel(label)) return
        val roots = collectRoots(svc)
        try {
            val overlayOpen = roots.any { FieldInputTargetResolver.isSelectionPickerOverlay(it) }
            if (!overlayOpen) return
        } finally {
            roots.forEach { it.recycle() }
        }
        AppFileLogger.info("FlowPlayer", "field_picker_overlay_recovery label=$label")
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(350)
        val rootsAfter = collectRoots(svc)
        try {
            for (root in rootsAfter) {
                findEditableByHint(root, label)?.let { edit ->
                    try {
                        edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        FieldInputTargetResolver.tapBoundsLeftOfCenter(edit)?.let { (x, y) ->
                            val metrics = context.resources.displayMetrics
                            gestureTap(
                                svc,
                                (x / metrics.widthPixels) * 100f,
                                (y / metrics.heightPixels) * 100f,
                                null,
                                longPress = false,
                            )
                        }
                    } finally {
                        edit.recycle()
                    }
                    return
                }
            }
        } finally {
            rootsAfter.forEach { it.recycle() }
        }
    }

    private suspend fun performTapOnNode(
        svc: AccessibilityService,
        action: RecordedAction.Tap,
        node: AccessibilityNodeInfo,
    ) {
        val resolved = FieldInputTargetResolver.resolveFieldTarget(node)
        val target = resolved.node
        try {
            if (resolved.redirectedFromIcon) {
                AppFileLogger.info(
                    "FlowPlayer",
                    "field_icon_redirect_play icon=${node.viewIdResourceName} edit=${target.viewIdResourceName}",
                )
                if (!target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                FieldInputTargetResolver.tapBoundsLeftOfCenter(target)?.let { (x, y) ->
                    val metrics = context.resources.displayMetrics
                    val px = (x / metrics.widthPixels) * 100f
                    val py = (y / metrics.heightPixels) * 100f
                    gestureTap(svc, px, py, null, longPress = false)
                }
                return
            }
            val vid = target.viewIdResourceName
            val ambiguous = MaestroSelectorHeuristics.isAmbiguousSharedViewId(vid)
            val labelLeaf = !action.text.isNullOrBlank() &&
                (
                    target.text?.toString()?.equals(action.text, ignoreCase = true) == true ||
                        target.contentDescription?.toString()?.equals(action.text, ignoreCase = true) == true
                    )
            val preferGesture = ambiguous || (labelLeaf && !target.isClickable)
            // #region agent log
            DebugSessionLog.log(
                "F2",
                "FlowPlayer.tap",
                if (preferGesture) "gesture_on_label" else "action_click",
                mapOf(
                    "text" to action.text,
                    "nodeViewId" to vid?.substringAfterLast('/'),
                    "ambiguous" to ambiguous,
                    "labelLeaf" to labelLeaf,
                    "clickable" to target.isClickable,
                ),
            )
            // #endregion
            val clicked = if (!preferGesture) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                false
            }
            if (!clicked) {
                clickPointFallback(svc, null, null, target)
                noteDivergence(
                    "tap eseguito via gesture su coordinate (${action.viewId ?: action.text ?: "?"}) invece di " +
                        "ACTION_CLICK → il CLI userebbe tapOn sul selettore",
                )
            }
        } finally {
            target.recycle()
        }
    }

    private suspend fun canResolveTap(svc: AccessibilityService, action: RecordedAction.Tap): Boolean {
        val chain = action.selectorChain.ifEmpty {
            listOf(
                SelectorCandidate(
                    viewId = action.viewId,
                    text = action.text,
                    contentDescription = action.contentDescription,
                    pointPercentX = action.pointPercentX,
                    pointPercentY = action.pointPercentY,
                ),
            ).filterNot { it.isBlank() }
        }
        for (candidate in chain) {
            val hasLogical = !candidate.viewId.isNullOrBlank() ||
                !candidate.text.isNullOrBlank() ||
                !candidate.contentDescription.isNullOrBlank()
            if (!hasLogical) {
                if (candidate.pointPercentX != null && candidate.pointPercentY != null) return true
                continue
            }
            val probe = action.copy(
                viewId = candidate.viewId,
                text = candidate.text,
                contentDescription = candidate.contentDescription,
                selectorChain = emptyList(),
            )
            val node = waitForTapTarget(svc, probe, 800L)
            if (node != null) {
                node.recycle()
                return true
            }
        }
        return false
    }

    /**
     * Poll fino a timeout per trovare il nodo tap (schermata ancora in caricamento).
     */
    private suspend fun waitForTapTarget(
        svc: AccessibilityService,
        action: RecordedAction.Tap,
        timeoutMs: Long,
    ): AccessibilityNodeInfo? {
        val viewId = action.viewId.takeUnless {
            MaestroSelectorHeuristics.isStructuralContainerViewId(it) &&
                (!action.text.isNullOrBlank() || !action.contentDescription.isNullOrBlank())
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = findNode(svc, viewId, action.text, action.contentDescription)
            if (node != null) return node
            delay(250)
        }
        return null
    }

    private suspend fun longPress(action: RecordedAction.LongPress) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        val node = findNode(svc, action.viewId, action.text, action.contentDescription)
        if (node != null) {
            try {
                if (!node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    gestureTap(svc, action.pointPercentX, action.pointPercentY, node, longPress = true)
                }
            } finally {
                node.recycle()
            }
        } else {
            gestureTap(svc, action.pointPercentX, action.pointPercentY, null, longPress = true)
        }
    }

    /**
     * Doppio tap: due click in rapida successione sullo stesso target.
     */
    private suspend fun doubleTap(action: RecordedAction.DoubleTap) {
        val asTap = RecordedAction.Tap(
            packageName = action.packageName,
            viewId = action.viewId,
            text = action.text,
            contentDescription = action.contentDescription,
            pointPercentX = action.pointPercentX,
            pointPercentY = action.pointPercentY,
            timestampMs = action.timestampMs,
        )
        tap(asTap)
        delay(80)
        tap(asTap)
    }

    /**
     * Cancella il testo del campo (SET_TEXT stringa vuota).
     */
    private suspend fun eraseText(action: RecordedAction.EraseText) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        val node = waitForInputTarget(svc, action.viewId, action.packageName, INPUT_FIND_TIMEOUT_MS)
            ?: error("Campo eraseText non trovato (${action.viewId ?: "no-id"})")
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(150)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                error("eraseText SET_TEXT fallito id=${action.viewId}")
            }
        } finally {
            node.recycle()
        }
    }

    /**
     * Swipe lineare tra percentuali schermo (stile Maestro start/end).
     */
    private suspend fun swipe(action: RecordedAction.Swipe) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        val metrics = context.resources.displayMetrics
        val x1 = metrics.widthPixels * action.startPercentX / 100f
        val y1 = metrics.heightPixels * action.startPercentY / 100f
        val x2 = metrics.widthPixels * action.endPercentX / 100f
        val y2 = metrics.heightPixels * action.endPercentY / 100f
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 350L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = suspendCancellableCoroutine { cont ->
            val dispatched = svc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
        if (!ok) error("Swipe fallito")
    }

    /**
     * Mappa tasti Maestro comuni su azioni globali AccessibilityService.
     *
     * Enter non ha equivalente globale affidabile: viene loggato e lo step prosegue
     * (in CI Maestro gestisce IME Enter nativamente).
     */
    private fun pressKey(action: RecordedAction.PressKey) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        when (action.key.trim().lowercase()) {
            "enter", "return" -> {
                AppFileLogger.info("FlowPlayer", "pressKey_enter_skip_no_global_action")
            }
            "back" -> {
                if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                    error("pressKey Back fallito")
                }
            }
            "home" -> {
                if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                    error("pressKey Home fallito")
                }
            }
            "recents", "overview" -> {
                if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) {
                    error("pressKey Recents fallito")
                }
            }
            else -> {
                AppFileLogger.info("FlowPlayer", "pressKey_unsupported key=${action.key}")
            }
        }
    }

    /**
     * Attende che il nodo sia visibile entro timeout, altrimenti errore.
     */
    private suspend fun assertVisible(action: RecordedAction.AssertVisible) {
        val optional = action.executionMode == StepExecutionMode.Optional
        val timeoutMs = if (optional) minOf(action.timeoutMs, 2_000L) else action.timeoutMs
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val svc = service() ?: error("Servizio accessibilità non collegato")
            val node = findNode(svc, action.viewId, action.text, null)
            if (node != null) {
                node.recycle()
                return
            }
            val firstLine = action.text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.length >= 8 }
            if (!firstLine.isNullOrBlank() && firstLine != action.text) {
                val alt = findNode(svc, action.viewId, firstLine, null)
                if (alt != null) {
                    alt.recycle()
                    return
                }
            }
            delay(250)
        }
        if (optional) {
            AppFileLogger.info(
                "FlowPlayer",
                "assertVisible_optional_skip id=${action.viewId} text=${action.text?.take(40)}",
            )
            // #region agent log
            DebugSessionLog.log(
                "F4",
                "FlowPlayer.assertVisible",
                "optional_skip",
                mapOf("text" to action.text?.take(60), "viewId" to action.viewId),
            )
            // #endregion
            return
        }
        error("assertVisible fallito id=${action.viewId} text=${action.text}")
    }

    /**
     * Poll fino a sparizione del nodo (o timeout → errore).
     */
    private suspend fun assertNotVisible(action: RecordedAction.AssertNotVisible) {
        val deadline = System.currentTimeMillis() + action.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val svc = service() ?: error("Servizio accessibilità non collegato")
            val node = findNode(svc, action.viewId, action.text, null)
            if (node == null) return
            node.recycle()
            delay(250)
        }
        error("assertNotVisible fallito id=${action.viewId} text=${action.text}")
    }

    /**
     * Apre un URL via Intent VIEW.
     */
    private fun openLink(url: String) {
        if (url.isBlank()) error("openLink: url vuoto")
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Best-effort stop app: relaunch CLEAR_TOP / NEW_TASK oppure ActivityManager.
     */
    private fun stopApp(packageName: String) {
        if (packageName.isBlank()) error("stopApp: package vuoto")
        runCatching {
            @Suppress("DEPRECATION")
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(packageName)
        }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
            // Non riaprire: CLEAR_TASK da sola non ferma. Solo kill + log.
            AppFileLogger.info("FlowPlayer", "stopApp_best_effort pkg=$packageName")
        } else {
            AppFileLogger.info("FlowPlayer", "stopApp_no_launch_intent pkg=$packageName")
        }
    }

    /**
     * Scroll ripetuti finché il target è visibile o timeout.
     *
     * Bug reale osservato su un flusso AXA registrato: la direzione salvata nello YAML (l'ultima
     * scroll del run collassato da `ScrollCoalescer` in fase di registrazione) può non essere
     * quella che serve a raggiungere il target da uno stato fresco dell'app — es. l'utente aveva
     * corretto una sovra-scrollata durante la registrazione, ma da `launchApp` la lista parte
     * già in cima e la correzione registrata (UP) non porta a nulla. Prima, in quel caso, il
     * player martellava la stessa direzione fallimentare (`scroll_noop` ogni giro) per l'intero
     * timeout — un errore bloccante che interrompeva tutto il resto del flusso.
     */
    private suspend fun scrollUntilVisible(action: RecordedAction.ScrollUntilVisible) {
        if (action.visibleId.isNullOrBlank() && action.visibleText.isNullOrBlank()) {
            error("scrollUntilVisible senza selettore")
        }
        val deadline = System.currentTimeMillis() + action.timeoutMs
        var direction = action.direction
        var noopStreak = 0
        var flipped = false
        while (System.currentTimeMillis() < deadline) {
            val svc = service() ?: error("Servizio accessibilità non collegato")
            val node = findNode(svc, action.visibleId, action.visibleText, null)
            if (node != null) {
                node.recycle()
                if (flipped) {
                    noteDivergence(
                        "scrollUntilVisible su ${action.visibleId ?: action.visibleText} ha invertito " +
                            "la direzione registrata (${action.direction}→$direction) per trovare il " +
                            "target → il CLI userebbe solo la direzione dello YAML e potrebbe fallire",
                    )
                }
                return
            }
            val scrolled = scroll(action.packageName, direction)
            noopStreak = if (scrolled) 0 else noopStreak + 1
            if (noopStreak >= SCROLL_NOOP_FLIP_THRESHOLD) {
                direction = oppositeScrollDirection(direction)
                noopStreak = 0
                flipped = true
            }
            delay(400)
        }
        error(
            "scrollUntilVisible timeout id=${action.visibleId} text=${action.visibleText}",
        )
    }

    private suspend fun inputText(action: RecordedAction.InputText) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        val resolvedText = credentialVault.resolveInput(
            appId = action.packageName,
            text = action.text,
            isPassword = action.isPassword,
            viewId = action.viewId,
        )
        lastStepDataUsed = maskDataForReport(resolvedText, action)
        val node = waitForInputTarget(svc, action.viewId, action.packageName, INPUT_FIND_TIMEOUT_MS)
        // #region agent log
        DebugSessionLog.log(
            "B",
            "FlowPlayer.inputText",
            "target_found",
            mapOf(
                "viewId" to action.viewId,
                "found" to (node != null),
                "editable" to (node?.isEditable),
                "password" to (node?.isPassword),
                "clickable" to (node?.isClickable),
                "className" to (node?.className?.toString()),
                "nodeViewId" to (node?.viewIdResourceName),
                "isPasswordAction" to action.isPassword,
                "textMasked" to (action.text == "****"),
                "textLen" to resolvedText.length,
                "fromVault" to (resolvedText != action.text),
                "isPinSlot" to MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId),
                "step" to currentStepIndex,
            ),
        )
        if (MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId)) {
            AppFileLogger.info(
                "FlowPlayer",
                "pin_slot_input_text id=${action.viewId} len=${resolvedText.length} found=${node != null}",
            )
        }
        // #endregion
        // Slot OTP/PIN: ogni EditText tiene 1 cifra — SET_TEXT del codice intero su edit1
        // lascia solo la prima (evidenza e1:1). Distribuisci su edit1…editN.
        if (MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId) &&
            resolvedText.length in 2..12 &&
            resolvedText.all { it.isDigit() }
        ) {
            node?.recycle()
            val ok = inputPinCodeOnSlots(svc, action.packageName, resolvedText, action.viewId)
            // #region agent log
            DebugSessionLog.log(
                "B",
                "FlowPlayer.inputText",
                "pin_code_distributed",
                mapOf("ok" to ok, "len" to resolvedText.length, "viewId" to action.viewId),
            )
            AppFileLogger.info(
                "FlowPlayer",
                "pin_code_distributed ok=$ok len=${resolvedText.length}",
            )
            // #endregion
            if (!ok) {
                if (action.executionMode == StepExecutionMode.Optional) return
                error("Impossibile inserire PIN/OTP sugli slot (${action.viewId})")
            }
            hideSoftInputBestEffort()
            delay(200)
            return
        }
        if (node == null) {
            if (action.executionMode == StepExecutionMode.Optional) {
                AppFileLogger.info(
                    "FlowPlayer",
                    "skip_optional_input id=${action.viewId} textLen=${action.text.length}",
                )
                return
            }
            error("Campo testo non trovato (${action.viewId ?: "no-id"})")
        }
        try {
            // Molti EditText/Compose richiedono CLICK prima di accettare SET_TEXT.
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            // #region agent log
            DebugSessionLog.log(
                "B",
                "FlowPlayer.inputText",
                "after_click_focus",
                mapOf(
                    "clicked" to clicked,
                    "focused" to focused,
                    "viewId" to action.viewId,
                ),
            )
            // #endregion
            if (!clicked) {
                gestureTapOnNode(svc, node)
                // #region agent log
                DebugSessionLog.log(
                    "B",
                    "FlowPlayer.inputText",
                    "gesture_fallback_done",
                    mapOf("viewId" to action.viewId),
                )
                // #endregion
            }
            delay(250)

            // Skip SET_TEXT se ancora mascherato / placeholder senza vault.
            val stillMasked = resolvedText == "****" ||
                resolvedText == CredentialVault.PLACEHOLDER_PASSWORD ||
                resolvedText == CredentialVault.PLACEHOLDER_PIN
            if (stillMasked) {
                AppFileLogger.info(
                    "FlowPlayer",
                    "password_focused_skip_set_text id=${action.viewId}",
                )
                val secretName = if (resolvedText == CredentialVault.PLACEHOLDER_PIN) "PIN" else "PASSWORD"
                noteDivergence(
                    "segreto \${$secretName} non risolto (${action.viewId ?: "no-id"}) → " +
                        "in CI serve maestro test -e $secretName=...",
                )
                // #region agent log
                DebugSessionLog.log(
                    "C",
                    "FlowPlayer.inputText",
                    "password_skip_set_text",
                    mapOf(
                        "viewId" to action.viewId,
                        "reason" to "text_is_masked_or_placeholder",
                        "isPasswordFlag" to action.isPassword,
                    ),
                )
                // #endregion
                return
            }

            // #region agent log
            DebugSessionLog.log(
                "B",
                "FlowPlayer.inputText",
                "will_set_text",
                mapOf(
                    "viewId" to action.viewId,
                    "isPasswordFlag" to action.isPassword,
                    "textLen" to resolvedText.length,
                    "textMasked" to false,
                    "isPinSlot" to MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId),
                ),
            )
            // #endregion
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, resolvedText)
            }
            val setOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            // #region agent log
            DebugSessionLog.log(
                "B",
                "FlowPlayer.inputText",
                "set_text_primary",
                mapOf(
                    "ok" to setOk,
                    "viewId" to action.viewId,
                    "stillEditable" to node.isEditable,
                    "textLen" to resolvedText.length,
                    "isPinSlot" to MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId),
                ),
            )
            if (MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId)) {
                AppFileLogger.info(
                    "FlowPlayer",
                    "pin_slot_set_text id=${action.viewId} ok=$setOk len=${resolvedText.length}",
                )
            }
            // #endregion
            if (setOk) {
                // Chiudi IME così i tap successivi (es. CONTINUA) non restano coperti.
                hideSoftInputBestEffort()
                delay(400)
                return
            }
            // Fallback: nodo focus input dopo il tap.
            val focusedNode = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            try {
                val fallbackOk = focusedNode != null &&
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                // #region agent log
                DebugSessionLog.log(
                    "E",
                    "FlowPlayer.inputText",
                    "set_text_fallback",
                    mapOf(
                        "ok" to fallbackOk,
                        "focusedFound" to (focusedNode != null),
                        "focusedId" to focusedNode?.viewIdResourceName,
                        "focusedEditable" to focusedNode?.isEditable,
                    ),
                )
                // #endregion
                if (fallbackOk) {
                    hideSoftInputBestEffort()
                    delay(400)
                    return
                }
            } finally {
                focusedNode?.recycle()
            }
            error("SET_TEXT fallito id=${action.viewId}")
        } finally {
            node.recycle()
        }
    }

    /**
     * Solo clear-focus: NON usare BACK (su Samsung naviga indietro e rompe il login).
     */
    private fun hideSoftInputBestEffort() {
        val svc = service() ?: return
        val root = svc.rootInActiveWindow
        try {
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                try {
                    focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                } finally {
                    focused.recycle()
                }
            }
        } finally {
            root?.recycle()
        }
        // #region agent log
        DebugSessionLog.log(
            "P2",
            "FlowPlayer.hideSoftInput",
            "clear_focus_only_no_back",
            mapOf("ok" to true),
        )
        // #endregion
    }

    /**
     * Attende il campo input per viewId (anche se non ancora `isEditable`) o il primo editable.
     */
    private suspend fun waitForInputTarget(
        svc: AccessibilityService,
        viewId: String?,
        packageName: String,
        timeoutMs: Long,
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var scrollAttempts = 0
        while (System.currentTimeMillis() < deadline) {
            val node = findInputTarget(svc, viewId)
            if (node != null) return node
            if (scrollAttempts < 5) {
                scroll(packageName.ifBlank { svc.packageName }, ScrollDirection.DOWN)
                scrollAttempts++
                delay(400)
            } else {
                delay(250)
            }
        }
        return null
    }

    private fun findInputTarget(svc: AccessibilityService, viewId: String?): AccessibilityNodeInfo? {
        val shortWanted = MaestroSelectorHeuristics.shortViewId(viewId)
        val roots = collectRoots(svc)
        if (roots.isEmpty()) return null
        try {
            // viewId esplicito: mai fallback al primo editable (causa PIN → username).
            if (!shortWanted.isNullOrBlank()) {
                for (root in roots) {
                    findByShortId(root, shortWanted)?.let { candidate ->
                        if (idsMatch(shortWanted, candidate.viewIdResourceName)) {
                            return candidate
                        }
                        candidate.recycle()
                    }
                    val candidates = mutableListOf<String>()
                    if (viewId!!.contains(":id/")) candidates += viewId
                    val rootPkg = root.packageName?.toString()
                    if (rootPkg != null) candidates += "$rootPkg:id/$shortWanted"
                    for (full in candidates.distinct()) {
                        val byFull = root.findAccessibilityNodeInfosByViewId(full)
                        if (!byFull.isNullOrEmpty()) {
                            val keep = AccessibilityNodeInfo.obtain(byFull.first())
                            byFull.forEach { it.recycle() }
                            if (idsMatch(shortWanted, keep.viewIdResourceName)) {
                                return keep
                            }
                            keep.recycle()
                        }
                    }
                }
                // #region agent log
                DebugSessionLog.log(
                    "PIN",
                    "FlowPlayer.findInputTarget",
                    "strict_miss_no_fallback",
                    mapOf(
                        "viewIdQuery" to viewId,
                        "shortWanted" to shortWanted,
                        "roots" to roots.size,
                    ),
                )
                // #endregion
                return null
            }
            // Solo senza id: primo editable (step editor grezzi).
            for (root in roots) {
                findFirstEditable(root)?.let { return it }
            }
            return null
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    private fun idsMatch(wantedShort: String, actualViewId: String?): Boolean {
        val actual = MaestroSelectorHeuristics.shortViewId(actualViewId) ?: return false
        return actual.equals(wantedShort, ignoreCase = true)
    }

    private suspend fun gestureTapOnNode(svc: AccessibilityService, node: AccessibilityNodeInfo) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return
        val metrics = context.resources.displayMetrics
        val xPct = (bounds.centerX().toFloat() / metrics.widthPixels) * 100f
        val yPct = (bounds.centerY().toFloat() / metrics.heightPixels) * 100f
        gestureTap(svc, xPct, yPct, null, longPress = false)
    }

    /** @return `true` se lo scroll ha avuto effetto, `false` se noop (nulla da scrollare in quella direzione). */
    private fun scroll(packageName: String, direction: ScrollDirection = ScrollDirection.DOWN): Boolean {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        val root = svc.rootInActiveWindow ?: error("Nessuna finestra attiva")
        try {
            val scrollable = findScrollable(root)
            val actionId = when (direction) {
                ScrollDirection.UP, ScrollDirection.LEFT ->
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                ScrollDirection.DOWN, ScrollDirection.RIGHT ->
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            val scrolled = scrollable != null && scrollable.performAction(actionId)
            if (!scrolled) {
                AppFileLogger.info("FlowPlayer", "scroll_noop pkg=$packageName dir=$direction")
            }
            scrollable?.recycle()
            return scrolled
        } finally {
            root.recycle()
        }
    }

    private suspend fun waitUntil(action: RecordedAction.Wait) {
        val deadline = System.currentTimeMillis() + action.timeoutMs
        if (action.visibleId.isNullOrBlank() && action.visibleText.isNullOrBlank()) {
            // #region agent log
            DebugSessionLog.log(
                "H8",
                "FlowPlayer.waitUntil",
                "timed_wait",
                mapOf("timeoutMs" to action.timeoutMs),
            )
            // #endregion
            // Rispetta il timeout editor (prima era capped a DEFAULT_ANIM_MS → wait + ignorato).
            delay(action.timeoutMs.coerceIn(0L, 60_000L))
            return
        }
        while (System.currentTimeMillis() < deadline) {
            val svc = service() ?: break
            val node = findNode(svc, action.visibleId, action.visibleText, null)
            if (node != null) {
                node.recycle()
                return
            }
            delay(250)
        }
        // Soft-fail: continua il flusso (schermate Compose variabili).
        AppFileLogger.info(
            "FlowPlayer",
            "wait_timeout id=${action.visibleId} text=${action.visibleText}",
        )
        noteDivergence(
            "wait su ${action.visibleId ?: action.visibleText} scaduto in timeout ma proseguito " +
                "(soft-fail) → extendedWaitUntil nel CLI fallisce l'intero flusso",
        )
    }

    private fun findNode(
        svc: AccessibilityService,
        viewId: String?,
        text: String?,
        contentDescription: String?,
    ): AccessibilityNodeInfo? {
        val roots = collectRoots(svc)
        if (roots.isEmpty()) return null
        try {
            for (root in roots) {
                val rootPkg = root.packageName?.toString()
                val shortId = viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                if (shortId != null) {
                    val candidates = mutableListOf<String>()
                    if (viewId!!.contains(":id/")) candidates += viewId
                    if (rootPkg != null) candidates += "$rootPkg:id/$shortId"
                    for (candidate in candidates.distinct()) {
                        val byFull = root.findAccessibilityNodeInfosByViewId(candidate)
                        if (!byFull.isNullOrEmpty()) {
                            val resolved = FieldInputTargetResolver.resolveFieldTarget(byFull.first())
                            byFull.forEach { it.recycle() }
                            return resolved.node
                        }
                    }
                    findByShortId(root, shortId)?.let { found ->
                        val resolved = FieldInputTargetResolver.resolveFieldTarget(found)
                        found.recycle()
                        return resolved.node
                    }
                }
                if (!text.isNullOrBlank()) {
                    if (FieldInputTargetResolver.looksLikePickerListItem(text)) {
                        val pickerRoots = roots.filter { FieldInputTargetResolver.isSelectionPickerOverlay(it) }
                        for (root in pickerRoots) {
                            findClickableByLabel(root, text, allowEditableHint = false)?.let { return it }
                        }
                        for (root in roots) {
                            findClickableByLabel(root, text, allowEditableHint = false)?.let { return it }
                        }
                    } else {
                        for (root in roots) {
                            findClickableByLabel(root, text)?.let { return it }
                            findLabelInTree(root, text)?.let { return it }
                        }
                    }
                }
                if (!contentDescription.isNullOrBlank()) {
                    findByContentDescription(root, contentDescription)?.let { return it }
                }
            }
            // #region agent log
            DebugSessionLog.log(
                "P1",
                "FlowPlayer.findNode",
                "not_found",
                mapOf(
                    "viewId" to viewId,
                    "text" to text,
                    "roots" to roots.size,
                    "rootPkgs" to roots.mapNotNull { it.packageName?.toString() }.joinToString(","),
                ),
            )
            // #endregion
            return null
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    private fun collectRoots(svc: AccessibilityService): List<AccessibilityNodeInfo> {
        val appRoots = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            svc.windows?.forEach { window ->
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return@forEach
                }
                val root = window.root ?: return@forEach
                val pkg = root.packageName?.toString().orEmpty()
                // Finestre app target + dialog permesso/installer; ignora SystemUI / overlay AccessScope.
                val keep = pkg.isNotBlank() &&
                    pkg != context.packageName &&
                    (
                        !MaestroSelectorHeuristics.isForeignUiPackage(pkg) ||
                            MaestroSelectorHeuristics.isCaptureDialogPackage(pkg)
                        )
                if (!keep) {
                    root.recycle()
                    return@forEach
                }
                appRoots += root
            }
        }
        if (appRoots.isEmpty()) {
            svc.rootInActiveWindow?.let { root ->
                val pkg = root.packageName?.toString().orEmpty()
                val keep = pkg.isNotBlank() &&
                    pkg != context.packageName &&
                    (
                        !MaestroSelectorHeuristics.isForeignUiPackage(pkg) ||
                            MaestroSelectorHeuristics.isCaptureDialogPackage(pkg)
                        )
                if (keep) {
                    appRoots += root
                } else {
                    root.recycle()
                }
            }
        }
        return appRoots
    }

    /**
     * Trova nodo cliccabile per testo o contentDescription (bottoni Material/Compose).
     */
    private fun findClickableByLabel(
        root: AccessibilityNodeInfo,
        label: String,
        allowEditableHint: Boolean = true,
    ): AccessibilityNodeInfo? {
        if (allowEditableHint) {
            findEditableByHint(root, label)?.let { return it }
        }
        val list = root.findAccessibilityNodeInfosByText(label)
        try {
            val exact = list?.firstOrNull { node ->
                node.text?.toString().equals(label, ignoreCase = true) == true ||
                    node.contentDescription?.toString().equals(label, ignoreCase = true) == true
            }
            if (exact != null) {
                return climbToClickable(exact, label) ?: AccessibilityNodeInfo.obtain(exact)
            }
            val partial = list?.firstOrNull { node ->
                node.text?.toString()?.contains(label, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(label, ignoreCase = true) == true
            }
            if (partial != null) {
                return climbToClickable(partial, label) ?: AccessibilityNodeInfo.obtain(partial)
            }
        } finally {
            list?.forEach { it.recycle() }
        }
        return null
    }

    /** Scroll nel picker finché la voce lista non è visibile. */
    private suspend fun ensurePickerListItemVisible(svc: AccessibilityService, text: String) {
        repeat(5) {
            if (findNode(svc, null, text, null) != null) return
            val roots = collectRoots(svc).filter { FieldInputTargetResolver.isSelectionPickerOverlay(it) }
            if (roots.isEmpty()) return
            try {
                val pkg = roots.first().packageName?.toString() ?: return
                scroll(pkg, ScrollDirection.DOWN)
            } finally {
                roots.forEach { it.recycle() }
            }
            delay(350)
        }
    }

    /**
     * REC legacy: tap registrato come label campo → apri picker tramite icona sibling.
     */
    private fun tryPickerIconTap(svc: AccessibilityService, action: RecordedAction.Tap): AccessibilityNodeInfo? {
        val label = action.text ?: return null
        if (!FieldInputTargetResolver.looksLikeFieldLabel(label)) return null
        val roots = collectRoots(svc)
        try {
            for (root in roots) {
                val edit = findEditableByHint(root, label) ?: continue
                try {
                    findPickerIconNearEditable(edit)?.let { return it }
                } finally {
                    edit.recycle()
                }
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return null
    }

    private fun findPickerIconNearEditable(edit: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        FieldInputTargetResolver.findPickerIconNearEditable(edit)

    private fun findLabelInTree(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val t = node.text?.toString()
        val cd = node.contentDescription?.toString()
        val hit = t.equals(label, ignoreCase = true) ||
            cd.equals(label, ignoreCase = true) ||
            t?.contains(label, ignoreCase = true) == true ||
            cd?.contains(label, ignoreCase = true) == true
        if (hit) {
            return climbToClickable(node, label) ?: AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findLabelInTree(child, label)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findEditableByHint(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (root.isEditable) {
            val hint = root.hintText?.toString().orEmpty()
            val text = root.text?.toString().orEmpty()
            if (hint.contains(label, ignoreCase = true) || label.contains(hint, ignoreCase = true) ||
                text.contains(label, ignoreCase = true) || label.contains(text, ignoreCase = true)
            ) {
                return AccessibilityNodeInfo.obtain(root)
            }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findEditableByHint(child, label)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Sale al cliccabile più vicino evitando shell strutturali e id condivisi (`header`, …).
     * Per accordion: meglio la foglia testo → gesture sui bounds della riga corretta.
     */
    private fun climbToClickable(node: AccessibilityNodeInfo, label: String? = null): AccessibilityNodeInfo? {
        // Foglia con etichetta esatta: se non è cliccabile, sale a riga cliccabile non ambigua.
        if (!label.isNullOrBlank()) {
            val t = node.text?.toString()
            val cd = node.contentDescription?.toString()
            if (t.equals(label, ignoreCase = true) || cd.equals(label, ignoreCase = true)) {
                if (node.isClickable || node.isCheckable) {
                    // #region agent log
                    DebugSessionLog.log(
                        "F1",
                        "FlowPlayer.climbToClickable",
                        "keep_exact_label_leaf",
                        mapOf(
                            "label" to label,
                            "leafClickable" to true,
                            "viewId" to node.viewIdResourceName?.substringAfterLast('/'),
                        ),
                    )
                    // #endregion
                    return AccessibilityNodeInfo.obtain(node)
                }
            }
        }
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        while (current != null && depth < 6) {
            val c = current!!
            if (c.isClickable || c.isCheckable) {
                val vid = c.viewIdResourceName
                if (!MaestroSelectorHeuristics.isAmbiguousSharedViewId(vid)) {
                    // #region agent log
                    DebugSessionLog.log(
                        "F1",
                        "FlowPlayer.climbToClickable",
                        "accepted_row",
                        mapOf(
                            "label" to label,
                            "viewId" to vid?.substringAfterLast('/'),
                            "nodeText" to c.text?.toString()?.take(40),
                        ),
                    )
                    // #endregion
                    return c
                }
                // #region agent log
                DebugSessionLog.log(
                    "F1",
                    "FlowPlayer.climbToClickable",
                    "skip_ambiguous",
                    mapOf(
                        "label" to label,
                        "viewId" to vid?.substringAfterLast('/'),
                    ),
                )
                // #endregion
            }
            val parent = c.parent
            c.recycle()
            current = parent
            depth++
        }
        current?.recycle()
        // #region agent log
        DebugSessionLog.log(
            "F1",
            "FlowPlayer.climbToClickable",
            "keep_leaf_label",
            mapOf(
                "label" to label,
                "leafText" to node.text?.toString()?.take(40),
                "leafClickable" to node.isClickable,
            ),
        )
        // #endregion
        return AccessibilityNodeInfo.obtain(node)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findByShortId(node: AccessibilityNodeInfo, shortId: String): AccessibilityNodeInfo? {
        val vid = node.viewIdResourceName
        if (vid != null && vid.substringAfterLast('/') == shortId) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByShortId(child, shortId)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findByContentDescription(node: AccessibilityNodeInfo, cd: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == cd) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByContentDescription(child, cd)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private suspend fun clickPointFallback(
        svc: AccessibilityService,
        px: Float?,
        py: Float?,
        node: AccessibilityNodeInfo?,
    ) {
        gestureTap(svc, px, py, node, longPress = false)
    }

    private suspend fun gestureTap(
        svc: AccessibilityService,
        px: Float?,
        py: Float?,
        node: AccessibilityNodeInfo?,
        longPress: Boolean,
    ) {
        val metrics = context.resources.displayMetrics
        val (x, y) = when {
            px != null && py != null ->
                (metrics.widthPixels * px / 100f) to (metrics.heightPixels * py / 100f)
            node != null -> {
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                r.exactCenterX() to r.exactCenterY()
            }
            else -> error("Nessun selettore né coordinate per tap")
        }
        val path = Path().apply { moveTo(x, y) }
        val duration = if (longPress) 600L else 50L
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = suspendCancellableCoroutine { cont ->
            val dispatched = svc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
        if (!ok) error("Gesture tap fallito")
    }

    private fun service(): AccessibilityService? = serviceProvider()

    companion object {
        private const val BETWEEN_STEPS_MS = 400L
        private const val DEFAULT_ANIM_MS = 1_000L
        private const val TAP_FIND_TIMEOUT_MS = 10_000L
        /** Pad custom spesso assente: fail-fast poi fallback slot. */
        private const val PIN_PAD_FIND_TIMEOUT_MS = 400L
        /** Overlay opzionali (Non ora): non bruciare 10s. */
        private const val OPTIONAL_TAP_FIND_TIMEOUT_MS = 1_200L
        /** Timeout più lungo per campi post-loader (PIN dopo CONTINUA). */
        private const val INPUT_FIND_TIMEOUT_MS = 15_000L
        /** Scroll consecutivi senza effetto prima di provare la direzione opposta. */
        private const val SCROLL_NOOP_FLIP_THRESHOLD = 2

        /** Direzione opposta sullo stesso asse (`UP`↔`DOWN`, `LEFT`↔`RIGHT`). */
        internal fun oppositeScrollDirection(direction: ScrollDirection): ScrollDirection = when (direction) {
            ScrollDirection.UP -> ScrollDirection.DOWN
            ScrollDirection.DOWN -> ScrollDirection.UP
            ScrollDirection.LEFT -> ScrollDirection.RIGHT
            ScrollDirection.RIGHT -> ScrollDirection.LEFT
        }
    }
}
