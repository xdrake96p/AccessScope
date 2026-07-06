/**
 * Tracciamento diagnostico strutturato per sessioni di debug.
 *
 * Scrive payload JSON su Logcat per analisi post-esecuzione.
 * Sessione corrente: 90548f — rimuovere dopo verifica.
 */
package dev.accessscope.scanner.util

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Logger di debug che emette eventi strutturati in formato JSON su Logcat.
 *
 * Utilizzato per correlare ipotesi, posizioni nel codice e dati contestuali
 * durante l'investigazione di problemi specifici.
 */
object DebugTrace {
    private const val TAG = "ASDBG_90548f"
    private const val INGEST_URL =
        "http://127.0.0.1:7931/ingest/ec3129d3-eabf-4d44-91e4-790542799950"
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Registra un evento di tracciamento strutturato.
     *
     * Il payload include identificativo sessione, run, ipotesi, posizione,
     * messaggio, timestamp e dati aggiuntivi serializzati in JSON.
     *
     * @param hypothesisId Identificativo dell'ipotesi sotto verifica.
     * @param location Posizione nel codice (es. «Classe.metodo:42»).
     * @param message Messaggio descrittivo dell'evento.
     * @param data Mappa di valori contestuali opzionali.
     * @param runId Identificativo dell'esecuzione (default «scroll-debug»).
     */
    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "scroll-debug",
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
            val line = payload.toString()
            Log.i(TAG, line)
            postToIngest(line)
        } catch (_: Exception) {
        }
        // #endregion
    }

    private fun postToIngest(jsonLine: String) {
        executor.execute {
            try {
                val conn = (URL(INGEST_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", "90548f")
                    doOutput = true
                    connectTimeout = 1500
                    readTimeout = 1500
                }
                conn.outputStream.use { it.write(jsonLine.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
