/**
 * Orchestratore pipeline ottimizzazione Maestro intelligente.
 *
 * Competenze delegate: noise → scroll → **blocking overlay order** → wait → assert →
 * optional → lint → selector chain.
 */
package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.optimization.AssertPlanner
import dev.accessscope.scanner.recorder.optimization.conditional.BlockingOverlayOrderHealer
import dev.accessscope.scanner.recorder.optimization.conditional.OptionalStepPolicy
import dev.accessscope.scanner.recorder.optimization.lint.FlowLintAutoFix
import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import dev.accessscope.scanner.recorder.optimization.picker.PickerFlowHealer
import dev.accessscope.scanner.recorder.optimization.scroll.ScrollCoalescer
import dev.accessscope.scanner.recorder.optimization.selector.SelectorNormalizer
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker
import dev.accessscope.scanner.recorder.optimization.timing.BlockingOverlayWaitPlanner
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner

/**
 * Pipeline: coalesce → dedupe → noise → overlay-order → wait → assert → optional → lint → chain.
 */
object FlowOptimizationPipeline {

    private const val TAP_DEDUPE_MS = 800L

    /**
     * Ottimizza azioni con contesto telemetria/scan.
     *
     * Catena lineare (era nesting profondo): ogni riga è una fase, in ordine di esecuzione
     * dall'alto in basso. Le fasi marcate **[condivisa]** girano anche in [sanitizeForPlay] —
     * su un `actions.json` già arricchito da questa stessa `optimize()` — e devono restare
     * sicure da eseguire una seconda volta (vedi `SharedNoiseStageIdempotencyTest.kt`, che
     * verifica questa proprietà su fixture reali invece di scoprirla di nuovo su un flusso
     * dell'utente). Le due pipeline **non** condividono lo stesso ordine relativo — non è stato
     * unificato per non rischiare un cambio di comportamento silenzioso senza saperne il motivo
     * storico; l'ordine di ciascuna resta quello di sempre.
     */
    fun optimize(actions: List<RecordedAction>, context: OptimizationContext): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val appId = context.appId.ifBlank {
            actions.firstNotNullOfOrNull { it.packageName.takeIf { it.isNotBlank() } }.orEmpty()
        }
        val afterCoalesceInput = coalesceInputText(actions)
        val afterDedupeTaps = dedupeTaps(afterCoalesceInput)
        val afterPinPad = NoiseActionFilter.normalizePinOrOtpSlotInputs(afterDedupeTaps) // [condivisa]
        val afterRating = NoiseActionFilter.dropSpuriousRatingAsserts(afterPinPad) // [condivisa]
        val afterNoiseScrolls = NoiseActionFilter.dropNoiseScrolls(afterRating) // [condivisa]
        val afterForeign = NoiseActionFilter.dropForeignUiActions(afterNoiseScrolls, appId) // [condivisa]
        val afterNoiseTaps = NoiseActionFilter.dropNoiseTaps(afterForeign)
        val afterFocusTaps = NoiseActionFilter.dropFocusTapsBeforeInput(afterNoiseTaps)
        val afterGhost = NoiseActionFilter.dropGhostTapsAfterScrollOrIme(afterFocusTaps) // [condivisa]
        val afterPickerHeal = PickerFlowHealer.ensurePickerOpenBeforeSelect(afterGhost, appId)
        val afterNoise = ScrollCoalescer.coalesce(afterPickerHeal)
        // Prima dei wait: dismiss alert subito dopo CONTINUA (evita input sotto overlay).
        val ordered = BlockingOverlayOrderHealer.reorder(afterNoise)
        val withOverlayWaits = BlockingOverlayWaitPlanner.enrich(ordered, appId)
        val withWaits = WaitPlanner.enrich(withOverlayWaits, appId, context.telemetry, context.scanIntel)
        val withAsserts = AssertPlanner.enrich(withWaits, context.telemetry)
        val withOptional = OptionalStepPolicy.apply(withAsserts, context.telemetry, context.scanIntel)
        val afterNoiseWaits = NoiseActionFilter.dropNoiseWaits(withOptional) // [condivisa]
        val normalized = SelectorNormalizer.normalizeViewIds(afterNoiseWaits, appId)
        val linted = FlowLintAutoFix.apply(normalized, appId)
        val cleaned = SelectorRanker.attachChains(linted, context.scanIntel, context.telemetry)
        // Secondo passaggio: wait planner può aver lasciato dismiss dopo nuovi wait.
        return BlockingOverlayOrderHealer.reorder(cleaned)
    }

    /**
     * Sanitize per replay: rimuove solo rumore legacy (SystemUI, progress),
     * **senza** scartare step curati dall’editor (+ wait, hideKeyboard, tap su campi).
     * Riordina anche overlay bloccanti così Play non fallisce su input sotto alert.
     *
     * Le fasi marcate **[condivisa]** sono le stesse di [optimize] — qui girano una seconda
     * volta, dopo che `optimize()` (o una modifica manuale nell'editor) ha già arricchito la
     * lista con `Wait`/`WaitForAnimation`. Vedi il commento su [optimize] e
     * `SharedNoiseStageIdempotencyTest.kt`.
     */
    fun sanitizeForPlay(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = appId.ifBlank {
            actions.firstNotNullOfOrNull { it.packageName.takeIf { it.isNotBlank() } }.orEmpty()
        }
        val afterForeign = NoiseActionFilter.dropForeignUiActions(actions, pkg) // [condivisa]
        val afterPinPad = NoiseActionFilter.normalizePinOrOtpSlotInputs(afterForeign) // [condivisa]
        val afterRating = NoiseActionFilter.dropSpuriousRatingAsserts(afterPinPad) // [condivisa]
        val afterPickerAsserts = NoiseActionFilter.markPickerAssertsOptional(afterRating)
        val afterOrphanPicker = NoiseActionFilter.dropOrphanPickerAsserts(afterPickerAsserts)
        val afterPickerHeal = PickerFlowHealer.ensurePickerOpenBeforeSelect(afterOrphanPicker, pkg)
        val afterNoiseTaps = NoiseActionFilter.dropPlaybackNoiseTaps(afterPickerHeal)
        val afterGhost = NoiseActionFilter.dropGhostTapsAfterScrollOrIme(afterNoiseTaps) // [condivisa]
        val afterDupTaps = NoiseActionFilter.dropDuplicateTapsAcrossWaits(afterGhost)
        val afterStructScroll = NoiseActionFilter.dropStructuralScrollUntilVisible(afterDupTaps)
        val afterNoiseScrolls = NoiseActionFilter.dropNoiseScrolls(afterStructScroll) // [condivisa]
        val afterNoiseWaits = NoiseActionFilter.dropNoiseWaits(afterNoiseScrolls) // [condivisa]
        val ordered = BlockingOverlayOrderHealer.reorder(afterNoiseWaits)
        val withOverlayWaits = BlockingOverlayWaitPlanner.enrich(ordered, pkg)
        val withWaitTargets = WaitPlanner.attachBlindWaitsToNextTarget(withOverlayWaits, pkg)
        val withAnim = WaitPlanner.ensureAnimationWaits(withWaitTargets, pkg)
        val normalized = SelectorNormalizer.normalizeViewIds(withAnim, pkg)
        val withChains = SelectorRanker.attachChains(normalized, null, null)
        val finalOrdered = BlockingOverlayOrderHealer.reorder(withChains)
        return finalOrdered
    }

    /**
     * Confronta gli `Scroll` grezzi con quelli sopravvissuti dopo [optimize]/[sanitizeForPlay],
     * per accorgersi se della distanza di scroll necessaria a raggiungere un target è andata
     * persa senza una `ScrollUntilVisible` a spiegarlo — bug reale trovato su un flusso AXA
     * registrato (4 scroll diventati 1, vedi `docs/PROJECT.md`), che [dev.accessscope.scanner.recorder.quality.ZeroEditGate]
     * non poteva scoprire perché valuta solo la lista già ottimizzata, senza mai un confronto
     * con quella grezza, e [dev.accessscope.scanner.recorder.optimization.lint.FlowLinter] non
     * ispeziona affatto gli `Scroll`.
     *
     * Una `ScrollUntilVisible` sopravvissuta spiega sempre una riduzione (è esattamente il suo
     * scopo, collassare un run di scroll in un solo gesto mirato) — l'avviso scatta solo quando
     * gli scroll diminuiscono **senza** che nessuna sia stata promossa.
     *
     * @param before Azioni grezze, prima di [optimize].
     * @param after Azioni dopo [optimize] (o [sanitizeForPlay]).
     * @return Avvisi in linguaggio naturale, vuoto se nessuna perdita sospetta.
     */
    fun auditScrollCardinality(before: List<RecordedAction>, after: List<RecordedAction>): List<String> {
        val rawScrollCount = before.count { it is RecordedAction.Scroll }
        if (rawScrollCount == 0) return emptyList()
        val finalScrollUntilVisibleCount = after.count { it is RecordedAction.ScrollUntilVisible }
        if (finalScrollUntilVisibleCount > 0) return emptyList()
        val finalScrollCount = after.count { it is RecordedAction.Scroll }
        if (finalScrollCount >= rawScrollCount) return emptyList()
        return listOf(
            "Possibile perdita di distanza di scroll: $rawScrollCount scroll registrati, " +
                "$finalScrollCount rimasti nel flusso ottimizzato — nessuna scrollUntilVisible a spiegare la riduzione.",
        )
    }

    /**
     * Coalesce solo digitazione incrementale sullo stesso campo.
     * Duplicati completi (es. PIN + conferma) restano due step; il secondo diventa Optional.
     */
    fun coalesceInputText(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val a = actions[i]
            if (a is RecordedAction.InputText) {
                var last: RecordedAction.InputText = a
                var pendingEmit = true
                var j = i + 1
                while (j < actions.size) {
                    val next = actions[j] as? RecordedAction.InputText ?: break
                    when {
                        shouldCoalesceInput(last, next) -> {
                            last = next
                            pendingEmit = true
                            j++
                        }
                        isDuplicateCompleteInput(last, next) -> {
                            if (pendingEmit) out += last
                            out += next.copy(executionMode = StepExecutionMode.Optional)
                            last = next
                            pendingEmit = false
                            j++
                        }
                        else -> break
                    }
                }
                if (pendingEmit) out += last
                i = j
            } else {
                out += a
                i++
            }
        }
        return out
    }

    /**
     * Due inserimenti completi identici sullo stesso campo (PIN re-entry / conferma).
     */
    internal fun isDuplicateCompleteInput(
        prev: RecordedAction.InputText,
        next: RecordedAction.InputText,
    ): Boolean {
        if (!sameInputField(prev, next) || prev.text != next.text) return false
        if (prev.isPassword || next.isPassword) {
            val pinPrev = MaestroSelectorHeuristics.isPinLikeField(prev.viewId)
            val pinNext = MaestroSelectorHeuristics.isPinLikeField(next.viewId)
            if (!pinPrev && !pinNext) return false
        }
        return true
    }

    /**
     * True solo se [next] è prosecuzione/correzione di digitazione di [prev] sullo stesso campo.
     */
    fun shouldCoalesceInput(prev: RecordedAction.InputText, next: RecordedAction.InputText): Boolean {
        if (!sameInputField(prev, next)) return false
        // Password login: collassa **** consecutivi in un solo step.
        if (prev.isPassword || next.isPassword) {
            val pinPrev = MaestroSelectorHeuristics.isPinLikeField(prev.viewId)
            val pinNext = MaestroSelectorHeuristics.isPinLikeField(next.viewId)
            if (!pinPrev && !pinNext) {
                return isIncrementalInputTyping(prev.text, next.text) || prev.text == next.text
            }
        }
        // PIN e campi generici: solo digitazione incrementale, mai duplicati completi.
        if (prev.text == next.text) return false
        return isIncrementalInputTyping(prev.text, next.text)
    }

    /** Prefisso stretto = keystroke/correzione, non PIN+conferma. */
    internal fun isIncrementalInputTyping(prev: String, next: String): Boolean {
        if (prev == next) return false
        return (next.startsWith(prev) && next.length > prev.length) ||
            (prev.startsWith(next) && prev.length > next.length)
    }

    /**
     * Collassa tap/long-press consecutivi con stesso selettore entro [TAP_DEDUPE_MS].
     */
    fun dedupeTaps(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val out = mutableListOf<RecordedAction>()
        for (a in actions) {
            val prev = out.lastOrNull()
            if (prev != null && isDuplicateTap(prev, a)) {
                out[out.lastIndex] = a
            } else {
                out += a
            }
        }
        return out
    }

    private fun sameInputField(a: RecordedAction.InputText, b: RecordedAction.InputText): Boolean {
        val idA = MaestroSelectorHeuristics.shortViewId(a.viewId)
        val idB = MaestroSelectorHeuristics.shortViewId(b.viewId)
        return when {
            !idA.isNullOrBlank() && !idB.isNullOrBlank() -> idA == idB
            idA.isNullOrBlank() && idB.isNullOrBlank() -> true
            else -> false
        }
    }

    private fun isDuplicateTap(prev: RecordedAction, next: RecordedAction): Boolean {
        if (prev is RecordedAction.Tap && isPinPadDigitTap(prev)) return false
        if (next is RecordedAction.Tap && isPinPadDigitTap(next)) return false
        val (pSel, pTs) = tapSelector(prev) ?: return false
        val (nSel, nTs) = tapSelector(next) ?: return false
        if (pSel != nSel) return false
        return kotlin.math.abs(nTs - pTs) <= TAP_DEDUPE_MS
    }

    private fun isPinPadDigitTap(tap: RecordedAction.Tap): Boolean =
        MaestroSelectorHeuristics.isPinPadKey(tap.viewId, tap.text) ||
            MaestroSelectorHeuristics.isPinPadDigitTap(tap.text, tap.viewId)

    private fun tapSelector(action: RecordedAction): Pair<String, Long>? = when (action) {
        is RecordedAction.Tap -> tapKey(
            action.viewId,
            action.text,
            action.contentDescription,
            action.pointPercentX,
            action.pointPercentY,
        ) to action.timestampMs
        is RecordedAction.LongPress -> tapKey(
            action.viewId,
            action.text,
            action.contentDescription,
            action.pointPercentX,
            action.pointPercentY,
        ) to action.timestampMs
        else -> null
    }

    private fun tapKey(
        viewId: String?,
        text: String?,
        cd: String?,
        x: Float?,
        y: Float?,
    ): String {
        val id = MaestroSelectorHeuristics.shortViewId(viewId)
        return when {
            !id.isNullOrBlank() -> "id:$id"
            !text.isNullOrBlank() -> "text:$text"
            !cd.isNullOrBlank() -> "cd:$cd"
            x != null && y != null -> "pt:${"%.1f".format(java.util.Locale.US, x)},${"%.1f".format(java.util.Locale.US, y)}"
            else -> "unknown"
        }
    }
}
