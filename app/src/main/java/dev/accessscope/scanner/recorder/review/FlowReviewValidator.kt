/**
 * Validazione risposta Gemini per revisione flusso Maestro.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Verifica che la risposta AI rispetti il contratto prompt (changes non vuoto, draft modificato).
 */
object FlowReviewValidator {

    /**
     * Valida esito parse e applica fallback se la risposta non è accettabile.
     *
     * @param parsed Esito [FlowReviewResponseParser.parse].
     * @param draftActions Azioni draft B (pipeline app).
     * @param rawActions Azioni grezze REC (completezza).
     * @return [FlowReviewResult] con `usedFallback=true` se invalida.
     */
    fun validate(
        parsed: FlowReviewResult,
        draftActions: List<RecordedAction>,
        rawActions: List<RecordedAction> = draftActions,
    ): FlowReviewResult {
        if (parsed.usedFallback) return parsed
        if (parsed.correctedActions.isEmpty()) {
            return fallback(draftActions, "empty_corrected_actions")
        }
        if (parsed.changes.isEmpty()) {
            return fallback(draftActions, "empty_changes")
        }
        if (actionsStructurallyEqual(parsed.correctedActions, draftActions)) {
            return fallback(draftActions, "identical_to_draft")
        }
        val rawTaps = rawActions.count { it is RecordedAction.Tap }
        val correctedTaps = parsed.correctedActions.count { it is RecordedAction.Tap }
        if (correctedTaps < rawTaps) {
            return fallback(draftActions, "missing_taps_from_raw")
        }
        val rawInputs = rawActions.count { it is RecordedAction.InputText }
        val correctedInputs = parsed.correctedActions.count { it is RecordedAction.InputText }
        if (correctedInputs < rawInputs) {
            return fallback(draftActions, "missing_inputs_from_raw")
        }
        return parsed.copy(source = FlowReviewSource.GEMINI)
    }

    private fun fallback(draftActions: List<RecordedAction>, reason: String): FlowReviewResult =
        FlowReviewResult(
            correctedActions = draftActions,
            changes = emptyList(),
            usedFallback = true,
            errorMessage = reason,
            source = FlowReviewSource.APP,
        )

    private fun actionsStructurallyEqual(a: List<RecordedAction>, b: List<RecordedAction>): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { (left, right) ->
            left::class == right::class && actionSignature(left) == actionSignature(right)
        }
    }

    private fun actionSignature(action: RecordedAction): String = when (action) {
        is RecordedAction.Tap -> "Tap:${action.viewId}:${action.text}:${action.executionMode}"
        is RecordedAction.InputText -> "Input:${action.viewId}:${action.text.length}:${action.isPassword}"
        is RecordedAction.Scroll -> "Scroll:${action.direction}"
        is RecordedAction.WaitForAnimation -> "WFA"
        is RecordedAction.Wait -> "Wait:${action.timeoutMs}:${action.visibleId}:${action.visibleText}"
        is RecordedAction.LaunchApp -> "Launch"
        else -> action::class.simpleName.orEmpty()
    }
}
