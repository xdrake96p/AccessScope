/**
 * Healer flussi con selezione picker (rubrica / IBAN) incompleta in REC.
 */
package dev.accessscope.scanner.recorder.optimization.picker

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.capture.FieldInputTargetResolver
import dev.accessscope.scanner.recorder.model.StepExecutionMode

/**
 * Inserisce tap di apertura picker mancanti prima di tap su voci lista.
 */
object PickerFlowHealer {

    private const val LOOKBACK_STEPS = 8

    /**
     * Se un tap su voce lista picker non ha tap icona/campo precedente, inserisce tap sul campo form.
     *
     * @param actions Azioni in ingresso.
     * @param appId Package target.
     * @return Azioni con step di apertura picker sintetici dove necessario.
     */
    fun ensurePickerOpenBeforeSelect(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = appId.ifBlank { actions.first().packageName }
        val out = mutableListOf<RecordedAction>()
        for (i in actions.indices) {
            val action = actions[i]
            if (action is RecordedAction.Tap &&
                FieldInputTargetResolver.isPickerListLabel(action.text, action.contentDescription) &&
                !hasPickerOpeningInLookback(out, LOOKBACK_STEPS)
            ) {
                inferFieldLabelTap(action.text ?: action.contentDescription.orEmpty(), out)?.let { fieldTap ->
                    out += fieldTap.copy(
                        packageName = fieldTap.packageName.ifBlank { pkg },
                        timestampMs = action.timestampMs - 1L,
                    )
                }
            }
            out += action
        }
        return out
    }

    private fun hasPickerOpeningInLookback(actions: List<RecordedAction>, lookback: Int): Boolean {
        val start = (actions.size - lookback).coerceAtLeast(0)
        return actions.subList(start, actions.size).any { prev ->
            when (prev) {
                is RecordedAction.Tap ->
                    FieldInputTargetResolver.isPickerOpeningTap(prev.viewId, prev.text) ||
                        FieldInputTargetResolver.looksLikeFieldLabel(prev.text.orEmpty())
                is RecordedAction.AssertVisible ->
                    FieldInputTargetResolver.isSelectionPickerTitle(prev.text.orEmpty())
                else -> false
            }
        }
    }

    private fun inferFieldLabelTap(listLabel: String, prior: List<RecordedAction>): RecordedAction.Tap? {
        val compact = listLabel.replace(" ", "")
        val isIban = compact.matches(Regex("^IT[0-9A-Z]{13,34}$", RegexOption.IGNORE_CASE))
        val hints = if (isIban) {
            listOf("iban", "coordinate", "conto")
        } else {
            listOf("beneficiar", "dati", "contatt", "rubrica")
        }
        for (a in prior.asReversed()) {
            when (a) {
                is RecordedAction.Tap -> {
                    val t = a.text ?: continue
                    if (hints.any { h -> t.lowercase().contains(h) } &&
                        FieldInputTargetResolver.looksLikeFieldLabel(t)
                    ) {
                        return a
                    }
                }
                is RecordedAction.InputText -> {
                    val id = a.viewId?.lowercase().orEmpty()
                    if (hints.any { h -> id.contains(h) }) {
                        return RecordedAction.Tap(
                            packageName = a.packageName,
                            viewId = a.viewId,
                            text = hints.firstOrNull()?.let { "Inserisci dati $it" },
                            timestampMs = a.timestampMs,
                            executionMode = StepExecutionMode.Required,
                        )
                    }
                }
                else -> Unit
            }
        }
        return null
    }
}
