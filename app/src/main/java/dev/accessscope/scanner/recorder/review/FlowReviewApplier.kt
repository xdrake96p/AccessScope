/**
 * Applica esito revisione AI al flusso Maestro (normalizzazione post-Gemini).
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Post-processa azioni corrette da Gemini prima di ZeroEdit/export.
 *
 * Se [FlowReviewResult.usedFallback] è true restituisce il draft app ([optimized]).
 * Se valida, usa solo output Gemini (senza patch locali).
 */
object FlowReviewApplier {

    /**
     * Restituisce azioni da persistere.
     *
     * @param optimized Azioni pre-review pipeline app (fallback).
     * @param result Esito validato da [FlowReviewValidator].
     * @return Lista finale da passare a ZeroEdit.
     */
    fun apply(
        optimized: List<RecordedAction>,
        result: FlowReviewResult,
    ): List<RecordedAction> {
        if (result.usedFallback) return optimized
        return sanitizeSecrets(result.correctedActions)
    }

    private fun sanitizeSecrets(actions: List<RecordedAction>): List<RecordedAction> =
        actions.map { action ->
            if (action is RecordedAction.InputText && action.isPassword) {
                val masked = when {
                    action.text.contains("PIN", ignoreCase = true) ||
                        action.viewId?.contains("pin", ignoreCase = true) == true ->
                        "\${PIN}"
                    else -> "\${PASSWORD}"
                }
                action.copy(text = masked, isPassword = true)
            } else {
                action
            }
        }
}
