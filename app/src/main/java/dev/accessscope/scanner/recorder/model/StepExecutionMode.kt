/**
 * Modalità di esecuzione di uno step Maestro (required / optional / condizionale).
 */
package dev.accessscope.scanner.recorder.model

/**
 * Indica come Maestro e il playback in-app devono trattare un fallimento dello step.
 */
enum class StepExecutionMode {
    /** Step obbligatorio: fallimento blocca il flusso. */
    Required,

    /** Step opzionale (`optional: true` in YAML): fallimento ignorato. */
    Optional,

    /** Step condizionale: richiede elemento visibile prima dell’azione. */
    Conditional,
}
