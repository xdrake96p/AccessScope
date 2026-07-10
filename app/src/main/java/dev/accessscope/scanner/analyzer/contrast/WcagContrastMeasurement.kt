/**
 * Misurazione contrasto testo e controlli UI da bitmap.
 */
package dev.accessscope.scanner.analyzer.contrast

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs

internal object WcagContrastMeasurement {
        fun isReliableMeasurement(result: WcagContrastTypes.ContrastResult): Boolean {
            if (result.ratio < WcagContrastTypes.MIN_RELIABLE_RATIO) return false
            if (result.samplesUsed < WcagContrastTypes.MIN_SAMPLES) return false
            val separation = abs(WcagContrastMath.relativeLuminance(result.foreground) - WcagContrastMath.relativeLuminance(result.background))
            return separation >= WcagContrastTypes.MIN_LUMINANCE_SEPARATION
        }

        /**
         * Misura il contrasto del testo campionando pixel all'interno del bounds e nell'anello di sfondo.
         *
         * @param bitmap Screenshot della schermata corrente.
         * @param bounds Area del nodo testuale in coordinate schermo.
         * @param isLargeText `true` se il testo è classificato come "grande" secondo WCAG.
         * @return [WcagContrastTypes.ContrastResult] con rapporto e confidenza, oppure `null` se il campionamento fallisce.
         */
        fun measureTextContrast(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean): WcagContrastTypes.ContrastResult? {
            val fgSamples = WcagContrastSampling.sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.15f)
            if (fgSamples.isEmpty()) return null

            val bgSamples = WcagContrastSampling.sampleBackgroundRing(bitmap, bounds)
            if (bgSamples.isEmpty()) return null

            val fg = WcagContrastSampling.resolveEffectiveForeground(WcagContrastSampling.percentileColorByLuminance(fgSamples, percentile = 0.25))
            val bg = WcagContrastSampling.resolveEffectiveBackground(WcagContrastSampling.percentileColorByLuminance(bgSamples, percentile = 0.75))
            val ratio = WcagContrastMath.contrastRatio(fg, bg)

            val separation = abs(WcagContrastMath.relativeLuminance(fg) - WcagContrastMath.relativeLuminance(bg))
            val confidence = (0.55f + separation.coerceIn(0.0, 0.45).toFloat() +
                (fgSamples.size / 16f).coerceAtMost(0.2f)).coerceAtMost(0.98f)

            return WcagContrastTypes.ContrastResult(
                ratio = ratio,
                foreground = fg,
                background = bg,
                confidence = confidence,
                samplesUsed = fgSamples.size + bgSamples.size,
                backgroundSamples = bgSamples,
                foregroundSamples = fgSamples,
            )
        }

        /**
         * Variante per testo su superfici "button-like": campiona lo sfondo dentro i bounds.
         *
         * Serve a evitare falsi positivi quando l'anello esterno cade su card/parent con colore diverso
         * rispetto al background reale del bottone/CTA.
         */
        fun measureTextContrastWithInnerBackground(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean): WcagContrastTypes.ContrastResult? {
            val fgSamples = WcagContrastSampling.sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.18f)
            if (fgSamples.isEmpty()) return null

            // Per lo sfondo del bottone campioniamo internamente ai bordi (ma dentro la shape).
            val bgSamples = WcagContrastSampling.sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.28f)
            if (bgSamples.isEmpty()) return null

            val fg = WcagContrastSampling.resolveEffectiveForeground(WcagContrastSampling.percentileColorByLuminance(fgSamples, percentile = 0.25))
            val bg = WcagContrastSampling.resolveEffectiveBackground(WcagContrastSampling.percentileColorByLuminance(bgSamples, percentile = 0.75))
            val ratio = WcagContrastMath.contrastRatio(fg, bg)

            val separation = abs(WcagContrastMath.relativeLuminance(fg) - WcagContrastMath.relativeLuminance(bg))
            val confidence = (0.60f + separation.coerceIn(0.0, 0.35).toFloat() +
                (fgSamples.size / 16f).coerceAtMost(0.2f)).coerceAtMost(0.98f)

            return WcagContrastTypes.ContrastResult(
                ratio = ratio,
                foreground = fg,
                background = bg,
                confidence = confidence,
                samplesUsed = fgSamples.size + bgSamples.size,
                backgroundSamples = bgSamples,
                foregroundSamples = fgSamples,
            )
        }

        /**
         * Misura il contrasto di un controllo UI non testuale (icona, pulsante grafico).
         *
         * @param bitmap Screenshot della schermata corrente.
         * @param bounds Area del controllo in coordinate schermo.
         * @return [WcagContrastTypes.ContrastResult] con rapporto e confidenza, oppure `null` se il campionamento fallisce.
         */
        fun measureUiContrast(bitmap: Bitmap, bounds: Rect): WcagContrastTypes.ContrastResult? {
            val fgSamples = WcagContrastSampling.sampleGrid(bitmap, bounds, grid = 3, insetPercent = 0.2f)
            val bgSamples = WcagContrastSampling.sampleBackgroundRing(bitmap, bounds)
            if (fgSamples.isEmpty() || bgSamples.isEmpty()) return null
            val fg = WcagContrastSampling.resolveEffectiveForeground(WcagContrastSampling.percentileColorByLuminance(fgSamples, percentile = 0.25))
            val bg = WcagContrastSampling.resolveEffectiveBackground(WcagContrastSampling.percentileColorByLuminance(bgSamples, percentile = 0.75))
            val separation = abs(WcagContrastMath.relativeLuminance(fg) - WcagContrastMath.relativeLuminance(bg))
            val confidence = (0.60f + separation.coerceIn(0.0, 0.35).toFloat()).coerceAtMost(0.90f)
            return WcagContrastTypes.ContrastResult(
                ratio = WcagContrastMath.contrastRatio(fg, bg),
                foreground = fg,
                background = bg,
                confidence = confidence,
                samplesUsed = fgSamples.size + bgSamples.size,
                backgroundSamples = bgSamples,
                foregroundSamples = fgSamples,
            )
        }
}
