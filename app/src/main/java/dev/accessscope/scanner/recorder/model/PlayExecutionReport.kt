/**
 * Report esecuzione Play/Validate Maestro — audit trail per cliente e QA.
 */
package dev.accessscope.scanner.recorder.model

/**
 * Tipo di run registrato nel report.
 */
enum class PlayRunKind {
    /** Playback completo con gesture. */
    PLAY,

    /** Dry-run find-only (Validate). */
    VALIDATE,
}

/**
 * Esito di un singolo step nel report.
 */
enum class PlayStepStatus {
    /** Step eseguito con successo. */
    PASSED,

    /** Step fallito (Required). */
    FAILED,

    /** Step Optional saltato dopo errore non bloccante. */
    SKIPPED_OPTIONAL,

    /** Step non raggiunto perché il flusso si è fermato prima. */
    NOT_RUN,
}

/**
 * Dettaglio step per report cliente.
 *
 * @property index Indice 0-based nello stream play.
 * @property summary Descrizione leggibile (es. `tapOn id=uno`).
 * @property actionType Nome classe azione (Tap, InputText, …).
 * @property status Esito step.
 * @property dataUsed Valore inserito/cercato (mascherato per segreti).
 * @property error Messaggio errore se [status] è FAILED o SKIPPED_OPTIONAL.
 * @property note Nota divergenza CI (fallback morbido) senza cambiare esito.
 */
data class PlayStepResult(
    val index: Int,
    val summary: String,
    val actionType: String,
    val status: PlayStepStatus,
    val dataUsed: String? = null,
    val error: String? = null,
    val note: String? = null,
)

/**
 * Report completo di un run Play o Validate.
 *
 * @property runId Id univoco run (UUID corto).
 * @property flowId Id flusso salvato.
 * @property flowName Nome flusso al momento del run.
 * @property appId Package target.
 * @property appLabel Etichetta app al momento del run.
 * @property kind PLAY o VALIDATE.
 * @property startedAtMs Timestamp inizio run.
 * @property finishedAtMs Timestamp fine run.
 * @property clearState Se true, cold launch prima del flusso.
 * @property totalSteps Step totali nel flusso play.
 * @property passedSteps Step con esito PASSED.
 * @property failedSteps Step con esito FAILED.
 * @property skippedOptionalSteps Step Optional saltati.
 * @property success Esito complessivo (nessun FAILED Required).
 * @property errorMessage Errore globale se il run è fallito.
 * @property steps Dettaglio per step.
 * @property divergences Note CI aggregate (fallback, segreti non risolti, …).
 * @property selectorWinsCount Selettori promossi durante il run.
 */
data class PlayExecutionReport(
    val runId: String,
    val flowId: String,
    val flowName: String,
    val appId: String,
    val appLabel: String,
    val kind: PlayRunKind,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val clearState: Boolean = false,
    val totalSteps: Int,
    val passedSteps: Int,
    val failedSteps: Int,
    val skippedOptionalSteps: Int,
    val success: Boolean,
    val errorMessage: String? = null,
    val steps: List<PlayStepResult>,
    val divergences: List<String> = emptyList(),
    val selectorWinsCount: Int = 0,
) {
    val durationMs: Long get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)

    companion object {
        /** Costruisce report da [PlayOutcome] e metadati flusso. */
        fun fromOutcome(
            runId: String,
            flow: dev.accessscope.scanner.recorder.SavedFlow,
            kind: PlayRunKind,
            startedAtMs: Long,
            finishedAtMs: Long,
            clearState: Boolean,
            totalSteps: Int,
            outcome: PlayOutcome,
        ): PlayExecutionReport {
            val steps = outcome.stepResults.ifEmpty {
                buildFallbackSteps(totalSteps, outcome)
            }
            val passed = steps.count { it.status == PlayStepStatus.PASSED }
            val failed = steps.count { it.status == PlayStepStatus.FAILED }
            val skipped = steps.count { it.status == PlayStepStatus.SKIPPED_OPTIONAL }
            return PlayExecutionReport(
                runId = runId,
                flowId = flow.id,
                flowName = flow.name,
                appId = flow.appId,
                appLabel = flow.appLabel,
                kind = kind,
                startedAtMs = startedAtMs,
                finishedAtMs = finishedAtMs,
                clearState = clearState,
                totalSteps = totalSteps,
                passedSteps = passed,
                failedSteps = failed,
                skippedOptionalSteps = skipped,
                success = outcome.isSuccess,
                errorMessage = outcome.error,
                steps = steps,
                divergences = outcome.divergences,
                selectorWinsCount = outcome.selectorWins.size,
            )
        }

        private fun buildFallbackSteps(totalSteps: Int, outcome: PlayOutcome): List<PlayStepResult> {
            if (totalSteps <= 0) return emptyList()
            val failIdx = outcome.error?.let { err ->
                Regex("Step (\\d+)").find(err)?.groupValues?.get(1)?.toIntOrNull()?.minus(1)
            }
            return (0 until totalSteps).map { i ->
                val status = when {
                    failIdx == null -> PlayStepStatus.PASSED
                    i < failIdx -> PlayStepStatus.PASSED
                    i == failIdx -> PlayStepStatus.FAILED
                    else -> PlayStepStatus.NOT_RUN
                }
                PlayStepResult(
                    index = i,
                    summary = "step ${i + 1}",
                    actionType = "Unknown",
                    status = status,
                    error = if (i == failIdx) outcome.error else null,
                )
            }
        }
    }
}
