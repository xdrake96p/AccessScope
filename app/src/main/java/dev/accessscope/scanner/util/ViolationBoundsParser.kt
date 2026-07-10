/**
 * Parser per le coordinate bounds delle violazioni (formato legacy stringa o campi strutturati).
 */
package dev.accessscope.scanner.util

import android.graphics.Rect
import dev.accessscope.scanner.data.AccessibilityViolation

object ViolationBoundsParser {

    private val NodeSnapshotPattern = Regex("""(\d+)×(\d+)\s*px\s*@\((-?\d+),(-?\d+)\)""")
    private val LegacyPattern = Regex("""(-?\d+),(-?\d+)-(-?\d+),(-?\d+)""")

    /** Estrae un [Rect] dalla violazione, preferendo i campi strutturati. */
    fun rect(violation: AccessibilityViolation): Rect? {
        structuredRect(violation)?.let { return it }
        return parse(violation.bounds)
    }

    /** Parse del formato `"WxH px @(left,top)"` o legacy `left,top-right,bottom`. */
    fun parse(bounds: String?): Rect? {
        if (bounds.isNullOrBlank()) return null
        NodeSnapshotPattern.find(bounds)?.let { match ->
            val width = match.groupValues[1].toIntOrNull() ?: return null
            val height = match.groupValues[2].toIntOrNull() ?: return null
            val left = match.groupValues[3].toIntOrNull() ?: return null
            val top = match.groupValues[4].toIntOrNull() ?: return null
            if (width <= 0 || height <= 0) return null
            return Rect(left, top, left + width, top + height)
        }
        LegacyPattern.find(bounds)?.let { match ->
            val left = match.groupValues[1].toIntOrNull() ?: return null
            val top = match.groupValues[2].toIntOrNull() ?: return null
            val right = match.groupValues[3].toIntOrNull() ?: return null
            val bottom = match.groupValues[4].toIntOrNull() ?: return null
            if (right <= left || bottom <= top) return null
            return Rect(left, top, right, bottom)
        }
        return null
    }

    private fun structuredRect(violation: AccessibilityViolation): Rect? {
        val left = violation.boundsLeft ?: return null
        val top = violation.boundsTop ?: return null
        val right = violation.boundsRight ?: return null
        val bottom = violation.boundsBottom ?: return null
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
    }
}
