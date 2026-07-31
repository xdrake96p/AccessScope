/**
 * Classificazione popup/overlay per step optional Maestro.
 */
package dev.accessscope.scanner.recorder.optimization.conditional

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
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
        // Dialog permesso/installer: sempre optional (possono non riapparire).
        if (MaestroSelectorHeuristics.isCaptureDialogPackage(action.packageName)) {
            return StepMetadata(executionMode = StepExecutionMode.Optional)
        }

        // «Non ora», Allow, Chiudi, … — optional anche senza telemetria overlay.
        if (MaestroSelectorHeuristics.isPopupDismissLabel(action.text ?: action.contentDescription)) {
            return StepMetadata(executionMode = StepExecutionMode.Optional)
        }

        val transition = telemetry?.transitions?.firstOrNull { it.toIndex == actionIndex }
        val fingerprint = telemetry?.snapshots?.firstOrNull { it.actionIndex == actionIndex }?.fingerprint
        val overlay = transition?.kind == TransitionKind.PossibleOverlay
        val offMainPath = fingerprint != null &&
            intel != null &&
            intel.mainPathFingerprints.isNotEmpty() &&
            !intel.mainPathFingerprints.contains(fingerprint)

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
                    // Rispetta optional già impostato a REC (dismiss/permission).
                    if (action.executionMode == StepExecutionMode.Optional) return@mapIndexed action
                    val meta = PopupClassifier.classifyTap(action, index, telemetry, intel)
                    action.copy(
                        executionMode = meta.executionMode,
                        conditionVisibleId = meta.conditionVisibleId ?: action.conditionVisibleId,
                        conditionVisibleText = meta.conditionVisibleText ?: action.conditionVisibleText,
                    )
                }
                is RecordedAction.AssertVisible -> {
                    val optional = isOptionalAssert(action, index, telemetry)
                    if (optional && action.executionMode != StepExecutionMode.Optional) {
                        action.copy(executionMode = StepExecutionMode.Optional)
                    } else {
                        action
                    }
                }
                is RecordedAction.InputText -> action
                else -> action
            }
        }

    private fun isOptionalAssert(
        action: RecordedAction.AssertVisible,
        index: Int,
        telemetry: FlowTelemetry?,
    ): Boolean {
        val text = action.text?.lowercase().orEmpty()
        if (text.contains("documento") || text.contains("caricamento") ||
            text.contains("permission") || text.contains("consenti") ||
            text.contains("non ora") || text.contains("kyc") ||
            text.contains('\n')
        ) {
            return true
        }
        val transition = telemetry?.transitions?.firstOrNull { it.toIndex == index }
        return transition?.kind == TransitionKind.PossibleOverlay
    }
}
