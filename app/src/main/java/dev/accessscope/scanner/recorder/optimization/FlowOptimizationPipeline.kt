/**
 * Orchestratore pipeline ottimizzazione Maestro intelligente.
 *
 * Competenze delegate: noise → scroll → **blocking overlay order** → wait → assert →
 * optional → lint → selector chain.
 */
package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.optimization.AssertPlanner
import dev.accessscope.scanner.recorder.optimization.conditional.BlockingOverlayOrderHealer
import dev.accessscope.scanner.recorder.optimization.conditional.OptionalStepPolicy
import dev.accessscope.scanner.recorder.optimization.lint.FlowLintAutoFix
import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import dev.accessscope.scanner.recorder.optimization.scroll.ScrollCoalescer
import dev.accessscope.scanner.recorder.optimization.selector.SelectorNormalizer
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker
import dev.accessscope.scanner.recorder.optimization.timing.BlockingOverlayWaitPlanner
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner
import dev.accessscope.scanner.util.DebugSessionLog

/**
 * Pipeline: coalesce → dedupe → noise → overlay-order → wait → assert → optional → lint → chain.
 */
object FlowOptimizationPipeline {

    private const val TAP_DEDUPE_MS = 800L
    /** Gap oltre il quale due input uguali sullo stesso campo restano due step (PIN confirm). */
    private const val REENTRY_INPUT_GAP_MS = 1_500L

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
        val afterNoise = ScrollCoalescer.coalesce(afterGhost)
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
        val afterNoiseTaps = NoiseActionFilter.dropPlaybackNoiseTaps(afterRating)
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
        // #region agent log
        DebugSessionLog.log(
            "H7",
            "FlowOptimizationPipeline.sanitizeForPlay",
            "sanitize_counts",
            mapOf(
                "in" to actions.size,
                "out" to finalOrdered.size,
                "dropped" to (actions.size - finalOrdered.size),
                "inTypes" to actions.joinToString(",") { it::class.simpleName.orEmpty() },
                "outTypes" to finalOrdered.joinToString(",") { it::class.simpleName.orEmpty() },
                "blindWaitsAttached" to finalOrdered.count {
                    it is RecordedAction.Wait && !it.visibleId.isNullOrBlank()
                },
            ),
        )
        // #endregion
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
     * Non unisce due inserimenti completi uguali/distanti (es. PIN + conferma PIN).
     */
    fun coalesceInputText(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val a = actions[i]
            if (a is RecordedAction.InputText) {
                var last: RecordedAction.InputText = a
                var j = i + 1
                while (j < actions.size) {
                    val next = actions[j] as? RecordedAction.InputText
                    if (next != null && shouldCoalesceInput(last, next)) {
                        last = next
                        j++
                    } else {
                        break
                    }
                }
                out += last
                i = j
            } else {
                out += a
                i++
            }
        }
        return out
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
                val incremental = next.text.startsWith(prev.text) || prev.text.startsWith(next.text)
                return incremental || prev.text == next.text
            }
        }
        // PIN: non collassare due inserimenti completi (anche uguali).
        if (MaestroSelectorHeuristics.isPinLikeField(prev.viewId) ||
            MaestroSelectorHeuristics.isPinLikeField(next.viewId)
        ) {
            val incremental = next.text.startsWith(prev.text) || prev.text.startsWith(next.text)
            if (incremental && prev.text != next.text) return true
            // #region agent log
            DebugSessionLog.log(
                "H3",
                "FlowOptimizationPipeline.shouldCoalesceInput",
                "keep_pin_like",
                mapOf("viewId" to prev.viewId, "isPassword" to prev.isPassword),
            )
            // #endregion
            return false
        }
        val gap = kotlin.math.abs(next.timestampMs - prev.timestampMs)
        if (prev.text == next.text && gap >= REENTRY_INPUT_GAP_MS) {
            return false
        }
        if (next.text.startsWith(prev.text) || prev.text.startsWith(next.text)) return true
        if (gap < REENTRY_INPUT_GAP_MS) return true
        return false
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
        val (pSel, pTs) = tapSelector(prev) ?: return false
        val (nSel, nTs) = tapSelector(next) ?: return false
        if (pSel != nSel) return false
        return kotlin.math.abs(nTs - pTs) <= TAP_DEDUPE_MS
    }

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
