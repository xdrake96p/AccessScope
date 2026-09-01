/**
 * Budget chiamate Gemini per tier gratuito Google AI Studio.
 */
package dev.accessscope.scanner.recorder.review

/**
 * Limiti conservativi per evitare saturazione tier free.
 */
data class GeminiReviewBudget(
    val maxApiCalls: Int = 4,
    val maxImagesPerCall: Int = 24,
    val maxYamlSyntaxChars: Int = 4_000,
    val preferLiteModelAboveSteps: Int = 40,
)

/**
 * Stima utilizzo API durante save.
 */
object GeminiReviewBudgetPlanner {

    /**
     * @param rawStepCount Step grezzi.
     * @return Budget effettivo e numero chunk pianificato.
     */
    fun plan(rawStepCount: Int): Pair<GeminiReviewBudget, Int> {
        val chunks = FlowReviewChunkPlanner.plan(rawStepCount)
        val chunkCount = chunks.size.coerceAtMost(4)
        val budget = GeminiReviewBudget(
            maxApiCalls = chunkCount,
            maxImagesPerCall = if (chunkCount > 1) 20 else 24,
            maxYamlSyntaxChars = if (chunkCount > 1) 4_000 else 8_000,
            preferLiteModelAboveSteps = 40,
        )
        return budget to chunkCount
    }

    /** Stima token input (euristica ~4 char/token). */
    fun estimateTokens(promptChars: Int, imageCount: Int): Int =
        (promptChars / 4) + (imageCount * 500)
}
