/**
 * Riordina step dismiss di overlay bloccanti (alert) prima degli input/campi.
 *
 * Caso tipico Nexi: tap «OK, HO CAPITO» scartato in REC e catturato tardi → YAML prova
 * `inputText` su `edit1` mentre `alert_pop` è ancora aperto. Questo healer sposta il
 * dismiss subito dopo il tap submit-like (CONTINUA) e i relativi wait.
 */
package dev.accessscope.scanner.recorder.optimization.conditional

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner

/**
 * Heal dell’ordine step per overlay che bloccano i campi sottostanti.
 */
object BlockingOverlayOrderHealer {

    /**
     * Sposta i dismiss alert fuori posto subito dopo i tap submit-like.
     *
     * @param actions Azioni già filtrate noise (prima o dopo WaitPlanner).
     * @return Azioni con ordine corretto; invariate se non serve.
     */
    fun reorder(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.size < 3) return actions
        var current = actions.toMutableList()
        var guard = 0
        while (guard++ < 8) {
            val moved = moveOneMisplacedDismiss(current) ?: break
            current = moved.toMutableList()
        }
        return current
    }

    /**
     * Trova un dismiss dopo un campo e lo sposta dopo il submit precedente.
     *
     * @return Nuova lista o `null` se niente da spostare.
     */
    private fun moveOneMisplacedDismiss(actions: List<RecordedAction>): List<RecordedAction>? {
        for (i in actions.indices) {
            val submit = actions[i] as? RecordedAction.Tap ?: continue
            if (!WaitPlanner.isSubmitLikeTap(submit)) continue

            val afterWaits = skipTrailingWaits(actions, i + 1)
            val segmentEnd = nextSubmitOrEnd(actions, afterWaits)
            val firstField = (afterWaits until segmentEnd).firstOrNull { isFieldInteraction(actions[it]) }
                ?: continue
            val dismissIdx = (firstField + 1 until segmentEnd).firstOrNull { isBlockingDismiss(actions[it]) }
                ?: continue

            // Già subito dopo i wait del submit → ok.
            if (dismissIdx == afterWaits) continue

            val block = extractDismissBlock(actions, dismissIdx)
            val without = actions.toMutableList()
            // Rimuovi dal fondo dell’intervallo per non invalidare indici.
            for (idx in block.indices.reversed()) {
                without.removeAt(block[idx])
            }
            val insertAt = skipTrailingWaits(without, i + 1).coerceIn(0, without.size)
            // Evita duplicati: se lo stesso dismiss è già in insertAt, skip.
            val dismissAction = actions[dismissIdx]
            if (sameDismiss(without.getOrNull(insertAt), dismissAction)) continue

            val toInsert = block.map { actions[it] }
            without.addAll(insertAt, toInsert)
            return without
        }
        return null
    }

    /**
     * Blocco da spostare: Wait/WaitForAnimation immediatamente prima del dismiss + dismiss.
     */
    private fun extractDismissBlock(actions: List<RecordedAction>, dismissIdx: Int): List<Int> {
        val idxs = mutableListOf(dismissIdx)
        var j = dismissIdx - 1
        while (j >= 0) {
            when (actions[j]) {
                is RecordedAction.Wait, is RecordedAction.WaitForAnimation -> {
                    idxs.add(0, j)
                    j--
                }
                else -> break
            }
            // Non includere wait che seguono un campo (appartengono al campo).
            if (j >= 0 && isFieldInteraction(actions[j])) {
                idxs.removeAt(0)
                break
            }
        }
        // Solo un wait pre-dismiss (evita di trascinare wait lunghi di navigazione).
        if (idxs.size > 2) {
            return listOf(idxs[idxs.lastIndex - 1], idxs.last())
        }
        return idxs
    }

    private fun skipTrailingWaits(actions: List<RecordedAction>, from: Int): Int {
        var i = from
        while (i < actions.size) {
            when (actions[i]) {
                is RecordedAction.Wait, is RecordedAction.WaitForAnimation -> i++
                else -> break
            }
        }
        return i
    }

    private fun nextSubmitOrEnd(actions: List<RecordedAction>, from: Int): Int {
        for (i in from until actions.size) {
            val t = actions[i] as? RecordedAction.Tap ?: continue
            if (isBlockingDismiss(t)) continue
            if (WaitPlanner.isSubmitLikeTap(t)) return i
        }
        return actions.size
    }

    /**
     * Interazione su campo testo/PIN che fallisce se un alert è sopra.
     */
    fun isFieldInteraction(action: RecordedAction): Boolean = when (action) {
        is RecordedAction.InputText -> true
        is RecordedAction.EraseText -> true
        is RecordedAction.Tap -> {
            val id = MaestroSelectorHeuristics.shortViewId(action.viewId)?.lowercase().orEmpty()
            id.startsWith("edit") || id.contains("input") || id.contains("password") ||
                id.contains("pin") || id.contains("otp") || id.contains("customer") ||
                id.contains("user_code") || id.contains("edt_")
        }
        else -> false
    }

    /**
     * Tap che chiude un overlay bloccante (alert / permission dismiss).
     */
    fun isBlockingDismiss(action: RecordedAction): Boolean {
        val tap = action as? RecordedAction.Tap ?: return false
        val id = MaestroSelectorHeuristics.shortViewId(tap.viewId)?.lowercase().orEmpty()
        if (id == "dismiss" || id.endsWith("_dismiss") || id == "btn_ok" || id == "btn_confirm") {
            return true
        }
        val label = listOfNotNull(tap.text, tap.contentDescription).joinToString(" ")
        if (MaestroSelectorHeuristics.isPopupDismissLabel(label)) return true
        if (label.contains("ho capito", ignoreCase = true)) return true
        return false
    }

    private fun sameDismiss(a: RecordedAction?, b: RecordedAction): Boolean {
        val t1 = a as? RecordedAction.Tap ?: return false
        val t2 = b as? RecordedAction.Tap ?: return false
        val id1 = MaestroSelectorHeuristics.shortViewId(t1.viewId)
        val id2 = MaestroSelectorHeuristics.shortViewId(t2.viewId)
        if (!id1.isNullOrBlank() && id1 == id2) return true
        val text1 = t1.text?.trim()?.lowercase().orEmpty()
        val text2 = t2.text?.trim()?.lowercase().orEmpty()
        return text1.isNotBlank() && text1 == text2
    }
}
