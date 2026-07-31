package dev.accessscope.scanner.analyzer.node

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType


internal object NodeSingleNodeCheckSupport {
    val NON_DESCRIPTIVE_LINKS = setOf(
        "click here", "tap here", "here", "more", "read more", "learn more", "details", "link",
        "continue", "go", "ok", "submit", "clicca qui", "qui", "altro", "leggi", "leggi tutto",
        "scopri", "continua", "dettagli", "vai", "info", "apri", "tap",
    )

    fun isNonDescriptiveLink(name: String): Boolean {
        val n = name.trim().lowercase()
        return NON_DESCRIPTIVE_LINKS.any { n == it || n.matches(Regex("^$it\\W*")) }
    }

    /** Parole chiave IT/EN che indicano un messaggio di errore visivo nel testo di un campo. */
    private val VISUAL_ERROR_KEYWORDS = setOf(
        "error", "invalid", "required field",
        "errore", "non valido", "non valida", "obbligatorio", "campo richiesto",
    )

    /**
     * Verifica se il testo del nodo sembra un messaggio di errore visivo (IT/EN), non solo inglese.
     *
     * @param text Testo del nodo da valutare.
     * @return `true` se il testo contiene una keyword di errore nota.
     */
    fun looksLikeVisualErrorText(text: String): Boolean {
        val t = text.lowercase()
        return VISUAL_ERROR_KEYWORDS.any { t.contains(it) }
    }

    fun estimateScreenArea(snapshots: List<NodeSnapshot>): Int {
        if (snapshots.isEmpty()) return 0
        var maxRight = 0
        var maxBottom = 0
        snapshots.forEach { snap ->
            maxRight = maxOf(maxRight, snap.bounds.right)
            maxBottom = maxOf(maxBottom, snap.bounds.bottom)
        }
        return maxRight * maxBottom
    }
}
