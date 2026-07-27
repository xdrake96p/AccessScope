/**
 * Auto-fix lint Maestro: inserisce wait mancanti e collassa blind wait lunghi.
 */
package dev.accessscope.scanner.recorder.optimization.lint

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Applica correzioni automatiche derivate da [FlowLinter] senza intervento editor.
 *
 * - Dopo tap submit-like senza wait → `WaitForAnimation` + `Wait` sul prossimo target.
 * - Wait ciechi lunghi → arricchiti con `visibleId`/`visibleText` del prossimo target (se presente).
 */
object FlowLintAutoFix {

    private const val BLIND_WAIT_THRESHOLD_MS = 5_000L

    /**
     * @param actions Azioni già noise-filtered / wait-enriched.
     * @param appId Package target.
     * @return Azioni con fix lint applicati.
     */
    fun apply(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = appId.ifBlank {
            actions.firstNotNullOfOrNull { it.packageName.takeIf { p -> p.isNotBlank() } }.orEmpty()
        }
        var out = insertMissingSubmitWaits(actions, pkg)
        out = shortenBlindWaits(out, pkg)
        return out
    }

    private fun insertMissingSubmitWaits(
        actions: List<RecordedAction>,
        pkg: String,
    ): List<RecordedAction> {
        val out = mutableListOf<RecordedAction>()
        for (i in actions.indices) {
            val a = actions[i]
            out += a
            if (a !is RecordedAction.Tap) continue
            if (!FlowLinter.isSubmitLike(a.text ?: a.contentDescription)) continue
            if (hasWaitAhead(actions, i)) continue
            val next = actions.getOrNull(i + 1) ?: continue
            out += RecordedAction.WaitForAnimation(packageName = a.packageName, timeoutMs = 1_000L)
            val wait = waitForNextTarget(next, a.packageName.ifBlank { pkg })
            if (wait != null) out += wait
        }
        return out
    }

    private fun shortenBlindWaits(actions: List<RecordedAction>, pkg: String): List<RecordedAction> {
        return actions.mapIndexed { index, action ->
            if (action !is RecordedAction.Wait) return@mapIndexed action
            if (!action.visibleId.isNullOrBlank() || !action.visibleText.isNullOrBlank()) {
                return@mapIndexed action
            }
            if (action.timeoutMs <= BLIND_WAIT_THRESHOLD_MS) return@mapIndexed action
            val next = actions.drop(index + 1).firstOrNull {
                it !is RecordedAction.Wait &&
                    it !is RecordedAction.WaitForAnimation &&
                    it !is RecordedAction.HideKeyboard
            } ?: return@mapIndexed action
            waitForNextTarget(next, action.packageName.ifBlank { pkg }, action.timeoutMs.coerceAtMost(8_000L))
                ?: action
        }
    }

    private fun hasWaitAhead(actions: List<RecordedAction>, fromIndex: Int): Boolean {
        val end = (fromIndex + 2).coerceAtMost(actions.lastIndex)
        if (fromIndex >= end) return false
        return (fromIndex + 1..end).any {
            when (actions[it]) {
                is RecordedAction.Wait, is RecordedAction.WaitForAnimation -> true
                else -> false
            }
        }
    }

    private fun waitForNextTarget(
        next: RecordedAction,
        pkg: String,
        timeoutMs: Long = 8_000L,
    ): RecordedAction.Wait? = when (next) {
        is RecordedAction.Tap -> {
            val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
                ?.takeUnless { MaestroSelectorHeuristics.isStructuralContainerViewId(it) }
                ?.takeUnless { MaestroSelectorHeuristics.isAmbiguousSharedViewId(it) }
            val text = next.text ?: next.contentDescription
            if (id.isNullOrBlank() && text.isNullOrBlank()) null
            else RecordedAction.Wait(
                packageName = pkg,
                timeoutMs = timeoutMs,
                visibleId = id?.let { MaestroSelectorHeuristics.normalizeViewId(it, pkg) },
                visibleText = text,
            )
        }
        is RecordedAction.InputText -> {
            val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
            if (id.isNullOrBlank()) null
            else RecordedAction.Wait(
                packageName = pkg,
                timeoutMs = timeoutMs,
                visibleId = MaestroSelectorHeuristics.normalizeViewId(next.viewId, pkg),
            )
        }
        else -> null
    }
}
