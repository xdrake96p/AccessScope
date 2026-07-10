/**
 * Valuta se uno screenshot è utilizzabile per analisi pixel (contrasto, evidenze).
 */
package dev.accessscope.scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.pow

object ScreenshotAnalyzer {

    private const val GRID = 8
    private const val BLACK_LUMINANCE_THRESHOLD = 0.05
    private const val BLACK_PIXEL_FRACTION = 0.90

    /**
     * `true` se il bitmap è vuoto, troppo piccolo o quasi interamente nero (FLAG_SECURE).
     */
    fun isBlackOrEmpty(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return true
        if (bitmap.width < 2 || bitmap.height < 2) return true
        var dark = 0
        var total = 0
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val x = (bitmap.width - 1) * col / max(GRID - 1, 1)
                val y = (bitmap.height - 1) * row / max(GRID - 1, 1)
                val pixel = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                if (Color.alpha(pixel) < 128) continue
                total++
                if (relativeLuminance(pixel) < BLACK_LUMINANCE_THRESHOLD) dark++
            }
        }
        if (total == 0) return true
        return dark.toDouble() / total >= BLACK_PIXEL_FRACTION
    }

    fun isUsableForContrast(bitmap: Bitmap?): Boolean = !isBlackOrEmpty(bitmap)

    private fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(Color.red(argb))
        val g = channel(Color.green(argb))
        val b = channel(Color.blue(argb))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
