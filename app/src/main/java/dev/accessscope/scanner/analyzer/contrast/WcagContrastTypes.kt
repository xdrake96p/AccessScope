/**
 * Costanti e modello risultato per misurazioni contrasto WCAG.
 */
package dev.accessscope.scanner.analyzer.contrast

import android.graphics.Color

object WcagContrastTypes {
    const val MIN_TEXT_CONTRAST = 4.5
    const val MIN_LARGE_TEXT_CONTRAST = 3.0
    const val MIN_NON_TEXT_CONTRAST = 3.0
    const val MIN_RELIABLE_RATIO = 1.15
    const val MIN_LUMINANCE_SEPARATION = 0.08
    const val MIN_SAMPLES = 8
    const val COMPLEX_BG_LUMINANCE_RANGE = 0.11
    const val RASTER_CONTENT_LUMINANCE_RANGE = 0.18
    const val COMPLEX_BG_PASS_FRACTION = 0.55

    data class ContrastResult(
        val ratio: Double,
        val foreground: Int,
        val background: Int,
        val confidence: Float,
        val samplesUsed: Int,
        val backgroundSamples: List<Int> = emptyList(),
        val foregroundSamples: List<Int> = emptyList(),
    )
}
