/**
 * Tracciamento diagnostico strutturato per sessioni di debug.
 *
 * Scrive payload JSON su Logcat per analisi post-esecuzione.
 * Sessione corrente: 90548f — rimuovere dopo verifica.
 */
package dev.accessscope.scanner.util

import android.util.Log
import org.json.JSONObject

/**
 * Logger di debug che emette eventi strutturati in formato JSON su Logcat.
 *
 * Utilizzato per correlare ipotesi, posizioni nel codice e dati contestuali
 * durante l'investigazione di problemi specifici.
 */
object DebugTrace {
    private const val TAG = "ASDBG_90548f"

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
     * @param runId Identificativo dell'esecuzione (default «pre-fix»).
     */
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
