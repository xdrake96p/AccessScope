/**
 * Coordinatore sessione di registrazione Maestro (Beta).
 */
package dev.accessscope.scanner.recorder

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.telemetry.RecordingTelemetry
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugSessionLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stato globale della registrazione: mutex con lo scan WCAG (gestito dal chiamante).
 *
 * @param appContext Context applicazione per anteprima YAML live sull’overlay.
 */
class RecordingSessionController(
    private val appContext: Context? = null,
) {

    private val recorder = ActionRecorder()
    private val telemetryCollector = RecordingTelemetry()
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    val isRecording: Boolean get() = _state.value.isRecording

    /** Tipo azione da creare in modalità PICK. */
    @Volatile
    var pickActionKind: PickActionKind = PickActionKind.TAP

    /** Step reali (escluso LaunchApp sintetico). */
    fun realStepCount(actions: List<RecordedAction> = _state.value.actions): Int =
        actions.count { it !is RecordedAction.LaunchApp }

    /**
     * Avvia la registrazione per il package indicato.
     *
     * @return `false` se già in registrazione.
     */
    fun start(targetPackage: String, targetLabel: String): Boolean {
        if (_state.value.isRecording) return false
        recorder.reset()
        telemetryCollector.reset()
        pickActionKind = PickActionKind.TAP
        val launch = RecordedAction.LaunchApp(packageName = targetPackage)
        _state.value = RecordingState(
            isRecording = true,
            targetPackage = targetPackage,
            targetLabel = targetLabel,
            actions = listOf(launch),
            statusMessage = null,
            pickMode = false,
            isPaused = false,
        )
        publishPreview()
        AppFileLogger.info("RecordingSession", "start pkg=$targetPackage")
        return true
    }

    /**
     * Ferma la registrazione e flush del testo pendente.
     *
     * @return Azioni finali (copia).
     */
    fun stop(): List<RecordedAction> {
        val flushed = recorder.flush()
        val finalActions = _state.value.actions + flushed
        val real = realStepCount(finalActions)
        _state.update {
            it.copy(
                isRecording = false,
                actions = finalActions,
                pickMode = false,
                isPaused = false,
                statusMessage = if (real == 0) {
                    "Nessun tap/testo catturato. Disattiva e riattiva AccessScope in Accessibilità, poi riprova."
                } else {
                    "Registrazione terminata ($real step)"
                },
            )
        }
        AppFileLogger.info("RecordingSession", "stop realSteps=$real total=${finalActions.size}")
        recorder.reset()
        telemetryCollector.reset()
        return finalActions
    }

    /** Annulla senza salvare. */
    fun cancel() {
        recorder.reset()
        telemetryCollector.reset()
        _state.value = RecordingState()
    }

    /**
     * Pausa/riprende la cattura automatica (PICK e append manuale restano disponibili).
     */
    fun setPaused(paused: Boolean) {
        if (!_state.value.isRecording) return
        _state.update {
            it.copy(
                isPaused = paused,
                pickMode = if (paused) false else it.pickMode,
            )
        }
        publishPreview()
        AppFileLogger.info("RecordingSession", if (paused) "paused" else "resumed")
    }

    /**
     * Attiva/disattiva modalità PICK (prossimo tap → step esplicito).
     */
    fun setPickMode(enabled: Boolean) {
        if (!_state.value.isRecording) return
        _state.update {
            it.copy(
                pickMode = enabled,
                // PICK mentre in pausa: ok; uscita pausa non obbligatoria.
            )
        }
        publishPreview()
    }

    /**
     * Aggiunge un’azione manuale (PICK / editor runtime) anche in pausa.
     */
    fun appendManual(action: RecordedAction) {
        if (!_state.value.isRecording) return
        _state.update { it.copy(actions = it.actions + action, pickMode = false) }
        publishPreview()
        AppFileLogger.info("RecordingSession", "manual_add type=${action::class.simpleName}")
    }

    /**
     * Rimuove l’ultimo step non-LaunchApp (undo mid-REC).
     * @return `true` se rimosso.
     */
    fun undoLastStep(): Boolean {
        if (!_state.value.isRecording) return false
        val actions = _state.value.actions
        val idx = actions.indexOfLast { it !is RecordedAction.LaunchApp }
        if (idx < 0) return false
        _state.update {
            it.copy(actions = it.actions.toMutableList().also { list -> list.removeAt(idx) })
        }
        publishPreview()
        return true
    }

    /**
     * Segna l’ultimo Tap come optional (popup dismiss mid-REC).
     * @return `true` se aggiornato.
     */
    fun markLastTapOptional(): Boolean {
        if (!_state.value.isRecording) return false
        val actions = _state.value.actions.toMutableList()
        val idx = actions.indexOfLast { it is RecordedAction.Tap }
        if (idx < 0) return false
        val tap = actions[idx] as RecordedAction.Tap
        actions[idx] = tap.copy(
            executionMode = dev.accessscope.scanner.recorder.model.StepExecutionMode.Optional,
        )
        _state.update { it.copy(actions = actions) }
        publishPreview()
        return true
    }

    /**
     * Telemetria raccolta durante la registrazione (snapshot + transizioni).
     */
    fun buildTelemetry(): FlowTelemetry {
        val timestamps = _state.value.actions.map { it.timestampMs }
        return telemetryCollector.build(timestamps)
    }

    /**
     * Inoltra un evento a11y se la registrazione è attiva e il package coincide (o root target).
     */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        screenWidthPx: Int,
        screenHeightPx: Int,
        rootProvider: AccessibilityRootProvider = AccessibilityRootProvider { null },
    ) {
        val current = _state.value
        if (!current.isRecording) return
        val target = current.targetPackage ?: return
        val pkg = event.packageName?.toString() ?: return
        val isClick = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        val isWindow = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val relevant = isRelevantPackage(pkg, target, rootProvider)
        // #region agent log
        if (isClick || isWindow) {
            DebugSessionLog.log(
                if (!relevant) "A" else "A",
                "RecordingSession.onAccessibilityEvent",
                if (!relevant) "event_skipped_pkg" else "event_accepted",
                mapOf(
                    "type" to event.eventType,
                    "pkg" to pkg,
                    "target" to target,
                    "relevant" to relevant,
                    "paused" to current.isPaused,
                    "eventText" to event.text?.joinToString("|") { it?.toString().orEmpty() }?.take(120),
                    "className" to event.className?.toString()?.substringAfterLast('.')?.take(40),
                ),
            )
        }
        // #endregion
        if (!relevant) {
            val interesting = isClick ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
            if (interesting) {
                AppFileLogger.info(
                    "RecordingSession",
                    "skip_pkg eventPkg=$pkg target=$target type=${event.eventType}",
                )
            }
            return
        }

        // In pausa: solo PICK esplicito registra; niente cattura automatica.
        if (current.isPaused && !(current.pickMode && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)) {
            // #region agent log
            if (isClick) {
                DebugSessionLog.log(
                    "A",
                    "RecordingSession.onAccessibilityEvent",
                    "event_skipped_paused",
                    mapOf("pkg" to pkg, "type" to event.eventType),
                )
            }
            // #endregion
            return
        }

        if (current.pickMode && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handlePickClick(event, screenWidthPx, screenHeightPx, rootProvider, target)
            return
        }

        val newActions = recorder.onEvent(event, screenWidthPx, screenHeightPx, rootProvider)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            telemetryCollector.onContentChanged(System.currentTimeMillis())
        }
        // #region agent log
        if (isClick || isWindow) {
            DebugSessionLog.log(
                "B",
                "RecordingSession.onAccessibilityEvent",
                "recorder_result",
                mapOf(
                    "type" to event.eventType,
                    "pkg" to pkg,
                    "added" to newActions.size,
                    "kinds" to newActions.joinToString(",") { it::class.simpleName.orEmpty() },
                    "texts" to newActions.mapNotNull {
                        when (it) {
                            is RecordedAction.Tap -> it.text
                            is RecordedAction.AssertVisible -> it.text
                            else -> null
                        }
                    }.joinToString("|").take(100),
                ),
            )
        }
        // #endregion
        if (newActions.isEmpty()) {
            return
        }
        _state.update { it.copy(actions = it.actions + newActions) }
        val lastIndex = _state.value.actions.size - 1
        telemetryCollector.capture(
            rootProvider.root(),
            target,
            lastIndex,
            System.currentTimeMillis(),
        )
        publishPreview()
        AppFileLogger.info(
            "RecordingSession",
            "added ${newActions.size} action(s) type=${event.eventType} total=${_state.value.actions.size}",
        )
    }

    /**
     * Registra Back hardware/gesture come step Maestro (sopprime scroll spurî successivi).
     *
     * @param targetPackage Package app in registrazione.
     */
    fun onHardwareBack(targetPackage: String) {
        val current = _state.value
        if (!current.isRecording || current.isPaused) return
        val newActions = recorder.onBackPressed(targetPackage)
        if (newActions.isEmpty()) return
        _state.update { it.copy(actions = it.actions + newActions) }
        publishPreview()
        AppFileLogger.info("RecordingSession", "hardware_back total=${_state.value.actions.size}")
    }

    private fun handlePickClick(
        event: AccessibilityEvent,
        screenWidthPx: Int,
        screenHeightPx: Int,
        rootProvider: AccessibilityRootProvider,
        target: String,
    ) {
        val captured = recorder.onEvent(event, screenWidthPx, screenHeightPx, rootProvider)
        val tap = captured.filterIsInstance<RecordedAction.Tap>().lastOrNull()
            ?: captured.filterIsInstance<RecordedAction.InputText>().lastOrNull()?.let { input ->
                RecordedAction.Tap(packageName = target, viewId = input.viewId)
            }
        if (tap == null) {
            AppFileLogger.info("RecordingSession", "pick_miss")
            _state.update { it.copy(pickMode = false) }
            publishPreview()
            return
        }
        // Evita di appendere anche il tap grezzo catturato: usiamo solo l’azione PICK.
        val action: RecordedAction = when (pickActionKind) {
            PickActionKind.TAP -> tap
            PickActionKind.LONG_PRESS -> RecordedAction.LongPress(
                packageName = tap.packageName,
                viewId = tap.viewId,
                text = tap.text,
                contentDescription = tap.contentDescription,
                pointPercentX = tap.pointPercentX,
                pointPercentY = tap.pointPercentY,
            )
            PickActionKind.DOUBLE_TAP -> RecordedAction.DoubleTap(
                packageName = tap.packageName,
                viewId = tap.viewId,
                text = tap.text,
                contentDescription = tap.contentDescription,
                pointPercentX = tap.pointPercentX,
                pointPercentY = tap.pointPercentY,
            )
            PickActionKind.ASSERT_VISIBLE -> RecordedAction.AssertVisible(
                packageName = tap.packageName,
                viewId = tap.viewId,
                text = tap.text,
            )
            PickActionKind.ASSERT_NOT_VISIBLE -> RecordedAction.AssertNotVisible(
                packageName = tap.packageName,
                viewId = tap.viewId,
                text = tap.text,
            )
            PickActionKind.INPUT_TEXT -> RecordedAction.InputText(
                packageName = tap.packageName,
                text = "",
                viewId = tap.viewId,
            )
            PickActionKind.ERASE_TEXT -> RecordedAction.EraseText(
                packageName = tap.packageName,
                viewId = tap.viewId,
            )
        }
        _state.update { it.copy(actions = it.actions + action, pickMode = false) }
        publishPreview()
        AppFileLogger.info("RecordingSession", "pick_add kind=$pickActionKind")
    }

    private fun publishPreview() {
        val ctx = appContext ?: return
        val s = _state.value
        val pkg = s.targetPackage.orEmpty()
        if (pkg.isBlank()) return
        RecordingLivePreview.publish(ctx, s.actions, pkg, s.pickMode, s.isPaused)
    }

    private fun isRelevantPackage(
        eventPackage: String,
        targetPackage: String,
        rootProvider: AccessibilityRootProvider,
    ): Boolean {
        if (eventPackage == targetPackage) return true
        // Popup permesso / installer: cattura tap anche se root attiva ≠ target.
        if (MaestroSelectorHeuristics.isCaptureDialogPackage(eventPackage)) return true
        if (MaestroSelectorHeuristics.isForeignUiPackage(eventPackage)) return false
        val root = rootProvider.root() ?: return false
        val rootPkg = root.packageName?.toString()
        root.recycle()
        if (rootPkg == targetPackage) return true
        // Dialog dello stesso target in finestra secondaria: root può essere il dialog package.
        return MaestroSelectorHeuristics.isCaptureDialogPackage(rootPkg)
    }

    fun clearStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}

/**
 * Azione da creare quando l’utente seleziona un componente in PICK.
 */
enum class PickActionKind {
    TAP,
    LONG_PRESS,
    DOUBLE_TAP,
    ASSERT_VISIBLE,
    ASSERT_NOT_VISIBLE,
    INPUT_TEXT,
    ERASE_TEXT,
}
