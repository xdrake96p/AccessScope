/**
 * Risolve quali modelli Gemini supportano generateContent per la API key corrente.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.util.AppFileLogger
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Interroga `GET /v1beta/models` e ordina i modelli Flash utili per Maestro.
 */
object GeminiModelResolver {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val cache = ConcurrentHashMap<String, CachedModels>()

    /** Preferenza modelli (prefisso); 3.5 first — 2.5 spesso non disponibile per chiavi nuove. */
    private val PREFERRED_PREFIXES = listOf(
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash",
        "gemini-3-flash",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
    )

    private val DEFAULT_STATIC_FALLBACK = listOf(
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-1.5-flash-8b",
        "gemini-1.5-flash",
    )

    private data class CachedModels(
        val models: List<String>,
        val fetchedAtMs: Long,
    )

    /**
     * Restituisce modelli da provare per [apiKey], con cache breve.
     *
     * @param preferredFirst Modello che ha funzionato l'ultima volta (se ancora in lista).
     * @param refresh Se true ignora cache.
     */
    fun resolve(
        apiKey: String,
        preferredFirst: String? = null,
        refresh: Boolean = false,
    ): List<String> {
        val keyHash = apiKey.hashCode().toString()
        if (!refresh) {
            cache[keyHash]?.let { cached ->
                if (System.currentTimeMillis() - cached.fetchedAtMs < CACHE_TTL_MS) {
                    return orderWithPreferred(cached.models, preferredFirst)
                }
            }
        }
        val listed = fetchFromApi(apiKey)
        if (listed.isNotEmpty()) {
            cache[keyHash] = CachedModels(listed, System.currentTimeMillis())
            AppFileLogger.info("GeminiReview", "models_list count=${listed.size} first=${listed.firstOrNull()}")
            return orderWithPreferred(listed, preferredFirst)
        }
        AppFileLogger.info("GeminiReview", "models_list_empty using_static_fallback")
        return orderWithPreferred(DEFAULT_STATIC_FALLBACK, preferredFirst)
    }

    /** Invalida cache (dopo errore persistente). */
    fun invalidate(apiKey: String) {
        cache.remove(apiKey.hashCode().toString())
    }

    private fun orderWithPreferred(models: List<String>, preferredFirst: String?): List<String> {
        val effective = when {
            preferredFirst.isNullOrBlank() -> null
            preferredFirst == MaestroReviewSettingsStore.MODEL_AUTO -> null
            preferredFirst in models -> preferredFirst
            else -> models.firstOrNull { it.startsWith(preferredFirst) }
        }
        if (effective == null) return models
        return listOf(effective) + models.filterNot { it == effective }
    }

    private fun fetchFromApi(apiKey: String): List<String> {
        return runCatching {
            val url = URL("$BASE_URL/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use(BufferedReader::readText)
                if (code !in 200..299) {
                    AppFileLogger.info("GeminiReview", "models_list_http_$code ${body.take(200)}")
                    return emptyList()
                }
                parseModels(body)
            } finally {
                conn.disconnect()
            }
        }.getOrElse {
            AppFileLogger.info("GeminiReview", "models_list_fail ${it.message}")
            emptyList()
        }
    }

    private fun parseModels(json: String): List<String> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("models") ?: return emptyList()
        val raw = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val methods = o.optJSONArray("supportedGenerationMethods") ?: continue
                var supportsGenerate = false
                for (j in 0 until methods.length()) {
                    if (methods.optString(j) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
                if (!supportsGenerate) continue
                val fullName = o.optString("name", "")
                val short = fullName.removePrefix("models/")
                if (short.isBlank()) continue
                if (shouldSkipModel(short)) continue
                add(short)
            }
        }
        return raw.sortedWith(modelComparator())
    }

    private fun shouldSkipModel(name: String): Boolean {
        val lower = name.lowercase()
        if ("embedding" in lower) return true
        if ("tts" in lower) return true
        if ("image" in lower && "flash" !in lower) return true
        if ("live" in lower) return true
        if ("exp" in lower && "preview" !in lower) return true
        return false
    }

    private fun modelComparator(): Comparator<String> = Comparator { a, b ->
        val rankA = PREFERRED_PREFIXES.indexOfFirst { a.startsWith(it) }.let { if (it < 0) 999 else it }
        val rankB = PREFERRED_PREFIXES.indexOfFirst { b.startsWith(it) }.let { if (it < 0) 999 else it }
        if (rankA != rankB) rankA - rankB else a.compareTo(b)
    }

    private const val CACHE_TTL_MS = 10 * 60 * 1000L
}
