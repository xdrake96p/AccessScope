/**
 * Gate ZeroEdit: lint + heal statico + decisione se pubblicare YAML.
 *
 * Separato da [dev.accessscope.scanner.recorder.FlowStore] (I/O) e da
 * [dev.accessscope.scanner.recorder.FlowOptimizer] (pipeline ottimizzazione).
 */
package dev.accessscope.scanner.recorder.quality

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.optimization.lint.FlowLinter
import dev.accessscope.scanner.recorder.optimization.lint.LintSeverity

/**
 * Valuta se un flusso ottimizzato rispetta il contratto ZeroEdit.
 */
object ZeroEditGate {

    /**
     * Applica heal statico, riesegue il lint e produce un [ZeroEditReport].
     *
     * @param actions Azioni già passate da [dev.accessscope.scanner.recorder.FlowOptimizer.optimize].
     * @param healFirst Se `true`, esegue [StaticSelectorHealer] prima del lint.
     * @return Report con actions heal-ate e issues mappate.
     */
    fun evaluate(
        actions: List<RecordedAction>,
        healFirst: Boolean = true,
    ): ZeroEditReport {
        val healed = if (healFirst) StaticSelectorHealer.heal(actions) else actions
        val lint = FlowLinter.lint(healed)
        val issues = lint.issues.map { it.toZeroEditIssue() }.toMutableList()

        // Controlli aggiuntivi dedicati ZeroEdit (oltre FlowLinter).
        healed.forEachIndexed { index, action ->
            if (action is RecordedAction.Tap && action.weakSelector) {
                val hasSemantic = !action.viewId.isNullOrBlank() ||
                    !action.text.isNullOrBlank() ||
                    !action.contentDescription.isNullOrBlank()
                if (!hasSemantic) {
                    val already = issues.any {
                        it.stepIndex == index &&
                            (it.code == "POINT_ONLY_SELECTOR" || it.code == "WEAK_SELECTOR")
                    }
                    if (!already) {
                        issues += ZeroEditIssue(
                            stepIndex = index,
                            code = "WEAK_SELECTOR",
                            severity = ZeroEditSeverity.Error,
                            message = "Selettore debole in REC: completa con PICK sull’elemento.",
                        )
                    }
                }
            }
        }

        return ZeroEditReport(issues = issues, actions = healed)
    }

    /**
     * `true` se il report non ha Error (YAML pubblicabile).
     *
     * @param report Esito [evaluate].
     */
    fun isPublishable(report: ZeroEditReport): Boolean = !report.hasErrors

    /**
     * Filtra solo issues di gravità Error (per UI blocco save).
     *
     * @param report Report completo.
     * @return Lista Error.
     */
    fun blockingIssues(report: ZeroEditReport): List<ZeroEditIssue> =
        report.issues.filter { it.severity == ZeroEditSeverity.Error }

    /**
     * Conta Error dal lint grezzo (senza heal), utile in editor live.
     *
     * @param actions Azioni correnti in editor.
     * @return Numero Error.
     */
    fun liveErrorCount(actions: List<RecordedAction>): Int =
        FlowLinter.lint(actions).issues.count { it.severity == LintSeverity.ERROR }
}
