/**
 * Formule WCAG: luminanza, rapporto contrasto, classificazione testo grande.
 */
package dev.accessscope.scanner.analyzer.contrast

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal object WcagContrastMath {
        fun contrastRatio(foreground: Int, background: Int): Double {
            val l1 = relativeLuminance(foreground)
            val l2 = relativeLuminance(background)
            val lighter = max(l1, l2)
            val darker = min(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        /**
         * Formatta un colore ARGB Android in esadecimale per report e dettaglio tecnico.
         *
         * @param color Colore ARGB (`0xAARRGGBB`).
         * @return `#RRGGBB` se opaco, altrimenti `#AARRGGBB`.
         */
        fun formatArgbHex(color: Int): String {
            val alpha = (color ushr 24) and 0xFF
            val red = (color ushr 16) and 0xFF
            val green = (color ushr 8) and 0xFF
            val blue = color and 0xFF
            return if (alpha < 255) {
                String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
            } else {
                String.format("#%02X%02X%02X", red, green, blue)
            }
        }

        /**
         * Calcola la luminanza relativa WCAG di un colore sRGB.
         *
         * @param color Colore ARGB.
         * @return Luminanza relativa normalizzata (0.0–1.0).
         */
        fun relativeLuminance(color: Int): Double {
            val r = channel(Color.red(color))
            val g = channel(Color.green(color))
            val b = channel(Color.blue(color))
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /**
         * Applica la correzione gamma sRGB a un singolo canale colore.
         *
         * @param value Valore del canale (0–255).
         * @return Valore lineare normalizzato per il calcolo della luminanza.
         */
        internal fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92
            else ((normalized + 0.055) / 1.055).pow(2.4)
        }

        /**
         * Legge il colore di un singolo pixel dal bitmap, con controllo dei limiti.
         *
         * @param bitmap Screenshot sorgente.
         * @param x Coordinata X in pixel.
         * @param y Coordinata Y in pixel.
         * @return Colore ARGB del pixel, oppure `null` se fuori dai limiti.
         */
        private fun sampleColor(bitmap: Bitmap, x: Int, y: Int): Int? {
            if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return null
            return bitmap.getPixel(x, y)
        }

        /**
         * Determina se il testo è "grande" secondo WCAG in base all'altezza in pixel.
         *
         * @param boundsHeightPx Altezza del bounds del testo in pixel.
         * @param density Densità dello schermo (dp → px).
         * @return `true` se l'altezza è ≥ 18 dp (equivalente a testo grande WCAG).
         */
        fun isLargeText(boundsHeightPx: Int, density: Float): Boolean {
            return boundsHeightPx >= (18 * density).toInt()
        }

        /**
         * Classifica testo grande usando dimensione stimata, bounds e ID noti (es. importi 35sp).
         *
         * @param snap Snapshot del nodo testuale.
         * @param density Densità dello schermo.
         * @param largeTextViewIds ID view che in Nexi/banking usano sempre testo grande.
         */
        fun isLargeText(
            snap: NodeSnapshot,
            density: Float,
            largeTextViewIds: Set<String> = emptySet(),
        ): Boolean {
            val id = snap.viewId?.substringAfterLast('/')?.lowercase().orEmpty()
            if (id in largeTextViewIds) return true
            // Se abbiamo una stima in sp, preferiscila ai bounds: i bounds includono spesso line-height/padding
            // e promuovono testo normale (es. 14sp) a "large" generando falsi negativi/positivi sulle soglie.
            snap.textSizeSp?.let { return it >= 18f }
            return isLargeText(snap.bounds.height(), density)
        }
        fun compositeOverWhite(color: Int, base: Int = Color.WHITE): Int {
            val alpha = Color.alpha(color) / 255.0
            if (alpha >= 0.99) return color
            val r = (Color.red(color) * alpha + Color.red(base) * (1 - alpha)).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * alpha + Color.green(base) * (1 - alpha)).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * alpha + Color.blue(base) * (1 - alpha)).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }

        /** Converte un colore ARGB in esadecimale #RRGGBB per report debug. */
        fun colorToHex(color: Int): String =
            String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
}
