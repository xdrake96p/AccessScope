/**
 * Heal statico selettori: promuove testo/cd quando l’id primario è strutturale o assente.
 *
 * Competenza separata dal ranking a Play e dal fail-rate store.
 */
package dev.accessscope.scanner.recorder.quality

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.SelectorCandidate
import dev.accessscope.scanner.recorder.optimization.selector.SelectorRanker

/**
 * Correzioni deterministiche pre-export senza telemetria di Play.
 */
object StaticSelectorHealer {

    /**
     * Per ogni tap: se l’id è strutturale/rumore/assente e la catena ha testo o cd, promuove quel ramo.
     * Rimuove point dai campi primari quando esiste un selettore semantico.
     *
     * @param actions Azioni già ottimizzate (o quasi).
     * @return Azioni con selettori primari riparati dove possibile.
     */
    fun heal(actions: List<RecordedAction>): List<RecordedAction> =
        actions.map { action ->
            if (action !is RecordedAction.Tap) return@map action
            healTap(action, actions)
        }

    private fun healTap(
        action: RecordedAction.Tap,
        all: List<RecordedAction>,
    ): RecordedAction.Tap {
        val chain = action.selectorChain.ifEmpty {
            SelectorRanker.buildChain(action, all)
        }
        val structuralOrNoise = action.viewId != null && (
            MaestroSelectorHeuristics.isStructuralContainerViewId(action.viewId) ||
                MaestroSelectorHeuristics.isNoiseViewId(action.viewId) ||
                MaestroSelectorHeuristics.isAmbiguousSharedViewId(action.viewId)
            )
        val noId = action.viewId.isNullOrBlank()
        val semantic = chain.firstOrNull { c ->
            !c.text.isNullOrBlank() || !c.contentDescription.isNullOrBlank()
        } ?: chain.firstOrNull { !it.viewId.isNullOrBlank() && !isBadId(it.viewId) }

        var next = action.copy(selectorChain = chain)

        if ((structuralOrNoise || noId) && semantic != null) {
            next = next.copy(
                viewId = when {
                    !semantic.viewId.isNullOrBlank() && !isBadId(semantic.viewId) -> semantic.viewId
                    structuralOrNoise -> null
                    else -> next.viewId
                },
                text = semantic.text ?: next.text,
                contentDescription = semantic.contentDescription ?: next.contentDescription,
            )
        }

        // Se abbiamo testo/cd, non esportare point come primario.
        if (!next.text.isNullOrBlank() || !next.contentDescription.isNullOrBlank() ||
            (!next.viewId.isNullOrBlank() && !isBadId(next.viewId))
        ) {
            next = next.copy(
                pointPercentX = null,
                pointPercentY = null,
                weakSelector = false,
            )
        }

        // Point-only residuo: resta weak.
        if (next.viewId.isNullOrBlank() && next.text.isNullOrBlank() &&
            next.contentDescription.isNullOrBlank()
        ) {
            next = next.copy(weakSelector = true)
        }

        return next.copy(
            selectorChain = SelectorRanker.buildChain(next, all),
        )
    }

    private fun isBadId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return true
        return MaestroSelectorHeuristics.isStructuralContainerViewId(viewId) ||
            MaestroSelectorHeuristics.isNoiseViewId(viewId) ||
            MaestroSelectorHeuristics.isAmbiguousSharedViewId(viewId)
    }
}
