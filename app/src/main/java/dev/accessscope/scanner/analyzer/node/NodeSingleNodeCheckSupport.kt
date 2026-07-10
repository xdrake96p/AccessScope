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
