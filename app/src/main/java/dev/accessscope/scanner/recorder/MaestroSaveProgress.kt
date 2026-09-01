/**
 * Stato UI salvataggio flusso Maestro + revisione Gemini (osservabile da FlowsScreen).
 */
package dev.accessscope.scanner.recorder

/**
 * Progresso save post-REC (overlay o schermata Maestro).
 *
 * @param active `true` mentre save/review è in corso.
 * @param phase Messaggio fase corrente (es. «Revisione Gemini Flash…»).
 * @param appLabel Nome app registrata.
 * @param stepCount Step grezzi catturati (per anteprima in lista).
 * @param lastSavedFlowId Id flusso appena salvato (per refresh/highlight).
 */
data class MaestroSaveProgress(
    val active: Boolean = false,
    val phase: String = "",
    val appLabel: String? = null,
    val stepCount: Int = 0,
    val lastSavedFlowId: String? = null,
)
