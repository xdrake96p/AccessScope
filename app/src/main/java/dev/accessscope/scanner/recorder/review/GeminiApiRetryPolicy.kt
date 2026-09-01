/**
 * Politica retry e classificazione errori API Gemini (rate limit, modello assente).
 */
package dev.accessscope.scanner.recorder.review

/**
 * Classifica risposte HTTP Gemini e calcola backoff per retry Maestro review.
 */
object GeminiApiRetryPolicy {

    /** Tentativi per modello prima di passare al successivo. */
    const val MAX_ATTEMPTS_PER_MODEL = 3

    /** Pausa base tra retry (ms). */
    const val BASE_BACKOFF_MS = 2_000L

    /**
     * `true` se conviene riprovare (stesso modello con backoff o modello alternativo).
     */
    fun isRetryable(httpCode: Int, errorMessage: String?): Boolean {
        if (httpCode == 429 || httpCode == 503 || httpCode == 502 || httpCode == 500) return true
        val msg = errorMessage.orEmpty().lowercase()
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("resource exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("quota") ||
            msg.contains("try again later")
    }

    /**
     * `true` se il modello non esiste o non è disponibile per questa API key — provare il prossimo.
     */
    fun isModelUnavailable(httpCode: Int, errorMessage: String?): Boolean {
        if (httpCode == 404) return true
        val msg = errorMessage.orEmpty().lowercase()
        return msg.contains("not found") ||
            msg.contains("no longer available") ||
            msg.contains("is not supported for generatecontent")
    }

    /**
     * Backoff esponenziale: 2s, 4s, 8s…
     *
     * @param attempt Numero tentativo 1-based dopo il primo fallimento.
     */
    fun backoffMs(attempt: Int): Long =
        BASE_BACKOFF_MS * (1L shl (attempt - 1).coerceAtMost(3))

    /** Timeout rete → riprova o modello alternativo. */
    fun isTimeout(message: String?): Boolean {
        val msg = message.orEmpty().lowercase()
        return msg.contains("timeout") || msg.contains("timed out")
    }
}
