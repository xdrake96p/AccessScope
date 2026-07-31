/**
 * Inserisce wait verso dismiss alert quando dopo un submit c’è un overlay bloccante noto.
 *
 * Complementa [BlockingOverlayOrderHealer]: se il dismiss è già subito dopo CONTINUA,
 * aggiunge `extendedWaitUntil` sul bottone dismiss / testo tipico così Play non
 * tenta i campi mentre l’alert è in animazione.
 */
package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.optimization.conditional.BlockingOverlayOrderHealer

/**
 * Attese mirate pre-dismiss dopo tap submit-like.
 */
object BlockingOverlayWaitPlanner {

    private const val DISMISS_WAIT_MS = 5_000L

    /**
     * Se dopo ContinuA (+ anim) c’è un dismiss bloccante senza waitUntil sul dismiss, lo aggiunge.
     *
     * @param actions Flusso già riordinato da [BlockingOverlayOrderHealer].
     * @param appId Package app.
     * @return Azioni con waitUntil sul dismiss dove manca.
     */
    fun enrich(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val pkg = actions.firstOrNull()?.packageName.orEmpty().ifBlank { appId }
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val a = actions[i]
            out += a
            val tap = a as? RecordedAction.Tap
            if (tap != null && WaitPlanner.isSubmitLikeTap(tap)) {
                // Salta wait già presenti in uscita / ingresso.
                var j = i + 1
                while (j < actions.size &&
                    (actions[j] is RecordedAction.Wait || actions[j] is RecordedAction.WaitForAnimation)
                ) {
                    out += actions[j]
                    j++
                }
                val next = actions.getOrNull(j)
                if (next != null && BlockingOverlayOrderHealer.isBlockingDismiss(next)) {
                    val dismiss = next as RecordedAction.Tap
                    val alreadyWaiting = out.takeLast(3).any { w ->
                        w is RecordedAction.Wait && (
                            (!dismiss.viewId.isNullOrBlank() && w.visibleId == dismiss.viewId) ||
                                (!dismiss.text.isNullOrBlank() && w.visibleText.equals(dismiss.text, true))
                            )
                    }
                    if (!alreadyWaiting) {
                        out += RecordedAction.Wait(
                            packageName = tap.packageName.ifBlank { pkg },
                            timeoutMs = DISMISS_WAIT_MS,
                            visibleId = dismiss.viewId,
                            visibleText = if (dismiss.viewId.isNullOrBlank()) dismiss.text else null,
                            timestampMs = tap.timestampMs + 1,
                        )
                    }
                }
                i = j
                continue
            }
            i++
        }
        return out
    }
}
