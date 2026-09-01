/**
 * Pianificazione wait adattivi per export Maestro.
 */
package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.capture.FieldInputTargetResolver
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.TransitionKind
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker

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
                is RecordedAction.Tap -> {
                    val next = actions.getOrNull(i + 1) ?: continue
                    if (next is RecordedAction.Wait ||
                        next is RecordedAction.WaitForAnimation ||
                        next is RecordedAction.HideKeyboard
                    ) {
                        continue
                    }
                    // Digitazione pad: niente wait tra cifre consecutive (stesso tasto o run pad).
                    if (isPinPadDigitTap(a) && next is RecordedAction.Tap && isPinPadDigitTap(next)) {
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
                    val quiescence = telemetry?.quiescenceGaps?.firstOrNull { it.afterActionIndex == i }
                    val quiescenceWait = quiescence?.let {
                        dev.accessscope.scanner.recorder.telemetry.RecordingTelemetry.suggestedWaitMs(it)
                    }
                    val delta = transition?.deltaMs
                        ?: (next.timestampMs - a.timestampMs).takeIf { a.timestampMs > 0L && next.timestampMs > a.timestampMs }
                    val submit = isSubmitLikeTap(a)
                    when {
                        // Stessa schermata: comunque breve waitForAnimation (evita tap durante anim).
                        !submit &&
                            transition?.kind == TransitionKind.SameScreen &&
                            (delta == null || delta < 800L) &&
                            quiescenceWait == null -> {
                            val short = TransitionTimingAnalyzer.sameScreenShortWaitMs(delta)
                                ?: 600L
                            out += RecordedAction.WaitForAnimation(
                                packageName = a.packageName.ifBlank { pkg },
                                timeoutMs = short.coerceIn(400L, 1_500L),
                            )
                        }
                        // Navigazione / loader / submit / quiescenza: animazione + waitUntil.
                        else -> {
                            val animTimeout = when {
                                quiescenceWait != null -> TransitionTimingAnalyzer.clamp(
                                    quiescenceWait,
                                    minMs = 700L,
                                    maxMs = 8_000L,
                                    fallbackMs = 2_000L,
                                )
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
                                ?.let { w ->
                                    if (quiescenceWait != null) {
                                        w.copy(timeoutMs = maxOf(w.timeoutMs, quiescenceWait))
                                    } else {
                                        w
                                    }
                                }
                            until?.let(out::add)
                        }
                    }
                }
                is RecordedAction.Back -> {
                    out += RecordedAction.WaitForAnimation(
                        packageName = a.packageName.ifBlank { pkg },
                        timeoutMs = 1_200L,
                    )
                }
                is RecordedAction.InputText -> {
                    val next = actions.getOrNull(i + 1) ?: continue
                    if (next is RecordedAction.Wait ||
                        next is RecordedAction.WaitForAnimation ||
                        next is RecordedAction.HideKeyboard
                    ) {
                        continue
                    }
                    if (next is RecordedAction.Tap || next is RecordedAction.InputText) {
                        out += RecordedAction.WaitForAnimation(
                            packageName = a.packageName.ifBlank { pkg },
                            timeoutMs = 700L,
                        )
                    }
                }
                else -> Unit
            }
        }
        return ensureAnimationWaits(out, pkg)
    }

    /**
     * Safety net: inserisce `waitForAnimationToEnd` tra azioni interattive consecutive
     * se manca già un wait (flussi legacy / editor).
     */
    fun ensureAnimationWaits(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.size < 2) return actions
        val pkg = appId.ifBlank {
            actions.firstNotNullOfOrNull { it.packageName.takeIf { p -> p.isNotBlank() } }.orEmpty()
        }
        val out = mutableListOf<RecordedAction>()
        for (i in actions.indices) {
            val cur = actions[i]
            out += cur
            val next = actions.getOrNull(i + 1) ?: break
            if (isSettlingAction(next)) continue
            if (!triggersUiAnimation(cur)) continue
            out += RecordedAction.WaitForAnimation(
                packageName = cur.packageName.ifBlank { pkg },
                timeoutMs = defaultAnimTimeoutMs(cur),
            )
        }
        return out
    }

    private fun isSettlingAction(action: RecordedAction): Boolean =
        action is RecordedAction.Wait ||
            action is RecordedAction.WaitForAnimation ||
            action is RecordedAction.HideKeyboard ||
            action is RecordedAction.AssertVisible ||
            action is RecordedAction.AssertNotVisible

    private fun triggersUiAnimation(action: RecordedAction): Boolean =
        when (action) {
            is RecordedAction.Tap,
            is RecordedAction.DoubleTap,
            is RecordedAction.LongPress,
            is RecordedAction.Back,
            is RecordedAction.LaunchApp,
            is RecordedAction.Scroll,
            is RecordedAction.ScrollUntilVisible,
            is RecordedAction.Swipe,
            is RecordedAction.InputText,
            -> true
            else -> false
        }

    private fun defaultAnimTimeoutMs(action: RecordedAction): Long =
        when (action) {
            is RecordedAction.LaunchApp -> 2_000L
            is RecordedAction.Back -> 1_200L
            is RecordedAction.Tap -> if (isSubmitLikeTap(action)) 2_000L
            else if (FieldInputTargetResolver.isPickerOpeningTap(action.viewId, action.text)) 1_200L
            else 800L
            else -> 800L
        }

    /**
     * Tap tipici di conferma/login che spesso attivano loader.
     *
     * Esclude etichette dismiss popup («OK, HO CAPITO», «Non ora», …) anche se
     * contengono sottostringhe come «ok».
     */
    fun isSubmitLikeTap(action: RecordedAction.Tap): Boolean {
        val label = listOfNotNull(action.text, action.contentDescription)
            .joinToString(" ")
            .lowercase()
            .trim()
        if (MaestroSelectorHeuristics.isPopupDismissLabel(label)) return false
        if (label.contains("ho capito")) return false
        if (label.isNotBlank() && SUBMIT_LABEL_HINTS.any { hint ->
                label == hint || label.startsWith("$hint ") || label.endsWith(" $hint") ||
                    label.contains(" $hint ")
            }
        ) {
            // Evita match troppo larghi: "ok" non deve matchare "ok, ho capito" (già escluso)
            // né stringhe casuali; richiede uguaglianza o parola intera.
            return true
        }
        // Match esatto su label corta (es. solo "CONTINUA" / "OK" bottone primario non-dismiss).
        if (label.isNotBlank() && SUBMIT_LABEL_HINTS.any { label == it }) return true
        val id = MaestroSelectorHeuristics.shortViewId(action.viewId)?.lowercase().orEmpty()
        if (id == "dismiss" || id.endsWith("_dismiss")) return false
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

    private fun isPinPadDigitTap(tap: RecordedAction.Tap): Boolean =
        MaestroSelectorHeuristics.isPinPadKey(tap.viewId, tap.text) ||
            MaestroSelectorHeuristics.isPinPadDigitTap(tap.text, tap.viewId)
}
