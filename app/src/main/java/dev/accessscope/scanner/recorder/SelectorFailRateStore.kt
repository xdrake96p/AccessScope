/**
 * Aggregatore fail-rate selettori Maestro: promuove rami catena dopo fallimenti ripetuti.
 */
package dev.accessscope.scanner.recorder

import android.content.Context

/**
 * Conta fallimenti per chiave tap (flowId + viewId/text) e decide promozione.
 *
 * @param context Contesto app.
 */
class SelectorFailRateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Registra un fallimento tap; se ≥ [THRESHOLD] restituisce `true` (promuovere catena).
     */
    fun recordFailure(flowId: String, viewId: String?, text: String?): Boolean {
        val key = key(flowId, viewId, text)
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return next >= THRESHOLD
    }

    /** Azzera contatore dopo heal riuscito. */
    fun clear(flowId: String, viewId: String?, text: String?) {
        prefs.edit().remove(key(flowId, viewId, text)).apply()
    }

    /** Contatore corrente (test/debug). */
    fun count(flowId: String, viewId: String?, text: String?): Int =
        prefs.getInt(key(flowId, viewId, text), 0)

    companion object {
        const val THRESHOLD = 2
        private const val PREFS = "maestro_selector_fail_rate"

        private fun key(flowId: String, viewId: String?, text: String?): String =
            "$flowId|${viewId.orEmpty()}|${text.orEmpty()}"
    }
}

/**
 * Promuove il prossimo candidato della catena su un tap fallito.
 */
object SelectorChainHealer {

    /**
     * @param action Tap fallito.
     * @return Tap con primario = prossimo ramo della catena, o `null` se non c’è successivo.
     */
    fun promoteNext(action: RecordedAction.Tap): RecordedAction.Tap? {
        val chain = action.selectorChain
        if (chain.size < 2) return null
        val next = chain[1]
        val rest = chain.drop(1)
        return action.copy(
            viewId = next.viewId,
            text = next.text ?: action.text,
            contentDescription = next.contentDescription ?: action.contentDescription,
            pointPercentX = next.pointPercentX,
            pointPercentY = next.pointPercentY,
            selectorChain = rest,
        )
    }

    /**
     * Applica promozione sul flusso se il fallimento è un Tap riconoscibile.
     */
    fun applyPromotion(
        actions: List<RecordedAction>,
        failedIndex: Int,
    ): List<RecordedAction>? {
        val failed = actions.getOrNull(failedIndex) as? RecordedAction.Tap ?: return null
        val promoted = promoteNext(failed) ?: return null
        return actions.toMutableList().also { it[failedIndex] = promoted }
    }

    /** Parsing grezzo di "Step N: ..." → indice 0-based. */
    fun parseStepIndex(error: String?): Int? {
        if (error.isNullOrBlank()) return null
        val m = Regex("""Step\s+(\d+)""", RegexOption.IGNORE_CASE).find(error) ?: return null
        return m.groupValues[1].toIntOrNull()?.let { it - 1 }
    }
}
