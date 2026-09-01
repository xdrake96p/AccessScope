/**
 * Serializzazione report revisione AI Maestro su `{id}.review.json`.
 */
package dev.accessscope.scanner.recorder.review

import org.json.JSONArray
import org.json.JSONObject

/**
 * Payload completo report save (Gemini + reconcile YAML).
 */
data class FlowReviewReportPayload(
    val review: FlowReviewResult,
    val reconcile: YamlReconcileResult?,
)

/**
 * Codec JSON per esito revisione Gemini (solo metadati testuali, no screenshot).
 */
object FlowReviewReportCodec {

    /**
     * Serializza report completo per persistenza.
     */
    fun toJson(payload: FlowReviewReportPayload): String = JSONObject().apply {
        val result = payload.review
        put("usedFallback", result.usedFallback)
        put("source", result.source.name.lowercase())
        putOpt("errorMessage", result.errorMessage)
        put("modelUsed", result.modelUsed.orEmpty())
        put("apiCalls", result.apiCalls)
        put("imagesSent", result.imagesSent)
        put("estimatedInputTokens", result.estimatedInputTokens)
        put("chunkCount", result.chunkCount)
        put(
            "changes",
            JSONArray().apply {
                result.changes.forEach { c ->
                    put(
                        JSONObject().apply {
                            put("stepIndex", c.stepIndex)
                            put("code", c.code)
                            put("message", c.message)
                        },
                    )
                }
            },
        )
        put("correctedStepCount", result.correctedActions.size)
        payload.reconcile?.let { r ->
            put(
                "yamlReconcile",
                JSONObject().apply {
                    put("presentedSource", r.presentedSource.name.lowercase())
                    putOpt("reason", r.reason)
                    put("appScore", r.appScore)
                    put("geminiScore", r.geminiScore)
                    put("mergedFixCount", r.mergedFixCount)
                    put("presentedStepCount", r.presentedActions.size)
                },
            )
        }
    }.toString(2)

    /** Retrocompat: solo review result. */
    fun toJson(result: FlowReviewResult): String =
        toJson(FlowReviewReportPayload(result, reconcile = null))

    private fun JSONObject.putOpt(key: String, value: String?) {
        if (value != null) put(key, value)
    }
}
