/**
 * Politiche affidabilità e segnalazione violazioni contrasto.
 */
package dev.accessscope.scanner.analyzer.contrast

import kotlin.math.abs

internal object WcagContrastPolicy {
        fun isComplexBackground(backgroundSamples: List<Int>): Boolean =
            isHighLuminanceVariance(backgroundSamples, WcagContrastTypes.COMPLEX_BG_LUMINANCE_RANGE)

        /**
         * `true` se l'area campionata sembra foto/illustrazione raster, non un controllo UI piatto.
         *
         * Evita falsi positivi di contrasto su ImageView e grafica decorativa.
         */
        fun isLikelyRasterImageContent(result: WcagContrastTypes.ContrastResult): Boolean {
            val fgVaried = isHighLuminanceVariance(result.foregroundSamples, WcagContrastTypes.RASTER_CONTENT_LUMINANCE_RANGE)
            if (fgVaried) return true
            return fgVaried && isComplexBackground(result.backgroundSamples)
        }

        internal fun isHighLuminanceVariance(samples: List<Int>, minRange: Double): Boolean {
            if (samples.size < 4) return false
            val luminances = samples.map { WcagContrastMath.relativeLuminance(it) }
            return (luminances.max() - luminances.min()) >= minRange
        }

        /**
         * Decide se segnalare una violazione di contrasto testo.
         *
         * Su sfondo uniforme usa il rapporto aggregato; su sfondo complesso richiede che
         * la maggioranza dei campioni intorno al testo fallisca la soglia (evita FP su hero illustrati).
         */
        fun shouldReportTextContrastFailure(
            result: WcagContrastTypes.ContrastResult,
            threshold: Double,
            isLargeText: Boolean,
        ): Boolean {
            if (result.ratio >= threshold) return false
            val bgSamples = result.backgroundSamples
            val complexBg = bgSamples.isNotEmpty() && isComplexBackground(bgSamples)
            val complexFg = isHighLuminanceVariance(
                result.foregroundSamples,
                WcagContrastTypes.COMPLEX_BG_LUMINANCE_RANGE,
            )
            if (bgSamples.isEmpty() || (!complexBg && !complexFg)) return true

            val ratios = bgSamples.map { WcagContrastMath.contrastRatio(result.foreground, it) }
            val passFraction = ratios.count { it >= threshold }.toDouble() / ratios.size

            if (passFraction >= WcagContrastTypes.COMPLEX_BG_PASS_FRACTION) return false

            // Titolo grande su illustrazione: worst-case marginale ma leggibile nella maggior parte dei punti
            if (isLargeText && result.ratio >= 2.75 && passFraction >= 0.34) return false

            // Contrasto catastrofico su tutti i campioni
            if (passFraction < 0.20 && result.ratio < 2.0) return true

            return passFraction < 0.45
        }

        /**
         * Come [shouldReportTextContrastFailure] ma con soglia più alta per icone/controlli UI.
         */
        fun shouldReportUiContrastFailure(
            result: WcagContrastTypes.ContrastResult,
            threshold: Double,
        ): Boolean {
            if (result.ratio >= threshold) return false
            val bgSamples = result.backgroundSamples
            if (bgSamples.isEmpty() || !isComplexBackground(bgSamples)) return true

            val ratios = bgSamples.map { WcagContrastMath.contrastRatio(result.foreground, it) }
            val passFraction = ratios.count { it >= threshold }.toDouble() / ratios.size
            if (passFraction >= 0.65) return false
            if (passFraction < 0.20 && result.ratio < 1.8) return true
            return passFraction < 0.40
        }
        fun minConfidenceForMeasurement(
            result: WcagContrastTypes.ContrastResult,
            boundsWidthPx: Int,
            boundsHeightPx: Int,
            density: Float,
            isSmallIcon: Boolean,
            baseMin: Float,
        ): Float {
            var min = baseMin
            if (result.ratio < 2.5) {
                val separation = abs(WcagContrastMath.relativeLuminance(result.foreground) - WcagContrastMath.relativeLuminance(result.background))
                if (separation < 0.12) min = maxOf(min, 0.82f)
            }
            val minDimPx = (24 * density).toInt()
            if (isSmallIcon && boundsWidthPx < minDimPx && boundsHeightPx < minDimPx) {
                min = maxOf(min, 0.78f)
            }
            return min
        }
}
