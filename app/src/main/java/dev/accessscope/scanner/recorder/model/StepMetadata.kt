/**
 * Metadati export/replay per singolo step Maestro.
 */
package dev.accessscope.scanner.recorder.model

/**
 * Metadata associata a tap/input per export optional/condizionale.
 *
 * @param executionMode Modalità Maestro dello step.
 * @param conditionVisibleId Id vista da attendere prima dello step condizionale.
 * @param conditionVisibleText Testo da attendere prima dello step condizionale.
 */
data class StepMetadata(
    val executionMode: StepExecutionMode = StepExecutionMode.Required,
    val conditionVisibleId: String? = null,
    val conditionVisibleText: String? = null,
)
