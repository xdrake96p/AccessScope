/**
 * Pianificazione wait adattivi per export Maestro.
 */
package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.TransitionKind
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker
import dev.accessscope.scanner.util.DebugSessionLog

/**
 * Inserisce `waitForAnimationToEnd` e `extendedWaitUntil` con timeout da telemetria/scan.
 *
 * Dopo tap di submit (CONTINUA/CONFERMA/…) inserisce sempre wait per loader/navigazione.
 */
object WaitPlanner {

    private val SUBMIT_LABEL_HINTS = listOf(
        "continua",
        "conferma",
        "confirm",
        "continue",
        "accedi",
        "login",
        "submit",
        "avanti",
        "next",
        "ok",
        "salva",
        "invia",
        "procedi",
        "entra",
        "sign in",
        "log in",
    )

    /**
     * Arricchisce azioni con wait intelligenti.
     *
     * @param actions Azioni già filtrate noise.
     * @param appId Package target.
     * @param telemetry Telemetria registrazione.
     * @param intel Intelligence scan.
     * @return Azioni con wait inseriti.
     */
    fun enrich(
        actions: List<RecordedAction>,
        appId: String,
        telemetry: FlowTelemetry? = null,
        intel: ScanIntelligenceBundle? = null,
    ): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = actions.firstOrNull()?.packageName.orEmpty().ifBlank { appId }
        val out = mutableListOf<RecordedAction>()
        for (i in actions.indices) {
            val a = actions[i]
            out += a
            when (a) {
                is RecordedAction.LaunchApp -> {
                    val timeout = TransitionTimingAnalyzer.launchAnimationTimeoutMs(telemetry)
                    out += RecordedAction.WaitForAnimation(packageName = a.packageName, timeoutMs = timeout)
                }
                is RecordedAction.InputText -> Unit
                is RecordedAction.Tap -> {
                    val next = actions.getOrNull(i + 1) ?: continue
                    if (next is RecordedAction.Wait ||
                        next is RecordedAction.WaitForAnimation ||
                        next is RecordedAction.HideKeyboard
                    ) {
                        continue
                    }
                    if (next !is RecordedAction.Tap &&
                        next !is RecordedAction.InputText &&
                        next !is RecordedAction.Scroll &&
                        next !is RecordedAction.Back
                    ) {
                        continue
                    }
                    val transition = telemetry?.transitions?.firstOrNull { it.fromIndex == i }
                    val delta = transition?.deltaMs
                        ?: (next.timestampMs - a.timestampMs).takeIf { a.timestampMs > 0L && next.timestampMs > a.timestampMs }
                    val submit = isSubmitLikeTap(a)
                    when {
                        // Tap verso campo vicino: wait breve o nessuno.
                        !submit &&
                            transition?.kind == TransitionKind.SameScreen &&
                            (delta == null || delta < 800L) -> {
                            val short = TransitionTimingAnalyzer.sameScreenShortWaitMs(delta)
                            // #region agent log
                            DebugSessionLog.log(
                                "H1",
                                "WaitPlanner.enrich",
                                "same_screen_short_or_skip",
                                mapOf(
                                    "tapText" to a.text,
                                    "tapId" to a.viewId,
                                    "shortMs" to short,
                                    "deltaMs" to delta,
                                ),
                            )
                            // #endregion
                            if (short != null) {
                                out += RecordedAction.WaitForAnimation(
                                    packageName = a.packageName.ifBlank { pkg },
                                    timeoutMs = short,
                                )
                            }
                        }
                        // Navigazione / loader / submit: sempre animazione + waitUntil sul prossimo target.
                        else -> {
                            val animTimeout = when {
                                submit -> TransitionTimingAnalyzer.clamp(
                                    delta?.let { (it * 1.2).toLong() },
                                    minMs = 1_500L,
                                    maxMs = 8_000L,
                                    fallbackMs = 2_500L,
                                )
                                transition?.kind == TransitionKind.ScreenTransition ->
                                    TransitionTimingAnalyzer.clamp(
                                        delta?.let { (it * 1.2).toLong() },
                                        minMs = 800L,
                                        maxMs = 5_000L,
                                        fallbackMs = 1_500L,
                                    )
                                else -> TransitionTimingAnalyzer.clamp(
                                    delta?.let { (it * 1.2).toLong() },
                                    minMs = 1_000L,
                                    maxMs = 6_000L,
                                    fallbackMs = 2_000L,
                                )
                            }
                            out += RecordedAction.WaitForAnimation(
                                packageName = a.packageName.ifBlank { pkg },
                                timeoutMs = animTimeout,
                            )
                            val until = waitUntilFor(next, a.packageName.ifBlank { pkg }, delta, intel, actions)
                            // #region agent log
                            DebugSessionLog.log(
                                "H1",
                                "WaitPlanner.enrich",
                                "inserted_wait_after_tap",
                                mapOf(
                                    "submit" to submit,
                                    "tapText" to a.text,
                                    "tapId" to a.viewId,
                                    "animMs" to animTimeout,
                                    "hasWaitUntil" to (until != null),
                                    "waitVisibleId" to until?.visibleId,
                                    "waitVisibleText" to until?.visibleText,
                                    "nextKind" to next::class.simpleName,
                                ),
                            )
                            // #endregion
                            until?.let(out::add)
                        }
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    /**
     * Tap tipici di conferma/login che spesso attivano loader.
     */
    fun isSubmitLikeTap(action: RecordedAction.Tap): Boolean {
        val label = listOfNotNull(action.text, action.contentDescription)
            .joinToString(" ")
            .lowercase()
        if (label.isNotBlank() && SUBMIT_LABEL_HINTS.any { label.contains(it) }) return true
        val id = MaestroSelectorHeuristics.shortViewId(action.viewId)?.lowercase().orEmpty()
        return id.contains("submit") ||
            id.contains("continue") ||
            id.contains("confirm") ||
            id.contains("login") ||
            id.contains("signin") ||
            id.contains("sign_in")
    }

    private fun waitUntilFor(
        next: RecordedAction,
        pkg: String,
        observedDeltaMs: Long?,
        intel: ScanIntelligenceBundle?,
        allActions: List<RecordedAction>,
    ): RecordedAction.Wait? {
        val timeout = TransitionTimingAnalyzer.extendedWaitTimeoutMs(observedDeltaMs)
        return when (next) {
            is RecordedAction.Tap -> {
                if (MaestroSelectorHeuristics.isNoiseViewId(next.viewId)) return null
                if (!SelectorRanker.shouldExportIdOnly(next, allActions, intel)) {
                    val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
                    if (id.isNullOrBlank() && next.text.isNullOrBlank()) return null
                }
                val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
                when {
                    !id.isNullOrBlank() && !MaestroSelectorHeuristics.isNoiseViewId(id) ->
                        RecordedAction.Wait(
                            packageName = pkg,
                            timeoutMs = timeout,
                            visibleId = MaestroSelectorHeuristics.normalizeViewId(next.viewId, pkg) ?: id,
                        )
                    !next.text.isNullOrBlank() ->
                        RecordedAction.Wait(
                            packageName = pkg,
                            timeoutMs = timeout,
                            visibleText = next.text,
                        )
                    else -> null
                }
            }
            is RecordedAction.InputText -> {
                val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
                if (id.isNullOrBlank() || MaestroSelectorHeuristics.isNoiseViewId(id)) return null
                RecordedAction.Wait(
                    packageName = pkg,
                    timeoutMs = timeout,
                    visibleId = MaestroSelectorHeuristics.normalizeViewId(next.viewId, pkg) ?: id,
                )
            }
            else -> null
        }
    }

    /**
     * Arricchisce `Wait` ciechi (solo timeout) con `visibleId`/`visibleText` del prossimo target.
     * Utile per flussi legacy / editor dove dopo CONTINUA c’è wait 10s senza selettore.
     *
     * @param actions Azioni da Play/sanitize.
     * @param appId Package target.
     * @return Azioni con wait più precisi.
     */
    fun attachBlindWaitsToNextTarget(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = appId.ifBlank {
            actions.firstOrNull()?.packageName.orEmpty()
        }
        return actions.mapIndexed { index, action ->
            if (action !is RecordedAction.Wait) return@mapIndexed action
            if (!action.visibleId.isNullOrBlank() || !action.visibleText.isNullOrBlank()) {
                return@mapIndexed action
            }
            val next = actions.drop(index + 1).firstOrNull {
                it is RecordedAction.InputText || it is RecordedAction.Tap
            } ?: return@mapIndexed action
            when (next) {
                is RecordedAction.InputText -> {
                    val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
                    if (id.isNullOrBlank()) action
                    else action.copy(
                        visibleId = MaestroSelectorHeuristics.normalizeViewId(next.viewId, pkg) ?: id,
                    )
                }
                is RecordedAction.Tap -> {
                    val id = MaestroSelectorHeuristics.shortViewId(next.viewId)
                    when {
                        !id.isNullOrBlank() &&
                            !MaestroSelectorHeuristics.isNoiseViewId(id) &&
                            !MaestroSelectorHeuristics.isStructuralContainerViewId(id) ->
                            action.copy(
                                visibleId = MaestroSelectorHeuristics.normalizeViewId(next.viewId, pkg) ?: id,
                            )
                        !next.text.isNullOrBlank() -> action.copy(visibleText = next.text)
                        else -> action
                    }
                }
                else -> action
            }
        }
    }
}
