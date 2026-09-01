/**
 * Parse risposta JSON Gemini per revisione flusso Maestro.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.ActionJsonCodec
import dev.accessscope.scanner.recorder.RecordedAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deserializza risposta Gemini in [FlowReviewResult].
 */
object FlowReviewResponseParser {

    /**
     * Estrae azioni corrette e changelog da testo JSON Gemini.
     *
     * @param rawBody Testo risposta (può contenere markdown fence).
     * @param fallbackActions Azioni da usare se parse fallisce.
     * @return [FlowReviewResult] con azioni parseate o fallback.
     */
    fun parse(rawBody: String, fallbackActions: List<RecordedAction>): FlowReviewResult {
        val jsonText = extractJson(rawBody)
        return runCatching {
            val root = JSONObject(jsonText)
            val actionsArr = root.optJSONArray("corrected_actions")
                ?: error("corrected_actions mancante")
            val actions = ActionJsonCodec.fromJson(actionsArr.toString())
            if (actions.isEmpty()) error("corrected_actions vuoto")
            val changes = parseChanges(root.optJSONArray("changes"))
            FlowReviewResult(
                correctedActions = actions,
                changes = changes,
                usedFallback = false,
            )
        }.getOrElse { err ->
            FlowReviewResult(
                correctedActions = fallbackActions,
                changes = emptyList(),
                usedFallback = true,
                errorMessage = err.message ?: "parse_error",
            )
        }
    }

    private fun parseChanges(arr: JSONArray?): List<FlowReviewChange> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    FlowReviewChange(
                        stepIndex = o.optInt("stepIndex", -1),
                        code = o.optString("code", "CHANGE"),
                        message = o.optString("message", ""),
                    ),
                )
            }
        }
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        fence.find(trimmed)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return trimmed
    }
}
