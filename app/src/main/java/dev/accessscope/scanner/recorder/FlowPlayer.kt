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
) {

    /**
     * Riproduce le azioni.
     *
     * @param actions Lista da `actions.json`.
     * @param clearState Se true, stopApp + tentativo clear + cold launch prima del flusso.
     * @param onStep Callback (indice 0-based, totale) a ogni step.
     * @return Messaggio errore o `null` se ok.
     */
    suspend fun play(
        actions: List<RecordedAction>,
        clearState: Boolean = false,
        onStep: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): String? {
        if (actions.isEmpty()) return "Flusso vuoto"
        val appId = actions.firstOrNull()?.packageName?.takeIf { it.isNotBlank() }
            ?: actions.filterIsInstance<RecordedAction.LaunchApp>().firstOrNull()?.packageName
        if (clearState && !appId.isNullOrBlank()) {
            val prep = prepareCleanLaunch(appId)
            if (prep != null) {
                AppFileLogger.info("FlowPlayer", "clearState_note $prep")
            }
        }
        val total = actions.size
        for ((index, action) in actions.withIndex()) {
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
                when {
                    // Con clear già fatto, launchApp successivo usa cold start.
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
                    continue
                }
                AppFileLogger.info("FlowPlayer", "step_fail i=$index err=$err")
                return "Step ${index + 1}: $err"
            }
            delay(BETWEEN_STEPS_MS)
        }
        return null
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
            else -> false
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
            is RecordedAction.WaitForAnimation -> {
                delay(action.timeoutMs?.coerceAtMost(5_000L) ?: DEFAULT_ANIM_MS)
            }
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
        val node = waitForTapTarget(svc, action, TAP_FIND_TIMEOUT_MS)
        // #region agent log
        DebugSessionLog.log(
            "P1",
            "FlowPlayer.tap",
            "tap_target",
            mapOf(
                "viewId" to action.viewId,
                "text" to action.text,
                "found" to (node != null),
                "nodeText" to node?.text?.toString(),
                "clickable" to node?.isClickable,
                "hasPoint" to (action.pointPercentX != null),
            ),
        )
        // #endregion
        if (node != null) {
            try {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                    clickPointFallback(svc, action.pointPercentX, action.pointPercentY, node)
                }
            } finally {
                node.recycle()
            }
        } else {
            // Senza nodo: point solo se presente; altrimenti errore (non “tap fantasma”).
            if (action.pointPercentX == null || action.pointPercentY == null) {
                error("Tap non trovato id=${action.viewId} text=${action.text}")
            }
            clickPointFallback(svc, action.pointPercentX, action.pointPercentY, null)
        }
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
        val node = waitForInputTarget(svc, action.viewId, INPUT_FIND_TIMEOUT_MS)
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
        val deadline = System.currentTimeMillis() + action.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val svc = service() ?: error("Servizio accessibilità non collegato")
            val node = findNode(svc, action.viewId, action.text, null)
            if (node != null) {
                node.recycle()
                return
            }
            delay(250)
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
     */
    private suspend fun scrollUntilVisible(action: RecordedAction.ScrollUntilVisible) {
        if (action.visibleId.isNullOrBlank() && action.visibleText.isNullOrBlank()) {
            error("scrollUntilVisible senza selettore")
        }
        val deadline = System.currentTimeMillis() + action.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val svc = service() ?: error("Servizio accessibilità non collegato")
            val node = findNode(svc, action.visibleId, action.visibleText, null)
            if (node != null) {
                node.recycle()
                return
            }
            scroll(action.packageName, action.direction)
            delay(400)
        }
        error(
            "scrollUntilVisible timeout id=${action.visibleId} text=${action.visibleText}",
        )
    }

    private suspend fun inputText(action: RecordedAction.InputText) {
        val svc = service() ?: error("Servizio accessibilità non collegato")
        val node = waitForInputTarget(svc, action.viewId, INPUT_FIND_TIMEOUT_MS)
        // #region agent log
        DebugSessionLog.log(
            "A",
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
                "textLen" to action.text.length,
            ),
        )
        // #endregion
        if (node == null) error("Campo testo non trovato (${action.viewId ?: "no-id"})")
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

            // Skip SET_TEXT solo se il testo è ancora mascherato.
            // Se l’utente ha messo una password reale in editor/actions.json, va scritta
            // anche se in registrazione isPassword=true.
            val masked = action.text == "****"
            if (masked) {
                AppFileLogger.info(
                    "FlowPlayer",
                    "password_focused_skip_set_text id=${action.viewId}",
                )
                // #region agent log
                DebugSessionLog.log(
                    "C",
                    "FlowPlayer.inputText",
                    "password_skip_set_text",
                    mapOf(
                        "viewId" to action.viewId,
                        "reason" to "text_is_masked_stars",
                        "isPasswordFlag" to action.isPassword,
                    ),
                )
                // #endregion
                return
            }

            // #region agent log
            DebugSessionLog.log(
                "F",
                "FlowPlayer.inputText",
                "will_set_text",
                mapOf(
                    "viewId" to action.viewId,
                    "isPasswordFlag" to action.isPassword,
                    "textLen" to action.text.length,
                    "textMasked" to false,
                ),
            )
            // #endregion
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
            }
            val setOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            // #region agent log
            DebugSessionLog.log(
                "C",
                "FlowPlayer.inputText",
                "set_text_primary",
                mapOf(
                    "ok" to setOk,
                    "viewId" to action.viewId,
                    "stillEditable" to node.isEditable,
                ),
            )
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
        timeoutMs: Long,
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = findInputTarget(svc, viewId)
            if (node != null) return node
            delay(250)
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

    private fun scroll(packageName: String, direction: ScrollDirection = ScrollDirection.DOWN) {
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
            if (scrollable == null || !scrollable.performAction(actionId)) {
                AppFileLogger.info("FlowPlayer", "scroll_noop pkg=$packageName dir=$direction")
            }
            scrollable?.recycle()
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
                            val keep = AccessibilityNodeInfo.obtain(byFull.first())
                            byFull.forEach { it.recycle() }
                            return keep
                        }
                    }
                    findByShortId(root, shortId)?.let { return it }
                }
                if (!text.isNullOrBlank()) {
                    findClickableByLabel(root, text)?.let { return it }
                    findLabelInTree(root, text)?.let { return it }
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
                // Solo finestre app target: ignora SystemUI / overlay AccessScope.
                if (pkg.isBlank() ||
                    MaestroSelectorHeuristics.isForeignUiPackage(pkg) ||
                    pkg == context.packageName
                ) {
                    root.recycle()
                    return@forEach
                }
                appRoots += root
            }
        }
        if (appRoots.isEmpty()) {
            svc.rootInActiveWindow?.let { root ->
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg.isNotBlank() &&
                    !MaestroSelectorHeuristics.isForeignUiPackage(pkg) &&
                    pkg != context.packageName
                ) {
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
    private fun findClickableByLabel(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByText(label)
        try {
            val exact = list?.firstOrNull { node ->
                node.text?.toString().equals(label, ignoreCase = true) == true ||
                    node.contentDescription?.toString().equals(label, ignoreCase = true) == true
            }
            if (exact != null) {
                val clickable = climbToClickable(exact) ?: exact
                return AccessibilityNodeInfo.obtain(clickable)
            }
            val partial = list?.firstOrNull { node ->
                node.text?.toString()?.contains(label, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(label, ignoreCase = true) == true
            }
            if (partial != null) {
                val clickable = climbToClickable(partial) ?: partial
                return AccessibilityNodeInfo.obtain(clickable)
            }
        } finally {
            list?.forEach { it.recycle() }
        }
        return null
    }

    private fun findLabelInTree(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val t = node.text?.toString()
        val cd = node.contentDescription?.toString()
        val hit = t.equals(label, ignoreCase = true) ||
            cd.equals(label, ignoreCase = true) ||
            t?.contains(label, ignoreCase = true) == true ||
            cd?.contains(label, ignoreCase = true) == true
        if (hit) {
            val clickable = climbToClickable(node) ?: node
            return AccessibilityNodeInfo.obtain(clickable)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findLabelInTree(child, label)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun climbToClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        repeat(6) {
            val c = current ?: return null
            if (c.isClickable || c.isCheckable) return c
            val parent = c.parent
            c.recycle()
            current = parent
        }
        current?.recycle()
        return null
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
        /** Timeout più lungo per campi post-loader (PIN dopo CONTINUA). */
        private const val INPUT_FIND_TIMEOUT_MS = 15_000L
    }
}
