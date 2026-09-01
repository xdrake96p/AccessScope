/**
 * Formattazione lint draft B per prompt revisione Gemini.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.optimization.lint.FlowLintIssue
import dev.accessscope.scanner.recorder.optimization.lint.FlowLinter
import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Produce riepilogo testuale issue lint per sezione B2 del prompt.
 */
object FlowReviewLintSummary {

    /**
     * Esegue lint su draft e formatta per Gemini.
     *
     * @param draftActions Azioni post-optimizer (Sezione B).
     * @return Testo multilinea o `null` se nessuna issue.
     */
    fun format(draftActions: List<RecordedAction>): String? {
        val issues = FlowLinter.lint(draftActions).issues
        if (issues.isEmpty()) return null
        return issues.joinToString("\n") { issue -> formatIssue(issue) }
    }

    private fun formatIssue(issue: FlowLintIssue): String =
        "step ${issue.stepIndex} [${issue.severity.name}/${issue.rule.name}]: ${issue.message}"
}
