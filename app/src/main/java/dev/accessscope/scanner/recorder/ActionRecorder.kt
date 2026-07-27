/**
 * Conversione eventi AccessibilityEvent in [RecordedAction] (Maestro Beta).
 */
package dev.accessscope.scanner.recorder

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugSessionLog

/**
 * Fornisce la root della finestra attiva per risolvere nodi quando `event.source` è null.
 */
fun interface AccessibilityRootProvider {
    /** @return Root corrente o `null` se non disponibile. */
    fun root(): AccessibilityNodeInfo?
}

/**
 * Costruisce azioni Maestro a partire dagli eventi di accessibilità.
 *
 * Preferisce `resource-id`, poi testo/contentDescription, infine coordinate percentuali.
 * Se `event.source` manca, tenta focus input / accessibility focus sulla root.
 * Coalescia input testo e scroll ripetuti; password → `****`.
 */
class ActionRecorder {

    private var lastTapKey: String? = null
    private var lastTapAtMs: Long = 0L
    private var pendingText: PendingText? = null
    private var lastScrollAtMs: Long = 0L

    private data class PendingText(
        val packageName: String,
        val viewId: String?,
        val text: String,
        val isPassword: Boolean,
        val timestampMs: Long,
    )

    /**
     * Interpreta un evento e restituisce zero o più azioni da appendere al flusso.
     *
     * @param event Evento di sistema.
     * @param screenWidthPx Larghezza display per calcolo point %.
     * @param screenHeightPx Altezza display per calcolo point %.
     * @param rootProvider Root finestra attiva (fallback se source null).
     */
    fun onEvent(
        event: AccessibilityEvent,
        screenWidthPx: Int,
        screenHeightPx: Int,
        rootProvider: AccessibilityRootProvider = AccessibilityRootProvider { null },
    ): List<RecordedAction> {
        val packageName = event.packageName?.toString() ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = mutableListOf<RecordedAction>()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                flushPendingText()?.let(out::add)
                val tap = buildTap(
                    event = event,
                    packageName = packageName,
                    now = now,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    longPress = false,
                    rootProvider = rootProvider,
                )
                if (tap == null) {
                    logDiscard(event.eventType, packageName, "tap_unresolved")
                    // #region agent log
                    DebugSessionLog.log(
                        "R1",
                        "ActionRecorder.onEvent",
                        "tap_discarded",
                        mapOf(
                            "reason" to "tap_unresolved",
                            "pkg" to packageName,
                            "eventText" to event.text?.firstOrNull()?.toString(),
                            "eventCd" to event.contentDescription?.toString(),
                            "hasSource" to (runCatching { event.source != null }.getOrDefault(false)),
                        ),
                    )
                    // #endregion
                } else {
                    val key = tapKey(tap)
                    if (key == lastTapKey && now - lastTapAtMs < TAP_DEBOUNCE_MS) {
                        // #region agent log
                        DebugSessionLog.log(
                            "R2",
                            "ActionRecorder.onEvent",
                            "tap_debounced",
                            mapOf("key" to key, "text" to (tap as? RecordedAction.Tap)?.text),
                        )
                        // #endregion
                        return out
                    }
                    lastTapKey = key
                    lastTapAtMs = now
                    out += tap
                    // #region agent log
                    DebugSessionLog.log(
                        "R1",
                        "ActionRecorder.onEvent",
                        "tap_recorded",
                        mapOf(
                            "text" to (tap as? RecordedAction.Tap)?.text,
                            "viewId" to (tap as? RecordedAction.Tap)?.viewId,
                            "hasPoint" to ((tap as? RecordedAction.Tap)?.pointPercentX != null),
                        ),
                    )
                    // #endregion
                }
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                flushPendingText()?.let(out::add)
                val press = buildTap(
                    event = event,
                    packageName = packageName,
                    now = now,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    longPress = true,
                    rootProvider = rootProvider,
                )
                if (press == null) {
                    logDiscard(event.eventType, packageName, "longpress_unresolved")
                } else {
                    out += press
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                out += updatePendingText(event, packageName, now, rootProvider)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val resolved = resolveNode(event, rootProvider)
                val editable = resolved.node?.isEditable == true
                val focusViewId = resolved.node?.viewIdResourceName
                resolved.recycleIfNeeded()
                if (editable) {
                    // PIN + conferma: nuovo focus su editabile → flush campo precedente.
                    if (pendingText != null) {
                        // #region agent log
                        DebugSessionLog.log(
                            "H4",
                            "ActionRecorder.onEvent",
                            "flush_on_editable_focus",
                            mapOf(
                                "focusViewId" to focusViewId,
                                "pendingViewId" to pendingText?.viewId,
                                "pendingPwd" to pendingText?.isPassword,
                            ),
                        )
                        // #endregion
                        flushPendingText()?.let(out::add)
                    }
                    out += updatePendingText(event, packageName, now, rootProvider)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // #region agent log
                if (pendingText != null) {
                    DebugSessionLog.log(
                        "H5",
                        "ActionRecorder.onEvent",
                        "flush_on_window_state",
                        mapOf(
                            "pkg" to packageName,
                            "pendingViewId" to pendingText?.viewId,
                            "pendingPwd" to pendingText?.isPassword,
                        ),
                    )
                }
                // #endregion
                flushPendingText()?.let(out::add)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (now - lastScrollAtMs < SCROLL_DEBOUNCE_MS) return out
                lastScrollAtMs = now
                flushPendingText()?.let(out::add)
                out += RecordedAction.Scroll(
                    packageName = packageName,
                    direction = ScrollDirection.DOWN,
                    timestampMs = now,
                )
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                flushPendingText()?.let(out::add)
                buildTap(
                    event = event,
                    packageName = packageName,
                    now = now,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    longPress = false,
                    rootProvider = rootProvider,
                )?.let { tap ->
                    val key = tapKey(tap)
                    if (key == lastTapKey && now - lastTapAtMs < TAP_DEBOUNCE_MS) return out
                    lastTapKey = key
                    lastTapAtMs = now
                    out += tap
                }
            }
        }
        return out
    }

    /** Scarica eventuale testo in attesa (es. a fine registrazione). */
    fun flush(): List<RecordedAction> = listOfNotNull(flushPendingText())

    fun reset() {
        lastTapKey = null
        lastTapAtMs = 0L
        pendingText = null
        lastScrollAtMs = 0L
    }

    /**
     * Aggiorna il buffer testo; se cambia campo, flush del precedente (PIN + conferma).
     *
     * @return Azioni da appendere (0 o 1 InputText flushato).
     */
    private fun updatePendingText(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        rootProvider: AccessibilityRootProvider,
    ): List<RecordedAction> {
        val resolved = resolveNode(event, rootProvider)
        val node = resolved.node
        val isPassword = node?.isPassword == true
        val text = event.text?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: node?.text?.toString()?.takeIf { it.isNotBlank() }
        val viewId = node?.viewIdResourceName?.takeIf { it.isNotBlank() }
        resolved.recycleIfNeeded()
        val pinLike = MaestroSelectorHeuristics.isPinLikeField(viewId)
        val loginPassword = MaestroSelectorHeuristics.isLoginPasswordField(viewId, isPassword)
        // Campo svuotato: flush pending (PIN conferma / nuovo ciclo).
        if (text.isNullOrBlank()) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                if (pendingText != null) {
                    return listOfNotNull(flushPendingText())
                }
                logDiscard(event.eventType, packageName, "text_blank")
            }
            return emptyList()
        }

        val flushed = mutableListOf<RecordedAction>()
        val pending = pendingText
        val storedText = if (isPassword) "****" else text.take(200)
        val reason = when {
            pending != null && !samePendingField(pending.viewId, viewId) -> "field_change"
            // Password login: NON trattare ****==**** come re-entry (evita N duplicati).
            loginPassword -> null
            // PIN: stesso testo o accorciato dopo pausa → nuovo inserimento.
            pending != null &&
                pinLike &&
                samePendingField(pending.viewId, viewId) &&
                (pending.text == storedText || storedText.length < pending.text.length || storedText.length <= 1) &&
                now - pending.timestampMs >= 800L -> "pin_reentry"
            pending != null &&
                !loginPassword &&
                pending.text == storedText &&
                now - pending.timestampMs >= 1_500L &&
                samePendingField(pending.viewId, viewId) -> "reentry_gap"
            else -> null
        }
        if (reason != null) {
            // #region agent log
            DebugSessionLog.log(
                "H4",
                "ActionRecorder.updatePendingText",
                "flush_pending_before_update",
                mapOf(
                    "reason" to reason,
                    "pendingViewId" to pending?.viewId,
                    "newViewId" to viewId,
                    "isPassword" to isPassword,
                    "pinLike" to pinLike,
                    "gapMs" to (pending?.let { now - it.timestampMs }),
                ),
            )
            // #endregion
            flushPendingText()?.let(flushed::add)
        }

        // Password: aggiorna pending senza cambiare timestamp a ogni keystroke (evita gap spurî).
        val ts = if (loginPassword && pending != null && samePendingField(pending.viewId, viewId)) {
            pending.timestampMs
        } else {
            now
        }
        pendingText = PendingText(
            packageName = packageName,
            viewId = viewId,
            text = storedText,
            isPassword = isPassword,
            timestampMs = ts,
        )
        return flushed
    }

    private fun samePendingField(a: String?, b: String?): Boolean {
        val idA = MaestroSelectorHeuristics.shortViewId(a)
        val idB = MaestroSelectorHeuristics.shortViewId(b)
        return when {
            !idA.isNullOrBlank() && !idB.isNullOrBlank() -> idA == idB
            idA.isNullOrBlank() && idB.isNullOrBlank() -> true
            else -> false
        }
    }

    private fun flushPendingText(): RecordedAction.InputText? {
        val pending = pendingText ?: return null
        pendingText = null
        return RecordedAction.InputText(
            packageName = pending.packageName,
            text = pending.text,
            viewId = pending.viewId,
            isPassword = pending.isPassword,
            timestampMs = pending.timestampMs,
        )
    }

    private fun buildTap(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        screenWidthPx: Int,
        screenHeightPx: Int,
        longPress: Boolean,
        rootProvider: AccessibilityRootProvider,
    ): RecordedAction? {
        val resolved = resolveNode(event, rootProvider)
        val node = resolved.node
        if (node?.isEditable == true && !longPress) {
            resolved.recycleIfNeeded()
            logDiscard(event.eventType, packageName, "editable_skip_tap")
            // #region agent log
            DebugSessionLog.log(
                "R1",
                "ActionRecorder.buildTap",
                "editable_skip_tap",
                mapOf(
                    "pkg" to packageName,
                    "viewId" to node.viewIdResourceName,
                    "eventText" to event.text?.firstOrNull()?.toString(),
                ),
            )
            // #endregion
            return null
        }

        // Salita verso parent cliccabile: il testo "CONTINUA" è spesso sul TextView figlio
        // mentre l’id sta sul Button/parent (id-first per Play stabile).
        val identity = resolveTapIdentity(node, event)
        val viewId = identity.viewId
        val text = identity.text
        val cd = identity.contentDescription

        if (MaestroSelectorHeuristics.isSystemChromeTap(packageName, viewId, text, cd) ||
            MaestroSelectorHeuristics.isForeignUiPackage(packageName)
        ) {
            resolved.recycleIfNeeded()
            logDiscard(event.eventType, packageName, "system_chrome_skip")
            return null
        }
        if (MaestroSelectorHeuristics.isNoiseViewId(viewId)) {
            resolved.recycleIfNeeded()
            logDiscard(event.eventType, packageName, "noise_view_id")
            return null
        }

        var px: Float? = null
        var py: Float? = null
        if (node != null && screenWidthPx > 0 && screenHeightPx > 0) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                px = ((bounds.centerX().toFloat() / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((bounds.centerY().toFloat() / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        }
        // Preferisci bounds del parent cliccabile se disponibile.
        identity.clickableBounds?.let { b ->
            if (screenWidthPx > 0 && screenHeightPx > 0 && !b.isEmpty) {
                px = ((b.centerX().toFloat() / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((b.centerY().toFloat() / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        }
        resolved.recycleIfNeeded()

        if (viewId == null && text == null && cd == null && (px == null || py == null)) return null

        // #region agent log
        DebugSessionLog.log(
            "R3",
            "ActionRecorder.buildTap",
            "tap_identity",
            mapOf(
                "viewId" to viewId,
                "text" to text,
                "hasPoint" to (px != null),
            ),
        )
        // #endregion

        return if (longPress) {
            RecordedAction.LongPress(
                packageName = packageName,
                viewId = viewId,
                text = text?.take(80),
                contentDescription = cd?.take(80),
                pointPercentX = px,
                pointPercentY = py,
                timestampMs = now,
            )
        } else {
            RecordedAction.Tap(
                packageName = packageName,
                viewId = viewId,
                text = text?.take(80),
                contentDescription = cd?.take(80),
                pointPercentX = px,
                pointPercentY = py,
                timestampMs = now,
            )
        }
    }

    /**
     * Estrae id/testo migliori: sale ai parent cliccabili dove di solito c’è resource-id.
     */
    private fun resolveTapIdentity(
        node: AccessibilityNodeInfo?,
        event: AccessibilityEvent,
    ): TapIdentity {
        var bestId: String? = null
        var clickableId: String? = null
        var bestText: String? = event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
        var bestCd: String? = event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
        var clickableBounds: Rect? = null

        if (node == null) {
            return TapIdentity(bestId, bestText, bestCd, null)
        }

        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        repeat(8) {
            val c = current ?: return@repeat
            val id = c.viewIdResourceName?.takeIf { it.isNotBlank() }
            if (id != null) {
                if (bestId == null) bestId = id
                if (c.isClickable || c.isCheckable) {
                    clickableId = id
                    val b = Rect()
                    c.getBoundsInScreen(b)
                    if (!b.isEmpty) clickableBounds = Rect(b)
                }
            }
            val t = c.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val cd = c.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
            if (bestText == null) bestText = t
            if (bestCd == null) bestCd = cd
            if (clickableBounds == null && (c.isClickable || c.isCheckable)) {
                val b = Rect()
                c.getBoundsInScreen(b)
                if (!b.isEmpty) clickableBounds = Rect(b)
            }
            val parent = c.parent
            c.recycle()
            current = parent
        }
        current?.recycle()

        return TapIdentity(
            viewId = clickableId ?: bestId,
            text = bestText,
            contentDescription = bestCd,
            clickableBounds = clickableBounds,
        )
    }

    private data class TapIdentity(
        val viewId: String?,
        val text: String?,
        val contentDescription: String?,
        val clickableBounds: Rect?,
    )

    /**
     * Risolve il nodo: source evento → testo evento sulla root → accessibility focus.
     * Non usa FOCUS_INPUT (campo password ancora focusato → scarta CONTINUA come editable).
     */
    private fun resolveNode(
        event: AccessibilityEvent,
        rootProvider: AccessibilityRootProvider,
    ): ResolvedNode {
        val source = runCatching { event.source }.getOrNull()
        if (source != null) return ResolvedNode(source, owned = true)

        val root = rootProvider.root() ?: return ResolvedNode(null, owned = false)
        val eventLabel = event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }

        if (!eventLabel.isNullOrBlank()) {
            val list = runCatching { root.findAccessibilityNodeInfosByText(eventLabel) }.getOrNull()
            val match = list?.firstOrNull { node ->
                val t = node.text?.toString()
                val cd = node.contentDescription?.toString()
                t.equals(eventLabel, ignoreCase = true) ||
                    cd.equals(eventLabel, ignoreCase = true) ||
                    (node.isClickable && (
                        t?.contains(eventLabel, ignoreCase = true) == true ||
                            cd?.contains(eventLabel, ignoreCase = true) == true
                        ))
            }
            if (match != null) {
                val keep = AccessibilityNodeInfo.obtain(match)
                list.forEach { it.recycle() }
                root.recycle()
                // #region agent log
                DebugSessionLog.log(
                    "R1",
                    "ActionRecorder.resolveNode",
                    "resolved_by_event_text",
                    mapOf("label" to eventLabel, "viewId" to keep.viewIdResourceName),
                )
                // #endregion
                return ResolvedNode(keep, owned = true)
            }
            list?.forEach { it.recycle() }
        }

        val a11yFocus = runCatching {
            root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        }.getOrNull()
        if (a11yFocus != null) {
            root.recycle()
            return ResolvedNode(a11yFocus, owned = true)
        }
        root.recycle()
        return ResolvedNode(null, owned = false)
    }

    private fun logDiscard(eventType: Int, packageName: String, reason: String) {
        AppFileLogger.info(
            "ActionRecorder",
            "discard type=$eventType pkg=$packageName reason=$reason",
        )
    }

    private fun tapKey(action: RecordedAction): String = when (action) {
        is RecordedAction.Tap ->
            "${action.viewId}|${action.text}|${action.contentDescription}|${action.pointPercentX}"
        is RecordedAction.LongPress ->
            "L|${action.viewId}|${action.text}|${action.contentDescription}|${action.pointPercentX}"
        else -> action.toString()
    }

    private data class ResolvedNode(
        val node: AccessibilityNodeInfo?,
        val owned: Boolean,
    ) {
        fun recycleIfNeeded() {
            if (owned) node?.recycle()
        }
    }

    companion object {
        private const val TAP_DEBOUNCE_MS = 150L
        private const val SCROLL_DEBOUNCE_MS = 600L
    }
}
