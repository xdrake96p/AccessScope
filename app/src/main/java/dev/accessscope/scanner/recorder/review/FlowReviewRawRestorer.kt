/**
 * Reinserisce azioni grezze REC perse dalla pipeline di ottimizzazione prima della review AI.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction

/**
 * Merge timeline grezza in draft ottimizzato preservando ordine temporale.
 */
object FlowReviewRawRestorer {

    /**
     * @param raw Azioni REC grezze.
     * @param optimized Draft post-[FlowOptimizer].
     * @return Lista con tap/input/scroll mancanti reinseriti.
     */
    fun restore(raw: List<RecordedAction>, optimized: List<RecordedAction>): List<RecordedAction> {
        if (raw.isEmpty()) return optimized
        if (optimized.isEmpty()) return raw
        val out = mutableListOf<RecordedAction>()
        var optIdx = 0
        raw.forEach { rawAction ->
            when (rawAction) {
                is RecordedAction.LaunchApp -> {
                    if (out.none { it is RecordedAction.LaunchApp }) {
                        out += rawAction
                    }
                    while (optIdx < optimized.size && optimized[optIdx] is RecordedAction.LaunchApp) {
                        if (out.none { it is RecordedAction.LaunchApp }) out += optimized[optIdx]
                        optIdx++
                    }
                }
                is RecordedAction.Tap -> {
                    val match = findNextMatchingTap(rawAction, optimized, optIdx)
                    if (match != null) {
                        while (optIdx < match) {
                            out += optimized[optIdx]
                            optIdx++
                        }
                        out += optimized[optIdx]
                        optIdx++
                    } else {
                        out += rawAction
                    }
                }
                is RecordedAction.InputText -> {
                    val match = findNextInput(rawAction, optimized, optIdx)
                    if (match != null) {
                        while (optIdx < match) {
                            out += optimized[optIdx]
                            optIdx++
                        }
                        out += optimized[optIdx]
                        optIdx++
                    } else {
                        out += rawAction.copy(
                            text = maskIfPassword(rawAction),
                        )
                    }
                }
                is RecordedAction.Scroll -> {
                    val match = findNextScroll(rawAction, optimized, optIdx)
                    if (match != null) {
                        while (optIdx < match) {
                            out += optimized[optIdx]
                            optIdx++
                        }
                        out += optimized[optIdx]
                        optIdx++
                    } else {
                        out += rawAction
                    }
                }
                else -> {
                    if (optIdx < optimized.size && sameKind(rawAction, optimized[optIdx])) {
                        out += optimized[optIdx]
                        optIdx++
                    } else {
                        out += rawAction
                    }
                }
            }
        }
        while (optIdx < optimized.size) {
            out += optimized[optIdx]
            optIdx++
        }
        return out
    }

    private fun maskIfPassword(input: RecordedAction.InputText): String = when {
        input.isPassword && input.text.contains("PIN", ignoreCase = true) -> "\${PIN}"
        input.isPassword -> "\${PASSWORD}"
        else -> input.text
    }

    private fun findNextMatchingTap(
        tap: RecordedAction.Tap,
        optimized: List<RecordedAction>,
        from: Int,
    ): Int? {
        val key = tapKey(tap)
        for (i in from until optimized.size) {
            val a = optimized[i]
            if (a is RecordedAction.Tap && tapKey(a) == key) return i
        }
        return null
    }

    private fun findNextInput(
        input: RecordedAction.InputText,
        optimized: List<RecordedAction>,
        from: Int,
    ): Int? {
        for (i in from until optimized.size) {
            val a = optimized[i]
            if (a is RecordedAction.InputText && a.viewId == input.viewId) return i
        }
        return null
    }

    private fun findNextScroll(
        scroll: RecordedAction.Scroll,
        optimized: List<RecordedAction>,
        from: Int,
    ): Int? {
        for (i in from until optimized.size) {
            val a = optimized[i]
            if (a is RecordedAction.Scroll && a.direction == scroll.direction) return i
        }
        return null
    }

    private fun sameKind(a: RecordedAction, b: RecordedAction): Boolean = a::class == b::class

    private fun tapKey(tap: RecordedAction.Tap): String =
        listOfNotNull(tap.viewId, tap.text, tap.contentDescription).joinToString("|")
}
