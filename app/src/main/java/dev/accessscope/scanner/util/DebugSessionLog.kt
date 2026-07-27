/**
 * Logger debug temporaneo → ingest Cursor (sessione ff64d3).
 * Rimuovere dopo verifica.
 */
package dev.accessscope.scanner.util

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Invia NDJSON al debug ingest locale (via `adb reverse tcp:7281`) e append su file host se presente.
 * Non usa org.json/Android Log così i test JVM non crashano.
 */
object DebugSessionLog {
    private const val ENDPOINT =
        "http://127.0.0.1:7281/ingest/c856a5fd-5141-4a81-9782-0f04d168ddf8"
    private const val SESSION = "ff64d3"
    private const val LOG_PATH =
        "/Users/davidevisconti/Documents/AccessScope/.cursor/debug-ff64d3.log"
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * @param hypothesisId Id ipotesi (A–E / H1…).
     * @param location File/funzione.
     * @param message Descrizione evento.
     * @param data Payload non sensibile (niente password in chiaro).
     */
    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
    ) {
        runCatching {
            val dataJson = data.entries.joinToString(",") { (k, v) ->
                "\"${esc(k)}\":${jsonValue(v)}"
            }
            val payload =
                """{"sessionId":"$SESSION","hypothesisId":"${esc(hypothesisId)}","location":"${esc(location)}","message":"${esc(message)}","timestamp":${System.currentTimeMillis()},"runId":"post-fix","data":{$dataJson}}"""
            // #region agent log
            runCatching {
                val f = File(LOG_PATH)
                if (f.parentFile?.exists() == true) {
                    synchronized(this) { f.appendText(payload + "\n") }
                }
            }
            // #endregion
            executor.execute {
                runCatching {
                    val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("X-Debug-Session-Id", SESSION)
                        doOutput = true
                        connectTimeout = 1500
                        readTimeout = 1500
                    }
                    conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    conn.responseCode
                    conn.disconnect()
                }
            }
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        else -> "\"${esc(v.toString())}\""
    }
}
