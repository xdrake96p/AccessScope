package dev.accessscope.scanner.util

import android.util.Log
import org.json.JSONObject

/** Debug session 90548f — rimuovere dopo verifica. */
object DebugTrace {
    private const val TAG = "ASDBG_90548f"

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        // #region agent log
        try {
            val payload = JSONObject().apply {
                put("sessionId", "90548f")
                put("runId", runId)
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("data", JSONObject(data))
            }
            Log.i(TAG, payload.toString())
        } catch (_: Exception) {
        }
        // #endregion
    }
}
