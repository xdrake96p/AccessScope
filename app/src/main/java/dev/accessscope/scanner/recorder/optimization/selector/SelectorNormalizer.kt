/**
 * Normalizzazione viewId per export Maestro id-first.
 */
package dev.accessscope.scanner.recorder.optimization.selector

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Normalizza `package:id/name` su tap/input/wait.
 */
object SelectorNormalizer {

    /**
     * Applica [MaestroSelectorHeuristics.normalizeViewId] su tutte le azioni con id.
     *
     * @param actions Azioni da normalizzare.
     * @param appId Package target.
     * @return Copia con id normalizzati.
     */
    fun normalizeViewIds(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (appId.isBlank()) return actions
        return actions.map { action ->
            when (action) {
                is RecordedAction.Tap -> {
                    val normalized = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId)
                        ?: action.viewId
                    // Preferisci testo se id è solo shell di layout.
                    val viewId = if (
                        (
                            MaestroSelectorHeuristics.isStructuralContainerViewId(normalized) ||
                                MaestroSelectorHeuristics.isAmbiguousSharedViewId(normalized)
                            ) &&
                        (!action.text.isNullOrBlank() || !action.contentDescription.isNullOrBlank())
                    ) {
                        null
                    } else {
                        normalized
                    }
                    action.copy(viewId = viewId)
                }
                is RecordedAction.DoubleTap -> {
                    val normalized = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId)
                        ?: action.viewId
                    val viewId = if (
                        (
                            MaestroSelectorHeuristics.isStructuralContainerViewId(normalized) ||
                                MaestroSelectorHeuristics.isAmbiguousSharedViewId(normalized)
                            ) &&
                        (!action.text.isNullOrBlank() || !action.contentDescription.isNullOrBlank())
                    ) {
                        null
                    } else {
                        normalized
                    }
                    action.copy(viewId = viewId)
                }
                is RecordedAction.LongPress -> {
                    val normalized = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId)
                        ?: action.viewId
                    val viewId = if (
                        (
                            MaestroSelectorHeuristics.isStructuralContainerViewId(normalized) ||
                                MaestroSelectorHeuristics.isAmbiguousSharedViewId(normalized)
                            ) &&
                        (!action.text.isNullOrBlank() || !action.contentDescription.isNullOrBlank())
                    ) {
                        null
                    } else {
                        normalized
                    }
                    action.copy(viewId = viewId)
                }
                is RecordedAction.InputText -> action.copy(
                    viewId = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId) ?: action.viewId,
                )
                is RecordedAction.EraseText -> action.copy(
                    viewId = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId) ?: action.viewId,
                )
                is RecordedAction.Wait -> action.copy(
                    visibleId = MaestroSelectorHeuristics.normalizeViewId(action.visibleId, appId)
                        ?: action.visibleId,
                )
                is RecordedAction.ScrollUntilVisible -> action.copy(
                    visibleId = MaestroSelectorHeuristics.normalizeViewId(action.visibleId, appId)
                        ?: action.visibleId,
                )
                is RecordedAction.AssertVisible -> action.copy(
                    viewId = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId) ?: action.viewId,
                )
                is RecordedAction.AssertNotVisible -> action.copy(
                    viewId = MaestroSelectorHeuristics.normalizeViewId(action.viewId, appId) ?: action.viewId,
                )
                else -> action
            }
        }
    }
}
