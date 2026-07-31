/**
 * Campionamento pixel da screenshot per stima colori foreground/background.
 */
package dev.accessscope.scanner.analyzer.contrast

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

internal object WcagContrastSampling {
        internal fun percentileColorByLuminance(colors: List<Int>, percentile: Double): Int {
            val sorted = colors.sortedBy { WcagContrastMath.relativeLuminance(it) }
            val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }

        /**
         * Campiona una griglia di pixel all'interno del bounds con inset percentuale.
         *
         * @param bitmap Screenshot sorgente.
         * @param bounds Area di campionamento.
         * @param grid Dimensione della griglia (grid × grid punti).
         * @param insetPercent Percentuale di riduzione del bounds per evitare bordi.
         * @return Lista di colori ARGB opachi (alpha > 200).
         */
        internal fun sampleGrid(bitmap: Bitmap, bounds: Rect, grid: Int, insetPercent: Float): List<Int> {
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

        /**
         * Campiona pixel in un anello attorno al bounds per stimare il colore di sfondo.
         *
         * @param bitmap Screenshot sorgente.
         * @param bounds Area centrale attorno alla quale campionare lo sfondo.
         * @return Lista di colori ARGB opachi campionati ai lati e agli angoli esterni.
         */
        internal fun sampleBackgroundRing(bitmap: Bitmap, bounds: Rect): List<Int> {
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
        internal fun sampleColor(bitmap: Bitmap, x: Int, y: Int): Int? {
            if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return null
            return bitmap.getPixel(x, y)
        }
        internal fun resolveEffectiveBackground(sampled: Int): Int {
            if (Color.alpha(sampled) >= 250) return sampled
            return WcagContrastMath.compositeOverWhite(sampled)
        }

        /**
         * Risolve il colore effettivo del testo (foreground), compositando i pixel
         * semi-trasparenti sullo sfondo realmente campionato — non su bianco fisso, altrimenti
         * il contrasto risulterebbe sbagliato per testo semi-trasparente su superfici scure.
         *
         * @param sampled Colore campionato dal foreground.
         * @param background Colore di sfondo già risolto (vedi [resolveEffectiveBackground]).
         */
        internal fun resolveEffectiveForeground(sampled: Int, background: Int): Int {
            if (Color.alpha(sampled) >= 250) return sampled
            return WcagContrastMath.compositeOverWhite(sampled, base = background)
        }
}
