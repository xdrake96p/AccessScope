/**
 * Serializzazione JSON report esecuzione Maestro Play/Validate.
 */
package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.PlayExecutionReport
import dev.accessscope.scanner.recorder.model.PlayRunKind
import dev.accessscope.scanner.recorder.model.PlayStepResult
import dev.accessscope.scanner.recorder.model.PlayStepStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Codec JSON per [PlayExecutionReport].
 */
object PlayReportCodec {

    /** Serializza un report in JSON compatto. */
    fun toJson(report: PlayExecutionReport): String =
        JSONObject().apply {
            put("runId", report.runId)
            put("flowId", report.flowId)
            put("flowName", report.flowName)
            put("appId", report.appId)
            put("appLabel", report.appLabel)
            put("kind", report.kind.name)
            put("startedAtMs", report.startedAtMs)
            put("finishedAtMs", report.finishedAtMs)
            put("clearState", report.clearState)
            put("totalSteps", report.totalSteps)
            put("passedSteps", report.passedSteps)
            put("failedSteps", report.failedSteps)
            put("skippedOptionalSteps", report.skippedOptionalSteps)
            put("success", report.success)
            put("errorMessage", report.errorMessage)
            put("selectorWinsCount", report.selectorWinsCount)
            put("steps", stepsArray(report.steps))
            put("divergences", JSONArray(report.divergences))
        }.toString(2)

    /** Deserializza un report; `null` se JSON invalido. */
    fun fromJson(json: String): PlayExecutionReport? = runCatching {
        val o = JSONObject(json)
        PlayExecutionReport(
            runId = o.getString("runId"),
            flowId = o.getString("flowId"),
            flowName = o.getString("flowName"),
            appId = o.getString("appId"),
            appLabel = o.optString("appLabel", o.getString("appId")),
            kind = PlayRunKind.valueOf(o.getString("kind")),
            startedAtMs = o.getLong("startedAtMs"),
            finishedAtMs = o.getLong("finishedAtMs"),
            clearState = o.optBoolean("clearState", false),
            totalSteps = o.getInt("totalSteps"),
            passedSteps = o.getInt("passedSteps"),
            failedSteps = o.getInt("failedSteps"),
            skippedOptionalSteps = o.optInt("skippedOptionalSteps", 0),
            success = o.getBoolean("success"),
            errorMessage = o.optString("errorMessage").ifBlank { null },
            steps = parseSteps(o.getJSONArray("steps")),
            divergences = parseStringList(o.optJSONArray("divergences")),
            selectorWinsCount = o.optInt("selectorWinsCount", 0),
        )
    }.getOrNull()

    /** Deserializza elenco report da file `{id}.reports.json`. */
    fun fromJsonArray(json: String): List<PlayExecutionReport> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                fromJson(item.toString())?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())

    /** Serializza elenco report. */
    fun toJsonArray(reports: List<PlayExecutionReport>): String {
        val arr = JSONArray()
        reports.forEach { arr.put(JSONObject(toJson(it))) }
        return arr.toString(2)
    }

    private fun stepsArray(steps: List<PlayStepResult>): JSONArray {
        val arr = JSONArray()
        steps.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("index", s.index)
                    put("summary", s.summary)
                    put("actionType", s.actionType)
                    put("status", s.status.name)
                    put("dataUsed", s.dataUsed)
                    put("error", s.error)
                    put("note", s.note)
                },
            )
        }
        return arr
    }

    private fun parseSteps(arr: JSONArray): List<PlayStepResult> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                PlayStepResult(
                    index = o.getInt("index"),
                    summary = o.getString("summary"),
                    actionType = o.optString("actionType", "Unknown"),
                    status = PlayStepStatus.valueOf(o.getString("status")),
                    dataUsed = o.optString("dataUsed").ifBlank { null },
                    error = o.optString("error").ifBlank { null },
                    note = o.optString("note").ifBlank { null },
                ),
            )
        }
    }

    private fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
}
