/**
 * Client REST Gemini Flash per revisione flusso Maestro.
 */
package dev.accessscope.scanner.recorder.review

import android.util.Base64
import dev.accessscope.scanner.recorder.model.RecordingVisualContext
import dev.accessscope.scanner.util.AppFileLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Invoca Gemini Flash (Google AI Studio) con prompt multimodal e parse risposta.
 */
class GeminiFlashFlowReviewer(
    private val settings: MaestroReviewSettingsStore,
) {

    /** Ultimo modello usato con successo (log/debug). */
    @Volatile
    var lastUsedModel: String? = null
        private set

    @Volatile
    private var reviewDeadlineMs: Long = Long.MAX_VALUE

    @Volatile
    private var progressCallback: ((String) -> Unit)? = null

    private fun reportProgress(message: String) {
        progressCallback?.invoke(message)
    }

    private fun ensureWithinDeadline() {
        if (System.currentTimeMillis() > reviewDeadlineMs) {
            error("review_timeout: Gemini non ha risposto entro ${REVIEW_TOTAL_DEADLINE_MS / 1000}s")
        }
    }

    /**
     * Esegue revisione incrociata registrazione vs draft (singola o chunked).
     *
     * @param request Contesto completo.
     * @param onProgress Callback fase UI (es. «Revisione AI 2/4»).
     * @return [FlowReviewResult]; fallback deterministico se key/rete/parse falliscono.
     */
    fun review(
        request: FlowReviewRequest,
        onProgress: ((String) -> Unit)? = null,
    ): FlowReviewResult {
        val key = settings.apiKey.trim()
        if (key.isBlank()) {
            AppFileLogger.info("GeminiReview", "review_skip api_key_missing")
            return fallbackResult(request, "api_key_missing")
        }
        reviewDeadlineMs = System.currentTimeMillis() + REVIEW_TOTAL_DEADLINE_MS
        progressCallback = onProgress
        val (budget, _) = GeminiReviewBudgetPlanner.plan(request.rawActions.size)
        val chunks = FlowReviewChunkPlanner.plan(request.rawActions.size)
            .take(budget.maxApiCalls)
        val diff = FlowReviewDiffAnalyzer.analyze(
            request.rawActions,
            request.optimizedActions,
            request.telemetry,
            request.visualContext,
        )
        if (chunks.size <= 1) {
            reportProgress("Revisione Gemini Flash…")
            return reviewSingle(request.copy(budget = budget, lostStepsSummary = diff.lostStepsSummary))
        }
        var apiCalls = 0
        var imagesSent = 0
        var tokens = 0
        val correctedChunks = mutableListOf<List<dev.accessscope.scanner.recorder.RecordedAction>>()
        val allChanges = mutableListOf<FlowReviewChange>()
        chunks.forEach { chunk ->
            ensureWithinDeadline()
            reportProgress("Revisione AI ${chunk.chunkIndex + 1}/${chunk.totalChunks}…")
            val sub = request.forChunk(chunk, budget, diff.lostStepsSummary)
            val result = reviewSingle(sub)
            apiCalls++
            imagesSent += result.imagesSent
            tokens += result.estimatedInputTokens
            if (result.usedFallback) {
                return result.copy(
                    apiCalls = apiCalls,
                    imagesSent = imagesSent,
                    estimatedInputTokens = tokens,
                    chunkCount = chunks.size,
                )
            }
            correctedChunks += result.correctedActions
            allChanges += result.changes
        }
        val merged = FlowReviewChunkMerger.merge(correctedChunks, chunks)
        val mergedResult = FlowReviewResult(
            correctedActions = merged,
            changes = FlowReviewChunkMerger.mergeChanges(listOf(allChanges)),
            usedFallback = false,
            source = FlowReviewSource.GEMINI,
            modelUsed = lastUsedModel,
            apiCalls = apiCalls,
            imagesSent = imagesSent,
            estimatedInputTokens = tokens,
            chunkCount = chunks.size,
        )
        return FlowReviewValidator.validate(mergedResult, request.optimizedActions, request.rawActions)
            .copy(
                modelUsed = lastUsedModel,
                apiCalls = apiCalls,
                imagesSent = imagesSent,
                estimatedInputTokens = tokens,
                chunkCount = chunks.size,
            )
    }

    private fun reviewSingle(request: FlowReviewRequest): FlowReviewResult {
        AppFileLogger.info(
            "GeminiReview",
            "review_start rawSteps=${request.rawActions.size} optimized=${request.optimizedActions.size}",
        )
        val parts = MaestroFlowReviewPromptBuilder.build(request)
        return runCatching {
            val body = buildRequestBody(parts, request.visualContext)
            val responseText = postGenerateContent(body, request)
            val parsed = FlowReviewResponseParser.parse(responseText, request.optimizedActions)
            val validated = FlowReviewValidator.validate(
                parsed,
                request.optimizedActions,
                request.rawActions,
            )
            val images = parts.imageStepIndices.size
            val tokens = GeminiReviewBudgetPlanner.estimateTokens(parts.text.length, images)
            if (validated.usedFallback) {
                AppFileLogger.info("GeminiReview", "review_invalid err=${validated.errorMessage}")
            } else {
                AppFileLogger.info(
                    "GeminiReview",
                    "review_ok changes=${validated.changes.size} steps=${validated.correctedActions.size}",
                )
            }
            validated.copy(
                modelUsed = lastUsedModel,
                apiCalls = 1,
                imagesSent = images,
                estimatedInputTokens = tokens,
                chunkCount = request.chunk?.totalChunks ?: 1,
            )
        }.getOrElse { err ->
            AppFileLogger.info("GeminiReview", "review_failed ${err.message}")
            fallbackResult(request, err.message)
        }
    }

    private fun fallbackResult(request: FlowReviewRequest, error: String?): FlowReviewResult =
        FlowReviewResult(
            correctedActions = request.optimizedActions,
            usedFallback = true,
            errorMessage = error,
            source = FlowReviewSource.APP,
        )

    /** Ping minimale API (validazione key in Impostazioni / schermata Maestro). */
    fun testConnection(): ApiKeyTestResult {
        val key = settings.apiKey.trim()
        if (key.isBlank()) {
            return ApiKeyTestResult(ok = false, message = "Inserisci una API key in Impostazioni → Maestro AI")
        }
        return runCatching {
            val preferred = resolvePreferredFirst(rawStepCount = 0)
            GeminiModelResolver.resolve(key, preferredFirst = preferred, refresh = true)
            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "Rispondi solo: OK")),
                        ),
                    ),
                )
            }
            val text = postGenerateContent(body.toString(), rawStepCount = 0, readTimeoutMs = TEST_READ_TIMEOUT_MS)
            if (text.isBlank()) {
                ApiKeyTestResult(ok = false, message = "Risposta vuota da Gemini")
            } else {
                ApiKeyTestResult(ok = true, message = "API key valida (${lastUsedModel.orEmpty()}) — ${text.take(50)}")
            }
        }.getOrElse { err ->
            ApiKeyTestResult(ok = false, message = err.message ?: "Errore connessione")
        }
    }

    private fun buildRequestBody(
        parts: MaestroFlowReviewPromptBuilder.PromptParts,
        visual: RecordingVisualContext?,
    ): String {
        val contentParts = JSONArray()
        contentParts.put(JSONObject().put("text", parts.text))
        val snapshotsByIndex = visual?.snapshots?.associateBy { it.actionIndex }.orEmpty()
        parts.imageStepIndices.forEach { idx ->
            val snap = snapshotsByIndex[idx] ?: return@forEach
            val jpeg = snap.bestImageBytes() ?: return@forEach
            val wireframe = snap.jpegBytes == null && snap.wireframeJpeg != null
            contentParts.put(
                JSONObject().apply {
                    put(
                        "inline_data",
                        JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                        },
                    )
                },
            )
            val label = if (wireframe) {
                "Wireframe sintetico step $idx (schermata protetta)"
            } else {
                "Screenshot step indice $idx"
            }
            contentParts.put(JSONObject().put("text", label))
        }
        return JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", contentParts)))
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.35)
                    put("responseMimeType", "application/json")
                },
            )
        }.toString()
    }

    private fun postGenerateContent(jsonBody: String, request: FlowReviewRequest): String =
        postGenerateContent(jsonBody, request.rawActions.size, REVIEW_READ_TIMEOUT_MS)

    private fun postGenerateContent(jsonBody: String, rawStepCount: Int, readTimeoutMs: Int): String {
        ensureWithinDeadline()
        val key = settings.apiKey.trim()
        val models = GeminiModelResolver.resolve(
            apiKey = key,
            preferredFirst = resolvePreferredFirst(rawStepCount),
        ).take(MAX_MODELS_TO_TRY)
        if (models.isEmpty()) error("Nessun modello Gemini trovato per questa API key")
        var lastError: String? = null
        for (model in models) {
            var attempt = 0
            while (attempt < GeminiApiRetryPolicy.MAX_ATTEMPTS_PER_MODEL) {
                ensureWithinDeadline()
                attempt++
                reportProgress(
                    if (attempt == 1) {
                        "Chiamata Gemini ($model)…"
                    } else {
                        "Gemini retry $attempt/${GeminiApiRetryPolicy.MAX_ATTEMPTS_PER_MODEL} ($model)…"
                    },
                )
                AppFileLogger.info(
                    "GeminiReview",
                    "api_request_start model=$model attempt=$attempt bodyBytes=${jsonBody.length}",
                )
                val result = postGenerateContentForModel(key, model, jsonBody, readTimeoutMs)
                when {
                    result.success -> {
                        lastUsedModel = model
                        settings.lastWorkingModel = model
                        AppFileLogger.info("GeminiReview", "api_model_ok model=$model attempt=$attempt")
                        return result.text!!
                    }
                    result.notFound -> {
                        AppFileLogger.info(
                            "GeminiReview",
                            "api_model_skip model=$model err=${result.error?.take(120)}",
                        )
                        lastError = result.error
                        break
                    }
                    result.fatal -> error(result.error ?: "Errore API Gemini")
                    result.retryable && attempt < GeminiApiRetryPolicy.MAX_ATTEMPTS_PER_MODEL -> {
                        val waitMs = GeminiApiRetryPolicy.backoffMs(attempt)
                            .coerceAtMost(reviewDeadlineMs - System.currentTimeMillis())
                        if (waitMs <= 0L) error("review_timeout: Gemini non ha risposto in tempo")
                        AppFileLogger.info(
                            "GeminiReview",
                            "api_retry model=$model attempt=$attempt waitMs=$waitMs err=${result.error?.take(80)}",
                        )
                        Thread.sleep(waitMs)
                    }
                    result.retryable -> {
                        AppFileLogger.info(
                            "GeminiReview",
                            "api_model_rate_limit model=$model attempts=$attempt",
                        )
                        lastError = result.error
                        break
                    }
                    else -> {
                        lastError = result.error
                        break
                    }
                }
            }
        }
        GeminiModelResolver.invalidate(key)
        error(lastError ?: "Nessun modello Gemini disponibile")
    }

    /**
     * Modello da provare per primo: preferenza utente, ultimo OK, o lite su flussi lunghi.
     */
    private fun resolvePreferredFirst(rawStepCount: Int): String? = when {
        settings.preferredModel != MaestroReviewSettingsStore.MODEL_AUTO -> settings.preferredModel
        rawStepCount >= 40 -> "gemini-3.5-flash-lite"
        !settings.lastWorkingModel.isNullOrBlank() -> settings.lastWorkingModel
        else -> null
    }

    private data class PostResult(
        val success: Boolean,
        val notFound: Boolean = false,
        val retryable: Boolean = false,
        val fatal: Boolean = false,
        val text: String? = null,
        val error: String? = null,
    )

    private fun postGenerateContentForModel(
        apiKey: String,
        model: String,
        jsonBody: String,
        readTimeoutMs: Int,
    ): PostResult {
        val url = URL("$BASE_URL/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use(BufferedReader::readText)
            if (code in 200..299) {
                return PostResult(success = true, text = extractTextFromResponse(response))
            }
            val error = parseApiError(response, code)
            return when {
                code == 401 || code == 400 -> PostResult(success = false, fatal = true, error = error)
                GeminiApiRetryPolicy.isModelUnavailable(code, error) ->
                    PostResult(success = false, notFound = true, error = error)
                GeminiApiRetryPolicy.isRetryable(code, error) ->
                    PostResult(success = false, retryable = true, error = error)
                else -> PostResult(success = false, error = error)
            }
        } catch (e: java.net.SocketTimeoutException) {
            return PostResult(
                success = false,
                retryable = true,
                error = "timeout: ${e.message}",
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun parseApiError(response: String, code: Int): String {
        val fromJson = runCatching {
            JSONObject(response).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return fromJson ?: "HTTP $code: ${response.take(300)}"
    }

    private fun extractTextFromResponse(json: String): String {
        val root = JSONObject(json)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val CONNECT_TIMEOUT_MS = 20_000
        /** Timeout lettura singola chiamata review (multimodal può essere lenta). */
        private const val REVIEW_READ_TIMEOUT_MS = 45_000
        /** Timeout test API key in Impostazioni. */
        private const val TEST_READ_TIMEOUT_MS = 30_000
        /** Deadline totale review: oltre → fallback pipeline app. */
        private const val REVIEW_TOTAL_DEADLINE_MS = 90_000L
        /** Max modelli da provare per chiamata (evita attese infinite). */
        private const val MAX_MODELS_TO_TRY = 3
    }
}

/** Esito test API key Gemini. */
data class ApiKeyTestResult(
    val ok: Boolean,
    val message: String,
)
