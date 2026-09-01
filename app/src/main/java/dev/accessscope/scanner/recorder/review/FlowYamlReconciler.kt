/**
 * Confronto YAML/azioni Gemini vs pipeline app prima di presentare all'operatore.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.quality.ZeroEditReport

/** Sorgente YAML mostrato all'utente. */
enum class PresentedYamlSource {
    GEMINI,
    APP,
    MERGED,
}

/**
 * Esito gate confronto qualità.
 */
data class YamlReconcileResult(
    val presentedActions: List<RecordedAction>,
    val presentedSource: PresentedYamlSource,
    val reason: String?,
    val appScore: Int,
    val geminiScore: Int,
    val mergedFixCount: Int = 0,
)

/**
 * Sceglie la migliore lista azioni tra Gemini e app (o merge ibrido).
 */
object FlowYamlReconciler {

    /**
     * @param raw Azioni REC grezze (completezza).
     * @param appActions Pipeline app post ZeroEdit.
     * @param geminiActions Output Gemini validato.
     * @param appReport Lint app.
     * @param geminiReport Lint Gemini.
     * @param geminiUsable `true` se review Gemini non in fallback.
     */
    fun reconcile(
        raw: List<RecordedAction>,
        appActions: List<RecordedAction>,
        geminiActions: List<RecordedAction>,
        appReport: ZeroEditReport,
        geminiReport: ZeroEditReport,
        geminiUsable: Boolean,
    ): YamlReconcileResult {
        val appScore = score(raw, appActions, appReport)
        val geminiScore = score(raw, geminiActions, geminiReport)
        if (!geminiUsable) {
            return YamlReconcileResult(
                presentedActions = appActions,
                presentedSource = PresentedYamlSource.APP,
                reason = "gemini_fallback",
                appScore = appScore,
                geminiScore = geminiScore,
            )
        }
        val rawTapCount = raw.count { it is RecordedAction.Tap }
        val geminiTapCount = geminiActions.count { it is RecordedAction.Tap }
        if (geminiTapCount < rawTapCount) {
            val merged = FlowYamlMergePolicy.mergeMissingFrom(raw, geminiActions, appActions)
            val mergedReport = geminiReport.copy(actions = merged)
            return YamlReconcileResult(
                presentedActions = merged,
                presentedSource = PresentedYamlSource.MERGED,
                reason = "merged_missing_taps",
                appScore = appScore,
                geminiScore = geminiScore,
                mergedFixCount = merged.size - geminiActions.size,
            )
        }
        if (geminiReport.hasErrors && !appReport.hasErrors) {
            return YamlReconcileResult(
                presentedActions = appActions,
                presentedSource = PresentedYamlSource.APP,
                reason = "gemini_lint_errors",
                appScore = appScore,
                geminiScore = geminiScore,
            )
        }
        if (geminiScore >= appScore) {
            return YamlReconcileResult(
                presentedActions = geminiActions,
                presentedSource = PresentedYamlSource.GEMINI,
                reason = null,
                appScore = appScore,
                geminiScore = geminiScore,
            )
        }
        return YamlReconcileResult(
            presentedActions = appActions,
            presentedSource = PresentedYamlSource.APP,
            reason = "app_score_higher",
            appScore = appScore,
            geminiScore = geminiScore,
        )
    }

    private fun score(raw: List<RecordedAction>, actions: List<RecordedAction>, report: ZeroEditReport): Int {
        var s = 0
        if (!report.hasErrors) s += 40
        s -= report.errorCount * 15
        s -= report.warningCount * 2
        val rawTaps = raw.count { it is RecordedAction.Tap }
        val taps = actions.count { it is RecordedAction.Tap }
        if (taps >= rawTaps) s += 30 else s -= (rawTaps - taps) * 10
        val waits = actions.count { it is RecordedAction.WaitForAnimation || it is RecordedAction.Wait }
        s += waits.coerceAtMost(20) * 2
        s += actions.size.coerceAtMost(80)
        return s
    }
}

/**
 * Reinserisce tap/input mancanti nel draft Gemini preservando ordine grezzo.
 */
object FlowYamlMergePolicy {

    fun mergeMissingFrom(
        raw: List<RecordedAction>,
        gemini: List<RecordedAction>,
        app: List<RecordedAction>,
    ): List<RecordedAction> {
        val restored = FlowReviewRawRestorer.restore(raw, gemini)
        if (restored.size >= gemini.size) return restored
        return FlowReviewRawRestorer.restore(raw, app)
    }
}
