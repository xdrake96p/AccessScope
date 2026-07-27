/**
 * Classificazione popup/overlay per step optional Maestro.
 */
package dev.accessscope.scanner.recorder.optimization.conditional

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.recorder.model.StepMetadata
import dev.accessscope.scanner.recorder.model.TransitionKind

/**
 * Identifica tap su popup/dismiss opzionali vs step obbligatori.
 */
object PopupClassifier {

    private val POPUP_TEXT_HINTS = listOf(
        "allow",
        "ok",
        "chiudi",
        "close",
        "non ora",
        "not now",
        "later",
        "dismiss",
        "annulla",
        "skip",
        "no thanks",
    )

    /**
     * Classifica metadata per un tap.
     *
     * @param action Tap da classificare.
     * @param actionIndex Indice nell’elenco azioni.
     * @param telemetry Telemetria registrazione.
     * @param intel Intelligence scan.
     * @return [StepMetadata] con execution mode.
     */
    fun classifyTap(
        action: RecordedAction.Tap,
        actionIndex: Int,
        telemetry: FlowTelemetry?,
        intel: ScanIntelligenceBundle?,
    ): StepMetadata {
        val transition = telemetry?.transitions?.firstOrNull { it.toIndex == actionIndex }
        val fingerprint = telemetry?.snapshots?.firstOrNull { it.actionIndex == actionIndex }?.fingerprint
        val popupText = isPopupDismissText(action.text ?: action.contentDescription)
        val overlay = transition?.kind == TransitionKind.PossibleOverlay
        val offMainPath = fingerprint != null &&
            intel != null &&
            intel.mainPathFingerprints.isNotEmpty() &&
            !intel.mainPathFingerprints.contains(fingerprint)

        if (popupText && (overlay || offMainPath)) {
            return StepMetadata(executionMode = StepExecutionMode.Optional)
        }

        val requiredForLogin = fingerprint != null &&
            intel != null &&
            intel.mainPathFingerprints.any { fp ->
                fp.contains("login", ignoreCase = true) || fp.contains("sign", ignoreCase = true)
            } &&
            action.viewId?.contains("signIn", ignoreCase = true) == true

        if (requiredForLogin) {
            return StepMetadata(executionMode = StepExecutionMode.Required)
        }

        if (offMainPath && overlay) {
            return StepMetadata(executionMode = StepExecutionMode.Optional)
        }

        return StepMetadata(executionMode = StepExecutionMode.Required)
    }

    private fun isPopupDismissText(text: String?): Boolean {
        val value = text?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return false
        return POPUP_TEXT_HINTS.any { value == it || value.contains(it) }
    }
}

/**
 * Applica policy optional/condizionale alle azioni tap/input.
 */
object OptionalStepPolicy {

    /**
     * Aggiorna `executionMode` e condizioni su tap/input.
     *
     * @param actions Azioni con wait inseriti.
     * @param telemetry Telemetria.
     * @param intel Intelligence scan.
     * @return Azioni con metadata step.
     */
    fun apply(
        actions: List<RecordedAction>,
        telemetry: FlowTelemetry?,
        intel: ScanIntelligenceBundle?,
    ): List<RecordedAction> =
        actions.mapIndexed { index, action ->
            when (action) {
                is RecordedAction.Tap -> {
                    val meta = PopupClassifier.classifyTap(action, index, telemetry, intel)
                    action.copy(
                        executionMode = meta.executionMode,
                        conditionVisibleId = meta.conditionVisibleId,
                        conditionVisibleText = meta.conditionVisibleText,
                    )
                }
                is RecordedAction.InputText -> action
                else -> action
            }
        }
}
