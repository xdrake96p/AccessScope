package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object WcagContrast {

    const val MIN_TEXT_CONTRAST = 4.5
    const val MIN_LARGE_TEXT_CONTRAST = 3.0
    const val MIN_NON_TEXT_CONTRAST = 3.0
    const val MIN_RELIABLE_RATIO = 1.15
    const val MIN_LUMINANCE_SEPARATION = 0.08
    const val MIN_SAMPLES = 8

    data class ContrastResult(
        val ratio: Double,
        val foreground: Int,
        val background: Int,
        val confidence: Float,
        val samplesUsed: Int,
    )

    fun isReliableMeasurement(result: ContrastResult): Boolean {
        if (result.ratio < MIN_RELIABLE_RATIO) return false
        if (result.samplesUsed < MIN_SAMPLES) return false
        val separation = abs(relativeLuminance(result.foreground) - relativeLuminance(result.background))
        return separation >= MIN_LUMINANCE_SEPARATION
    }

    fun measureTextContrast(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean): ContrastResult? {
        val fgSamples = sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.15f)
        if (fgSamples.isEmpty()) return null

        val bgSamples = sampleBackgroundRing(bitmap, bounds)
        if (bgSamples.isEmpty()) return null

        val fg = percentileColorByLuminance(fgSamples, percentile = 0.25)
        val bg = percentileColorByLuminance(bgSamples, percentile = 0.75)
        val ratio = contrastRatio(fg, bg)

        val separation = abs(relativeLuminance(fg) - relativeLuminance(bg))
        val confidence = (0.55f + separation.coerceIn(0.0, 0.45).toFloat() +
            (fgSamples.size / 16f).coerceAtMost(0.2f)).coerceAtMost(0.98f)

        return ContrastResult(ratio, fg, bg, confidence, fgSamples.size + bgSamples.size)
    }

    fun measureUiContrast(bitmap: Bitmap, bounds: Rect): ContrastResult? {
        val fgSamples = sampleGrid(bitmap, bounds, grid = 3, insetPercent = 0.2f)
        val bgSamples = sampleBackgroundRing(bitmap, bounds)
        if (fgSamples.isEmpty() || bgSamples.isEmpty()) return null
        val fg = percentileColorByLuminance(fgSamples, percentile = 0.25)
        val bg = percentileColorByLuminance(bgSamples, percentile = 0.75)
        val separation = abs(relativeLuminance(fg) - relativeLuminance(bg))
        val confidence = (0.60f + separation.coerceIn(0.0, 0.35).toFloat()).coerceAtMost(0.90f)
        return ContrastResult(
            ratio = contrastRatio(fg, bg),
            foreground = fg,
            background = bg,
            confidence = confidence,
            samplesUsed = fgSamples.size + bgSamples.size,
        )
    }

    private fun percentileColorByLuminance(colors: List<Int>, percentile: Double): Int {
        val sorted = colors.sortedBy { relativeLuminance(it) }
        val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun sampleGrid(bitmap: Bitmap, bounds: Rect, grid: Int, insetPercent: Float): List<Int> {
        val insetX = (bounds.width() * insetPercent).toInt()
        val insetY = (bounds.height() * insetPercent).toInt()
        val left = bounds.left + insetX
        val top = bounds.top + insetY
        val right = bounds.right - insetX
        val bottom = bounds.bottom - insetY
        if (left >= right || top >= bottom) return emptyList()

        val samples = mutableListOf<Int>()
        for (row in 0 until grid) {
            for (col in 0 until grid) {
                val x = left + (right - left) * col / (grid - 1).coerceAtLeast(1)
                val y = top + (bottom - top) * row / (grid - 1).coerceAtLeast(1)
                sampleColor(bitmap, x, y)?.let(samples::add)
            }
        }
        return samples.filter { Color.alpha(it) > 200 }
    }

    private fun sampleBackgroundRing(bitmap: Bitmap, bounds: Rect): List<Int> {
        val ring = (6 * (bounds.width().coerceAtLeast(bounds.height()) / 48f)).toInt().coerceIn(4, 12)
        val points = listOf(
            bounds.left - ring to bounds.centerY(),
            bounds.right + ring to bounds.centerY(),
            bounds.centerX() to bounds.top - ring,
            bounds.centerX() to bounds.bottom + ring,
            bounds.left - ring to bounds.top - ring,
            bounds.right + ring to bounds.bottom + ring,
        )
        return points.mapNotNull { (x, y) -> sampleColor(bitmap, x, y) }
            .filter { Color.alpha(it) > 200 }
    }

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
        return if (normalized <= 0.03928) normalized / 12.92
        else ((normalized + 0.055) / 1.055).pow(2.4)
    }

    private fun sampleColor(bitmap: Bitmap, x: Int, y: Int): Int? {
        if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return null
        return bitmap.getPixel(x, y)
    }

    fun isLargeText(boundsHeightPx: Int, density: Float): Boolean {
        return boundsHeightPx >= (18 * density).toInt()
    }
}
