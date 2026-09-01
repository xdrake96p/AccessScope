/**
 * Esito Play / Validate Maestro con telemetria selettori vincenti.
 */
package dev.accessscope.scanner.recorder.model

/**
 * Selettore della catena che ha funzionato a Play (per auto-heal).
 *
 * @property stepIndex Indice 0-based nello stream play.
 * @property originalViewId viewId dello step prima del resolve.
 * @property originalText text dello step prima del resolve.
 * @property candidate Candidato usato.
 * @property chainIndex Posizione nella catena (0 = primario).
 */
data class SelectorWin(
    val stepIndex: Int,
    val originalViewId: String? = null,
    val originalText: String? = null,
    val candidate: SelectorCandidate,
    val chainIndex: Int,
)

/**
 * Risultato di [dev.accessscope.scanner.recorder.FlowPlayer.play] o validate.
 *
 * @property error Messaggio errore step, o `null` se ok.
 * @property selectorWins Tap risolti con ramo non-primario della catena.
 * @property validateFailures Indici step non trovati in validate (find-only).
 * @property divergences Note su rami "morbidi" del Play in-app che il `maestro` CLI non ha
 * (segreto non risolto, fallback selettore/coordinate/PIN-pad, wait soft-fail) — non cambiano
 * l'esito ([isSuccess] resta vero), ma un flusso verde qui non garantisce che `maestro test`
 * verifichi esattamente le stesse cose.
 */
data class PlayOutcome(
    val error: String? = null,
    val selectorWins: List<SelectorWin> = emptyList(),
    val validateFailures: List<Int> = emptyList(),
    val divergences: List<String> = emptyList(),
) {
    val isSuccess: Boolean get() = error == null && validateFailures.isEmpty()
}
