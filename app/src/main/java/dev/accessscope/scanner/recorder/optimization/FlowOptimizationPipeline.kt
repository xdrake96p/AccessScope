/**
 * Orchestratore pipeline ottimizzazione Maestro intelligente.
 */
package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.optimization.AssertPlanner
import dev.accessscope.scanner.recorder.optimization.conditional.OptionalStepPolicy
import dev.accessscope.scanner.recorder.optimization.lint.FlowLintAutoFix
import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import dev.accessscope.scanner.recorder.optimization.scroll.ScrollCoalescer
import dev.accessscope.scanner.recorder.optimization.selector.SelectorNormalizer
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner
import dev.accessscope.scanner.util.DebugSessionLog

/**
 * Pipeline: coalesce → dedupe → noise → wait → assert schermata → optional → lint → chain.
 */
object FlowOptimizationPipeline {

    private const val TAP_DEDUPE_MS = 800L
    /** Gap oltre il quale due input uguali sullo stesso campo restano due step (PIN confirm). */
    private const val REENTRY_INPUT_GAP_MS = 1_500L

    /**
     * Ottimizza azioni con contesto telemetria/scan.
     */
    fun optimize(actions: List<RecordedAction>, context: OptimizationContext): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val appId = context.appId.ifBlank {
            actions.firstNotNullOfOrNull { it.packageName.takeIf { it.isNotBlank() } }.orEmpty()
        }
        val cleaned = SelectorRanker.attachChains(
            FlowLintAutoFix.apply(
                SelectorNormalizer.normalizeViewIds(
                    NoiseActionFilter.dropNoiseWaits(
                        OptionalStepPolicy.apply(
                            AssertPlanner.enrich(
                                WaitPlanner.enrich(
                                    ScrollCoalescer.coalesce(
                                        NoiseActionFilter.dropGhostTapsAfterScrollOrIme(
                                            NoiseActionFilter.dropFocusTapsBeforeInput(
                                                NoiseActionFilter.dropNoiseTaps(
                                                    NoiseActionFilter.dropForeignUiActions(
                                                        NoiseActionFilter.dropNoiseScrolls(
                                                            dedupeTaps(coalesceInputText(actions)),
                                                        ),
                                                        appId,
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    appId,
                                    context.telemetry,
                                    context.scanIntel,
                                ),
                                context.telemetry,
                            ),
                            context.telemetry,
                            context.scanIntel,
                        ),
                    ),
                    appId,
                ),
                appId,
            ),
            context.scanIntel,
            context.telemetry,
        )
        return cleaned
    }

    /**
     * Sanitize per replay: rimuove solo rumore legacy (SystemUI, progress),
     * **senza** scartare step curati dall’editor (+ wait, hideKeyboard, tap su campi).
     */
    fun sanitizeForPlay(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = appId.ifBlank {
            actions.firstNotNullOfOrNull { it.packageName.takeIf { it.isNotBlank() } }.orEmpty()
        }
        val afterForeign = NoiseActionFilter.dropForeignUiActions(actions, pkg)
        val afterNoiseTaps = NoiseActionFilter.dropPlaybackNoiseTaps(afterForeign)
        val afterGhost = NoiseActionFilter.dropGhostTapsAfterScrollOrIme(afterNoiseTaps)
        val afterDupTaps = NoiseActionFilter.dropDuplicateTapsAcrossWaits(afterGhost)
        val afterStructScroll = NoiseActionFilter.dropStructuralScrollUntilVisible(afterDupTaps)
        val afterNoiseScrolls = NoiseActionFilter.dropNoiseScrolls(afterStructScroll)
        val afterNoiseWaits = NoiseActionFilter.dropNoiseWaits(afterNoiseScrolls)
        val withWaitTargets = WaitPlanner.attachBlindWaitsToNextTarget(afterNoiseWaits, pkg)
        val withAnim = WaitPlanner.ensureAnimationWaits(withWaitTargets, pkg)
        val normalized = SelectorNormalizer.normalizeViewIds(withAnim, pkg)
        val withChains = SelectorRanker.attachChains(normalized, null, null)
        // #region agent log
        DebugSessionLog.log(
            "H7",
            "FlowOptimizationPipeline.sanitizeForPlay",
            "sanitize_counts",
            mapOf(
                "in" to actions.size,
                "out" to withChains.size,
                "dropped" to (actions.size - withChains.size),
                "inTypes" to actions.joinToString(",") { it::class.simpleName.orEmpty() },
                "outTypes" to withChains.joinToString(",") { it::class.simpleName.orEmpty() },
                "blindWaitsAttached" to withChains.count {
                    it is RecordedAction.Wait && !it.visibleId.isNullOrBlank()
                },
            ),
        )
        // #endregion
        return withChains
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
