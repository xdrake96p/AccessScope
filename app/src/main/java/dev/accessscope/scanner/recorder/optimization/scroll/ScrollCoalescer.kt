/**
 * Coalescenza degli scroll consecutivi in step `scrollUntilVisible` (piano M1-A2).
 */
package dev.accessscope.scanner.recorder.optimization.scroll

import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Collassa run di scroll ripetuti nella stessa direzione.
 *
 * Regole:
 * - run di ≥2 scroll stessa direzione — tollerando `Wait`/`WaitForAnimation`/`HideKeyboard`
 *   interposti, che non interrompono la sequenza logica (es. `scroll → waitForAnimationToEnd →
 *   scroll`, il pattern reale osservato su it.nexi.bff/MPS) — seguito da un `Tap`/`AssertVisible`/
 *   `InputText` con selettore → un solo [RecordedAction.ScrollUntilVisible] sul target + il target;
 * - stesso caso ma senza elementi interposti e senza bersaglio utilizzabile → un solo
 *   [RecordedAction.Scroll], comportamento storico invariato;
 * - run con elementi interposti ma senza bersaglio promuovibile alla fine → non si rischia di
 *   scartare informazione: la sotto-sequenza viene riemessa esattamente com'era.
 */
object ScrollCoalescer {

    /**
     * Applica la coalescenza agli scroll del flusso.
     *
     * @param actions Azioni in ordine temporale.
     * @return Flusso con scroll normalizzati.
     */
    fun coalesce(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.size < 2) return actions
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val current = actions[i]
            if (current !is RecordedAction.Scroll) {
                out += current
                i++
                continue
            }
            val direction = current.direction
            val interleaved = mutableListOf<RecordedAction>()
            var end = i
            var count = 0
            while (end < actions.size) {
                val candidate = actions[end]
                when {
                    candidate is RecordedAction.Scroll && candidate.direction == direction -> {
                        end++
                        count++
                    }
                    count > 0 && isBenignBetweenScrolls(candidate) -> {
                        interleaved += candidate
                        end++
                    }
                    else -> break
                }
            }
            val next = actions.getOrNull(end)
            val target = next?.let(::scrollTargetOf)
            if (count >= 2 && target != null) {
                val (targetId, targetText) = target
                if (!targetId.isNullOrBlank() || !targetText.isNullOrBlank()) {
                    // Gli elementi interposti (wait/hideKeyboard) diventano ridondanti:
                    // scrollUntilVisible incorpora già l'attesa implicita di ogni passo.
                    out += RecordedAction.ScrollUntilVisible(
                        packageName = current.packageName,
                        visibleId = targetId,
                        visibleText = if (targetId.isNullOrBlank()) targetText else null,
                        direction = direction,
                        timestampMs = current.timestampMs,
                    )
                } else {
                    // Bersaglio senza selettore utilizzabile: non possiamo puntare a nulla →
                    // scroll singolo, ma il target resta consumato qui sotto.
                    out += current
                }
                out += next!!
                i = end + 1
            } else if (interleaved.isEmpty()) {
                // Nessun elemento interposto: comportamento storico, run collassato in un solo scroll.
                out += current
                i = end
            } else {
                // Elementi interposti ma nessun bersaglio promuovibile: non rischiare, riemetti
                // la sotto-sequenza esattamente com'era.
                for (idx in i until end) out += actions[idx]
                i = end
            }
        }
        return out
    }

    private fun isBenignBetweenScrolls(action: RecordedAction): Boolean =
        action is RecordedAction.Wait ||
            action is RecordedAction.WaitForAnimation ||
            action is RecordedAction.HideKeyboard

    /** ID o testo/cd del bersaglio su cui puntare lo scroll, se [action] ne espone uno. */
    private fun scrollTargetOf(action: RecordedAction): Pair<String?, String?>? = when (action) {
        is RecordedAction.Tap -> action.viewId to (action.text ?: action.contentDescription)
        is RecordedAction.AssertVisible -> action.viewId to action.text
        is RecordedAction.InputText -> action.viewId to null
        else -> null
    }
}
