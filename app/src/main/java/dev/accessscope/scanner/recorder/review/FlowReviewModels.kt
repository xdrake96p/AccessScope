/**
 * Modello richiesta/risposta revisione flusso Maestro con Gemini Flash.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordingVisualContext

/**
 * Input completo per confronto incrociato registrazione vs YAML draft.
 *
 * @param appId Package Android target.
 * @param flowName Nome flusso.
 * @param rawActions Azioni grezze da REC (fonte di verità utente).
 * @param optimizedActions Azioni post-[FlowOptimizer].
 * @param yamlDraft YAML generato dall'app prima della revisione AI.
 * @param telemetry Telemetria fingerprint/transizioni/quiescence.
 * @param visualContext Screenshot JPEG + albero a11y per step (RAM).
 * @param scanIntel Intelligence da scan WCAG (opzionale).
 * @param draftLintSummary Segnalazioni [FlowLinter] sul draft B (testo per prompt).
 * @param yamlSyntaxReference YAML draft troncato (appendice B3, solo sintassi Maestro).
 * @param chunk Segmento corrente (null = flusso intero).
 * @param budget Limiti tier free per immagini/YAML.
 * @param lostStepsSummary Sezione A0 pre-calcolata.
 */
data class FlowReviewRequest(
    val appId: String,
    val flowName: String,
    val rawActions: List<RecordedAction>,
    val optimizedActions: List<RecordedAction>,
    val yamlDraft: String,
    val telemetry: FlowTelemetry?,
    val visualContext: RecordingVisualContext?,
    val scanIntel: ScanIntelligenceBundle? = null,
    val draftLintSummary: String? = null,
    val yamlSyntaxReference: String? = null,
    val chunk: FlowReviewChunk? = null,
    val budget: GeminiReviewBudget? = null,
    val lostStepsSummary: String? = null,
) {
    /** Richiesta slice per chunk. */
    fun forChunk(
        chunk: FlowReviewChunk,
        budget: GeminiReviewBudget,
        lostSummary: String?,
    ): FlowReviewRequest {
        val from = chunk.fromActionIndex
        val to = chunk.toActionIndexInclusive
        val visual = visualContext?.let { ctx ->
            ctx.copy(
                snapshots = ctx.snapshots.filter { it.actionIndex in from..to },
            )
        }
        return copy(
            rawActions = rawActions.filterIndexed { i, _ -> i in from..to },
            optimizedActions = optimizedActions.filterIndexed { i, _ -> i in from..to },
            visualContext = visual,
            chunk = chunk,
            budget = budget,
            lostStepsSummary = lostSummary,
            yamlSyntaxReference = yamlSyntaxReference?.take(budget.maxYamlSyntaxChars),
        )
    }
}

/** Sorgente azioni finali salvate nel flusso. */
enum class FlowReviewSource {
    /** Output validato da Gemini. */
    GEMINI,

    /** Pipeline deterministica app (fallback). */
    APP,
}

/**
 * Singola correzione applicata dalla revisione AI.
 *
 * @param stepIndex Indice step interessato (-1 se globale).
 * @param code Codice breve (es. `MISSING_WAIT`).
 * @param message Spiegazione in italiano.
 */
data class FlowReviewChange(
    val stepIndex: Int,
    val code: String,
    val message: String,
)

/**
 * Esito revisione Gemini.
 *
 * @param correctedActions Lista azioni corretta (schema ActionJsonCodec).
 * @param changes Elenco modifiche con motivazione.
 * @param usedFallback `true` se review fallita e si usa pipeline deterministica.
 * @param errorMessage Errore se fallback.
 * @param source Sorgente azioni effettive (`gemini` o `app`).
 * @param modelUsed Modello Gemini effettivamente usato.
 * @param apiCalls Numero chiamate API effettuate.
 * @param imagesSent Totale immagini inviate.
 * @param estimatedInputTokens Stima token input.
 * @param chunkCount Chunk processati.
 */
data class FlowReviewResult(
    val correctedActions: List<RecordedAction>,
    val changes: List<FlowReviewChange> = emptyList(),
    val usedFallback: Boolean = false,
    val errorMessage: String? = null,
    val source: FlowReviewSource = FlowReviewSource.APP,
    val modelUsed: String? = null,
    val apiCalls: Int = 0,
    val imagesSent: Int = 0,
    val estimatedInputTokens: Int = 0,
    val chunkCount: Int = 1,
)
