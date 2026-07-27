/**
 * Inserisce assertVisible di schermata dopo wait di animazione post-navigazione.
 */
package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.recorder.model.TransitionKind
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner

/**
 * Dopo `waitForAnimation` che segue launch / submit / back / screen transition:
 * assert sul titolo snapshot della schermata di arrivo.
 */
object AssertPlanner {

    /**
     * @param actions Azioni già con wait (output WaitPlanner).
     * @param telemetry Snapshot/title per indice.
     */
    fun enrich(actions: List<RecordedAction>, telemetry: FlowTelemetry?): List<RecordedAction> {
        if (actions.isEmpty() || telemetry == null) return actions
        val out = mutableListOf<RecordedAction>()
        for (i in actions.indices) {
            val a = actions[i]
            out += a
            if (a !is RecordedAction.WaitForAnimation) continue
            val prev = actions.getOrNull(i - 1) ?: continue
            val next = actions.getOrNull(i + 1)
            if (next is RecordedAction.AssertVisible) continue
            val shouldAssert = when (prev) {
                is RecordedAction.LaunchApp -> true
                is RecordedAction.Back -> true
                is RecordedAction.Tap ->
                    WaitPlanner.isSubmitLikeTap(prev) ||
                        telemetry.transitions.any {
                            it.fromIndex == indexOfAction(actions, prev) &&
                                it.kind == TransitionKind.ScreenTransition
                        }
                else -> false
            }
            if (!shouldAssert) continue
            val prevIdx = indexOfAction(actions, prev)
            val snap = telemetry.snapshots.firstOrNull { it.actionIndex == prevIdx + 1 }
                ?: telemetry.snapshots.firstOrNull { it.actionIndex == prevIdx }
            val title = snap?.title?.trim()?.takeIf { it.length in 3..80 } ?: continue
            if (title.equals("Schermata", ignoreCase = true)) continue
            out += RecordedAction.AssertVisible(
                packageName = prev.packageName.ifBlank { snap.packageName },
                text = title,
                timeoutMs = 8_000L,
                executionMode = StepExecutionMode.Required,
                timestampMs = a.timestampMs + 1,
            )
        }
        return out
    }

    private fun indexOfAction(actions: List<RecordedAction>, target: RecordedAction): Int =
        actions.indexOfFirst {
            it === target ||
                (it.timestampMs == target.timestampMs && it::class == target::class)
        }.coerceAtLeast(0)
}
