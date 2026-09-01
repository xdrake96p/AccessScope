/**
 * Ottimizza le azioni grezze della registrazione prima di export/persistenza (Maestro Beta).
 *
 * Facade pubblica che delega a [dev.accessscope.scanner.recorder.optimization.FlowOptimizationPipeline].
 */
package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.optimization.FlowOptimizationPipeline
import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner

/**
 * Coalesce input, dedupe tap, rimozione scroll/tap spurio e inserimento wait/hideKeyboard.
 */
object FlowOptimizer {

    /**
     * Applica tutte le ottimizzazioni e arricchimenti export.
     *
     * @param actions Azioni grezze dalla registrazione o dall’editor.
     * @param context Telemetria/scan intelligence opzionale.
     * @return Lista ottimizzata pronta per YAML / Play / store.
     */
    fun optimize(actions: List<RecordedAction>, context: OptimizationContext? = null): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val appId = context?.appId?.takeIf { it.isNotBlank() }
            ?: actions.firstNotNullOfOrNull { it.packageName.takeIf { it.isNotBlank() } }.orEmpty()
        val ctx = context ?: OptimizationContext(appId = appId)
        return FlowOptimizationPipeline.optimize(actions, ctx.copy(appId = appId))
    }

    /**
     * Pulisce azioni già salvate per replay: solo rumore SystemUI/progress.
     * Non rimuove hideKeyboard, wait o tap su campi aggiunti dall’editor.
     */
    fun sanitizeForPlay(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val appId = actions.firstOrNull { it is RecordedAction.LaunchApp }?.packageName
            ?: actions.firstOrNull {
                it.packageName.isNotBlank() && !MaestroSelectorHeuristics.isForeignUiPackage(it.packageName)
            }?.packageName
            .orEmpty()
        return FlowOptimizationPipeline.sanitizeForPlay(actions, appId)
    }

    fun coalesceInputText(actions: List<RecordedAction>): List<RecordedAction> =
        FlowOptimizationPipeline.coalesceInputText(actions)

    fun dedupeTaps(actions: List<RecordedAction>): List<RecordedAction> =
        FlowOptimizationPipeline.dedupeTaps(actions)

    fun dropNoiseScrolls(actions: List<RecordedAction>): List<RecordedAction> =
        NoiseActionFilter.dropNoiseScrolls(actions)

    fun dropNoiseTaps(actions: List<RecordedAction>): List<RecordedAction> =
        NoiseActionFilter.dropNoiseTaps(actions)

    fun dropFocusTapsBeforeInput(actions: List<RecordedAction>): List<RecordedAction> =
        NoiseActionFilter.dropFocusTapsBeforeInput(actions)

    fun dropNoiseWaits(actions: List<RecordedAction>): List<RecordedAction> =
        NoiseActionFilter.dropNoiseWaits(actions)

    /**
     * Segnala se degli `Scroll` sono spariti tra [actions] grezze e [optimized] senza una
     * `ScrollUntilVisible` a spiegare la riduzione — vedi
     * [dev.accessscope.scanner.recorder.optimization.FlowOptimizationPipeline.auditScrollCardinality].
     */
    fun auditScrollCardinality(actions: List<RecordedAction>, optimized: List<RecordedAction>): List<String> =
        FlowOptimizationPipeline.auditScrollCardinality(actions, optimized)

    /**
     * Inserisce wait dopo launch/navigazione (non hideKeyboard automatico dopo input).
     */
    fun enrich(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val appId = actions.firstOrNull()?.packageName.orEmpty()
        return WaitPlanner.enrich(dedupeTaps(coalesceInputText(actions)), appId, null, null)
    }
}
