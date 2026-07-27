/**
 * Contesto per la pipeline di ottimizzazione Maestro intelligente.
 */
package dev.accessscope.scanner.recorder.model

import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle

/**
 * Input per [dev.accessscope.scanner.recorder.optimization.FlowOptimizationPipeline].
 *
 * @param appId Package Android target.
 * @param telemetry Telemetria registrazione (opzionale).
 * @param scanIntel Intelligence da scan archiviata/live (opzionale).
 */
data class OptimizationContext(
    val appId: String,
    val telemetry: FlowTelemetry? = null,
    val scanIntel: ScanIntelligenceBundle? = null,
)
