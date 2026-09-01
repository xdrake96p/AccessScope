/**
 * Merge azioni corrette da chunk Gemini multipli.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Concatena output chunk evitando duplicati nella zona overlap.
 */
object FlowReviewChunkMerger {

    private const val OVERLAP = 2

    /**
     * @param chunkResults Azioni per chunk in ordine.
     * @param chunks Metadati chunk (stesso ordine).
     * @return Lista unificata.
     */
    fun merge(
        chunkResults: List<List<RecordedAction>>,
        chunks: List<FlowReviewChunk>,
    ): List<RecordedAction> {
        if (chunkResults.isEmpty()) return emptyList()
        if (chunkResults.size == 1) return chunkResults.first()
        val out = mutableListOf<RecordedAction>()
        chunkResults.forEachIndexed { index, actions ->
            if (actions.isEmpty()) return@forEachIndexed
            if (index == 0) {
                out += actions
                return@forEachIndexed
            }
            val skip = OVERLAP.coerceAtMost(actions.size).coerceAtMost(out.size)
            out += actions.drop(skip)
        }
        return out
    }

    /**
     * Unisce changelog da tutti i chunk.
     */
    fun mergeChanges(changes: List<List<FlowReviewChange>>): List<FlowReviewChange> =
        changes.flatten()
}
