/**
 * Lint statico dei flussi Maestro: segnala step fragili prima del Play (piano M1-A1).
 */
package dev.accessscope.scanner.recorder.optimization.lint

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Gravità di una segnalazione di lint.
 *
 * [ERROR] è bloccante per il contratto ZeroEdit (gate al save).
 */
enum class LintSeverity { ERROR, WARNING, INFO }

/** Tipologia di problema rilevato su uno step. */
enum class LintRule {
    /** Solo coordinate % — non esportabile come primario ZeroEdit. */
    POINT_ONLY_SELECTOR,
    WEAK_SELECTOR,
    TEXT_ONLY_SELECTOR,
    STRUCTURAL_SELECTOR,
    NOISE_SELECTOR,
    VOLATILE_ID,
    MISSING_WAIT_AFTER_SUBMIT,
    BLIND_WAIT_LONG,
}

/** Singola segnalazione su uno step del flusso. */
data class FlowLintIssue(
    val stepIndex: Int,
    val rule: LintRule,
    val severity: LintSeverity,
    val message: String,
)

/** Esito del lint su un intero flusso. */
data class FlowLintReport(
    val issues: List<FlowLintIssue>,
) {
    /** Errori bloccanti ZeroEdit. */
    val errorCount: Int get() = issues.count { it.severity == LintSeverity.ERROR }

    val warningCount: Int get() = issues.count { it.severity == LintSeverity.WARNING }

    /** Segnalazioni raggruppate per indice step (editor). */
    fun byStep(): Map<Int, List<FlowLintIssue>> = issues.groupBy { it.stepIndex }
}

/**
 * Analizzatore statico dei flussi Maestro.
 *
 * Regole generiche (non legate a una singola app): robustezza del selettore,
 * attese mancanti dopo azioni "submit-like", attese cieche troppo lunghe.
 */
object FlowLinter {

    private val SUBMIT_LABELS = setOf(
        "continua", "conferma", "accedi", "avanti", "ok", "invia", "login",
        "sign in", "next", "done", "concludi", "paga", "autorizza", "prosegui",
    )

    private val VOLATILE_ID_REGEX = Regex(".*(_\\d{4,}|[0-9a-f]{8}-[0-9a-f]{4}|[0-9a-f]{16,}).*")

    private const val BLIND_WAIT_THRESHOLD_MS = 5_000L
    private const val SUBMIT_LOOKAHEAD_STEPS = 2

    /**
     * Esegue il lint di un flusso.
     *
     * @param actions Azioni del flusso in ordine di esecuzione.
     * @return [FlowLintReport] con segnalazioni ordinate per step.
     */
    fun lint(actions: List<RecordedAction>): FlowLintReport {
        val issues = mutableListOf<FlowLintIssue>()
        actions.forEachIndexed { index, action ->
            when (action) {
                is RecordedAction.Tap -> {
                    lintSelector(
                        stepIndex = index,
                        viewId = action.viewId,
                        text = action.text ?: action.contentDescription,
                        hasPoint = action.pointPercentX != null && action.pointPercentY != null,
                        weakFlag = action.weakSelector,
                        issues = issues,
                    )
                    if (isSubmitLike(action.text ?: action.contentDescription) &&
                        !hasWaitWithin(actions, index, SUBMIT_LOOKAHEAD_STEPS)
                    ) {
                        issues += FlowLintIssue(
                            stepIndex = index,
                            rule = LintRule.MISSING_WAIT_AFTER_SUBMIT,
                            severity = LintSeverity.WARNING,
                            message = "Tap «submit» senza attesa: aggiungi un wait sul prossimo target.",
                        )
                    }
                }
                is RecordedAction.DoubleTap -> {
                    lintSelector(
                        stepIndex = index,
                        viewId = action.viewId,
                        text = action.text ?: action.contentDescription,
                        hasPoint = action.pointPercentX != null && action.pointPercentY != null,
                        weakFlag = false,
                        issues = issues,
                    )
                }
                is RecordedAction.LongPress -> {
                    lintSelector(
                        stepIndex = index,
                        viewId = action.viewId,
                        text = action.text ?: action.contentDescription,
                        hasPoint = action.pointPercentX != null && action.pointPercentY != null,
                        weakFlag = false,
                        issues = issues,
                    )
                }
                is RecordedAction.Wait -> {
                    if (action.visibleId == null && action.visibleText == null &&
                        action.timeoutMs > BLIND_WAIT_THRESHOLD_MS
                    ) {
                        issues += FlowLintIssue(
                            stepIndex = index,
                            rule = LintRule.BLIND_WAIT_LONG,
                            severity = LintSeverity.INFO,
                            message = "Attesa cieca ${action.timeoutMs}ms: preferisci extendedWaitUntil su un target.",
                        )
                    }
                }
                else -> Unit
            }
        }
        return FlowLintReport(issues)
    }

    private fun lintSelector(
        stepIndex: Int,
        viewId: String?,
        text: String?,
        hasPoint: Boolean,
        weakFlag: Boolean,
        issues: MutableList<FlowLintIssue>,
    ) {
        val noSemantic = viewId.isNullOrBlank() && text.isNullOrBlank()
        when {
            noSemantic && hasPoint ->
                issues += FlowLintIssue(
                    stepIndex, LintRule.POINT_ONLY_SELECTOR, LintSeverity.ERROR,
                    "Solo coordinate %: usa PICK o un selettore id/testo prima del save.",
                )
            noSemantic && !hasPoint ->
                issues += FlowLintIssue(
                    stepIndex, LintRule.WEAK_SELECTOR, LintSeverity.ERROR,
                    "Tap senza selettore: ripeti con PICK sull’elemento.",
                )
            weakFlag && noSemantic ->
                issues += FlowLintIssue(
                    stepIndex, LintRule.WEAK_SELECTOR, LintSeverity.ERROR,
                    "Selettore debole segnalato in REC: completa con PICK.",
                )
            viewId.isNullOrBlank() && !text.isNullOrBlank() ->
                issues += FlowLintIssue(
                    stepIndex, LintRule.TEXT_ONLY_SELECTOR, LintSeverity.INFO,
                    "Solo testo: dipende da lingua e duplicati.",
                )
        }
        if (!viewId.isNullOrBlank()) {
            if (MaestroSelectorHeuristics.isStructuralContainerViewId(viewId)) {
                issues += FlowLintIssue(
                    stepIndex, LintRule.STRUCTURAL_SELECTOR, LintSeverity.WARNING,
                    "Id strutturale (layout/container): instabile come target primario.",
                )
            }
            if (MaestroSelectorHeuristics.isNoiseViewId(viewId)) {
                issues += FlowLintIssue(
                    stepIndex, LintRule.NOISE_SELECTOR, LintSeverity.WARNING,
                    "Id di caricamento/progress: elemento effimero, evita come target.",
                )
            }
            val short = MaestroSelectorHeuristics.shortViewId(viewId).orEmpty()
            if (VOLATILE_ID_REGEX.matches(short)) {
                issues += FlowLintIssue(
                    stepIndex, LintRule.VOLATILE_ID, LintSeverity.WARNING,
                    "Id con suffisso volatile (numerico/hash): probabile rottura al prossimo build.",
                )
            }
        }
    }

    /** `true` se l'etichetta assomiglia a un'azione di invio/conferma. */
    fun isSubmitLike(label: String?): Boolean {
        val normalized = label?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return false
        return SUBMIT_LABELS.any { normalized == it || normalized.startsWith("$it ") }
    }

    private fun hasWaitWithin(actions: List<RecordedAction>, fromIndex: Int, within: Int): Boolean {
        val end = (fromIndex + within).coerceAtMost(actions.lastIndex)
        if (fromIndex >= end) return false
        return (fromIndex + 1..end).any { i ->
            when (actions[i]) {
                is RecordedAction.Wait, is RecordedAction.WaitForAnimation,
                is RecordedAction.AssertVisible, is RecordedAction.ScrollUntilVisible,
                -> true
                else -> false
            }
        }
    }
}
