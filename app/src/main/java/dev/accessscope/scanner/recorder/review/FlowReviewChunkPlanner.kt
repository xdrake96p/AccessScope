/**
 * Pianificazione chunk per revisione Gemini su flussi lunghi (tier free).
 */
package dev.accessscope.scanner.recorder.review

/**
 * Segmento di flusso da revisionare in una chiamata Gemini.
 */
data class FlowReviewChunk(
    val chunkIndex: Int,
    val totalChunks: Int,
    val fromActionIndex: Int,
    val toActionIndexInclusive: Int,
)

/**
 * Divide il flusso in chunk con overlap per continuità wait/selettori.
 */
object FlowReviewChunkPlanner {

    private const val SINGLE_CHUNK_MAX = 20
    private const val CHUNK_SIZE = 14
    private const val OVERLAP = 2

    /**
     * @param actionCount Numero azioni grezze.
     * @return Chunk da processare in ordine.
     */
    fun plan(actionCount: Int): List<FlowReviewChunk> {
        if (actionCount <= 0) return emptyList()
        if (actionCount <= SINGLE_CHUNK_MAX) {
            return listOf(
                FlowReviewChunk(0, 1, 0, actionCount - 1),
            )
        }
        val chunks = mutableListOf<FlowReviewChunk>()
        var start = 0
        while (start < actionCount) {
            val end = (start + CHUNK_SIZE - 1).coerceAtMost(actionCount - 1)
            chunks += FlowReviewChunk(
                chunkIndex = chunks.size,
                totalChunks = 0,
                fromActionIndex = start,
                toActionIndexInclusive = end,
            )
            if (end >= actionCount - 1) break
            start = (end + 1 - OVERLAP).coerceAtLeast(start + 1)
        }
        val total = chunks.size
        return chunks.map { it.copy(totalChunks = total) }
    }
}
