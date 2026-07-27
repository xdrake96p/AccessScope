/**
 * Coalescenza degli scroll consecutivi in step `scrollUntilVisible` (piano M1-A2).
 */
package dev.accessscope.scanner.recorder.optimization.scroll

import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Collassa run di scroll ripetuti nella stessa direzione.
 *
 * Regole:
 * - run di ≥2 scroll stessa direzione seguito da un tap con selettore →
 *   un solo [RecordedAction.ScrollUntilVisible] sul target del tap + il tap;
 * - run di scroll non seguito da tap (o tap senza selettore) → un solo [RecordedAction.Scroll].
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
            // Run di scroll consecutivi nella stessa direzione.
            var end = i
            var count = 0
            while (end < actions.size) {
                val candidate = actions[end] as? RecordedAction.Scroll
                if (candidate == null || candidate.direction != current.direction) break
                end++
                count++
            }
            val next = actions.getOrNull(end)
            if (count >= 2 && next is RecordedAction.Tap) {
                val targetId = next.viewId
                val targetText = next.text ?: next.contentDescription
                if (!targetId.isNullOrBlank() || !targetText.isNullOrBlank()) {
                    out += RecordedAction.ScrollUntilVisible(
                        packageName = current.packageName,
                        visibleId = targetId,
                        visibleText = if (targetId.isNullOrBlank()) targetText else null,
                        direction = current.direction,
                        timestampMs = current.timestampMs,
                    )
                } else {
                    // Tap senza selettore: non possiamo puntare a nulla → scroll singolo.
                    out += current
                }
                out += next
                i = end + 1
            } else {
                // Run senza tap selettivo: collassa in un solo scroll.
                out += current
                i = end
            }
        }
        return out
    }
}
