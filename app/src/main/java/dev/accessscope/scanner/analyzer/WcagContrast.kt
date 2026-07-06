package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object WcagContrast {

    const val MIN_TEXT_CONTRAST = 4.5
    const val MIN_LARGE_TEXT_CONTRAST = 3.0
    const val MIN_NON_TEXT_CONTRAST = 3.0

    fun contrastRatio(foreground: Int, background: Int): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun relativeLuminance(color: Int): Double {
        val r = channel(Color.red(color))
        val g = channel(Color.green(color))
        val b = channel(Color.blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    fun sampleForeground(bitmap: Bitmap, bounds: Rect): Int? =
        sampleColor(bitmap, bounds.centerX(), bounds.centerY())

    fun sampleBackground(bitmap: Bitmap, bounds: Rect): Int? {
        val offsets = listOf(
            bounds.left - 4 to bounds.top - 4,
            bounds.right + 4 to bounds.top - 4,
            bounds.left - 4 to bounds.bottom + 4,
            bounds.right + 4 to bounds.bottom + 4,
        )
        val samples = offsets.mapNotNull { (x, y) -> sampleColor(bitmap, x, y) }
        return samples.firstOrNull()
    }

    private fun sampleColor(bitmap: Bitmap, x: Int, y: Int): Int? {
        if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return null
        return bitmap.getPixel(x, y)
    }

    fun isLargeText(boundsHeightPx: Int, density: Float): Boolean {
        val largeTextThresholdPx = (18 * density).toInt()
        return boundsHeightPx >= largeTextThresholdPx
    }
}
