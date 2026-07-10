/**
 * Misurazione del contrasto colore su screenshot secondo le formule WCAG 2.x.
 *
 * Campiona pixel dall'area del nodo e da un anello di sfondo circostante per stimare
 * il rapporto di contrasto tra primo piano e sfondo, con soglie per testo normale,
 * testo grande e componenti UI non testuali.
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Utility per il calcolo del contrasto colore e la validazione dell'affidabilità della misura.
 */
object WcagContrast {

    /** Soglia WCAG AA per testo normale (rapporto minimo 4.5:1). */
    const val MIN_TEXT_CONTRAST = 4.5

    /** Soglia WCAG AA per testo grande o testo in grassetto (rapporto minimo 3:1). */
    const val MIN_LARGE_TEXT_CONTRAST = 3.0

    /** Soglia WCAG AA per componenti UI e grafica non testuale (rapporto minimo 3:1). */
    const val MIN_NON_TEXT_CONTRAST = 3.0

    /** Rapporto minimo sotto il quale la misura è considerata inaffidabile (colori quasi identici). */
    const val MIN_RELIABLE_RATIO = 1.15

    /** Separazione minima di luminanza relativa tra primo piano e sfondo per considerare la misura valida. */
    const val MIN_LUMINANCE_SEPARATION = 0.08

    /** Numero minimo di campioni pixel richiesti per una misura attendibile. */
    const val MIN_SAMPLES = 8

    /**
     * Risultato di una misurazione di contrasto colore.
     *
     * @property ratio Rapporto di contrasto calcolato (es. 4.5 significa 4.5:1).
     * @property foreground Colore ARGB stimato del primo piano.
     * @property background Colore ARGB stimato dello sfondo.
     * @property confidence Punteggio di confidenza della misura (0.0–1.0).
     * @property samplesUsed Numero totale di pixel campionati.
     * @property backgroundSamples Colori campionati per stimare lo sfondo (anello o area interna).
     */
    data class ContrastResult(
        val ratio: Double,
        val foreground: Int,
        val background: Int,
        val confidence: Float,
        val samplesUsed: Int,
        val backgroundSamples: List<Int> = emptyList(),
        val foregroundSamples: List<Int> = emptyList(),
    )

    /** Range minimo di luminanza tra campioni sfondo per considerarlo "complesso" (illustrazione/foto). */
    private const val COMPLEX_BG_LUMINANCE_RANGE = 0.11

    /** Varianza luminanza interna tipica di foto/illustrazioni raster (non icone piatte). */
    private const val RASTER_CONTENT_LUMINANCE_RANGE = 0.18

    /** Frazione minima di campioni sfondo che devono passare per ignorare un falso positivo. */
    private const val COMPLEX_BG_PASS_FRACTION = 0.55

    /**
     * Verifica se una misurazione è sufficientemente affidabile per generare una violazione.
     *
     * @param result Risultato della misurazione da validare.
     * @return `true` se rapporto, campioni e separazione di luminanza superano le soglie minime.
     */
    fun isReliableMeasurement(result: ContrastResult): Boolean {
        if (result.ratio < MIN_RELIABLE_RATIO) return false
        if (result.samplesUsed < MIN_SAMPLES) return false
        val separation = abs(relativeLuminance(result.foreground) - relativeLuminance(result.background))
        return separation >= MIN_LUMINANCE_SEPARATION
    }

    /**
     * Misura il contrasto del testo campionando pixel all'interno del bounds e nell'anello di sfondo.
     *
     * @param bitmap Screenshot della schermata corrente.
     * @param bounds Area del nodo testuale in coordinate schermo.
     * @param isLargeText `true` se il testo è classificato come "grande" secondo WCAG.
     * @return [ContrastResult] con rapporto e confidenza, oppure `null` se il campionamento fallisce.
     */
    fun measureTextContrast(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean): ContrastResult? {
        val fgSamples = sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.15f)
        if (fgSamples.isEmpty()) return null

        val bgSamples = sampleBackgroundRing(bitmap, bounds)
        if (bgSamples.isEmpty()) return null

        val fg = resolveEffectiveForeground(percentileColorByLuminance(fgSamples, percentile = 0.25))
        val bg = resolveEffectiveBackground(percentileColorByLuminance(bgSamples, percentile = 0.75))
        val ratio = contrastRatio(fg, bg)

        val separation = abs(relativeLuminance(fg) - relativeLuminance(bg))
        val confidence = (0.55f + separation.coerceIn(0.0, 0.45).toFloat() +
            (fgSamples.size / 16f).coerceAtMost(0.2f)).coerceAtMost(0.98f)

        return ContrastResult(
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
    fun measureTextContrastWithInnerBackground(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean): ContrastResult? {
        val fgSamples = sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.18f)
        if (fgSamples.isEmpty()) return null

        // Per lo sfondo del bottone campioniamo internamente ai bordi (ma dentro la shape).
        val bgSamples = sampleGrid(bitmap, bounds, grid = 4, insetPercent = 0.28f)
        if (bgSamples.isEmpty()) return null

        val fg = resolveEffectiveForeground(percentileColorByLuminance(fgSamples, percentile = 0.25))
        val bg = resolveEffectiveBackground(percentileColorByLuminance(bgSamples, percentile = 0.75))
        val ratio = contrastRatio(fg, bg)

        val separation = abs(relativeLuminance(fg) - relativeLuminance(bg))
        val confidence = (0.60f + separation.coerceIn(0.0, 0.35).toFloat() +
            (fgSamples.size / 16f).coerceAtMost(0.2f)).coerceAtMost(0.98f)

        return ContrastResult(
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
     * @return [ContrastResult] con rapporto e confidenza, oppure `null` se il campionamento fallisce.
     */
    fun measureUiContrast(bitmap: Bitmap, bounds: Rect): ContrastResult? {
        val fgSamples = sampleGrid(bitmap, bounds, grid = 3, insetPercent = 0.2f)
        val bgSamples = sampleBackgroundRing(bitmap, bounds)
        if (fgSamples.isEmpty() || bgSamples.isEmpty()) return null
        val fg = resolveEffectiveForeground(percentileColorByLuminance(fgSamples, percentile = 0.25))
        val bg = resolveEffectiveBackground(percentileColorByLuminance(bgSamples, percentile = 0.75))
        val separation = abs(relativeLuminance(fg) - relativeLuminance(bg))
        val confidence = (0.60f + separation.coerceIn(0.0, 0.35).toFloat()).coerceAtMost(0.90f)
        return ContrastResult(
            ratio = contrastRatio(fg, bg),
            foreground = fg,
            background = bg,
            confidence = confidence,
            samplesUsed = fgSamples.size + bgSamples.size,
            backgroundSamples = bgSamples,
            foregroundSamples = fgSamples,
        )
    }

    /**
     * `true` se i campioni di sfondo hanno luminanza molto variabile (tipico di illustrazioni/foto).
     */
    fun isComplexBackground(backgroundSamples: List<Int>): Boolean =
        isHighLuminanceVariance(backgroundSamples, COMPLEX_BG_LUMINANCE_RANGE)

    /**
     * `true` se l'area campionata sembra foto/illustrazione raster, non un controllo UI piatto.
     *
     * Evita falsi positivi di contrasto su ImageView e grafica decorativa.
     */
    fun isLikelyRasterImageContent(result: ContrastResult): Boolean {
        val fgVaried = isHighLuminanceVariance(result.foregroundSamples, RASTER_CONTENT_LUMINANCE_RANGE)
        if (fgVaried) return true
        return fgVaried && isComplexBackground(result.backgroundSamples)
    }

    private fun isHighLuminanceVariance(samples: List<Int>, minRange: Double): Boolean {
        if (samples.size < 4) return false
        val luminances = samples.map { relativeLuminance(it) }
        return (luminances.max() - luminances.min()) >= minRange
    }

    /**
     * Decide se segnalare una violazione di contrasto testo.
     *
     * Su sfondo uniforme usa il rapporto aggregato; su sfondo complesso richiede che
     * la maggioranza dei campioni intorno al testo fallisca la soglia (evita FP su hero illustrati).
     */
    fun shouldReportTextContrastFailure(
        result: ContrastResult,
        threshold: Double,
        isLargeText: Boolean,
    ): Boolean {
        if (result.ratio >= threshold) return false
        val bgSamples = result.backgroundSamples
        val complexBg = bgSamples.isNotEmpty() && isComplexBackground(bgSamples)
        val complexFg = isHighLuminanceVariance(
            result.foregroundSamples,
            COMPLEX_BG_LUMINANCE_RANGE,
        )
        if (bgSamples.isEmpty() || (!complexBg && !complexFg)) return true

        val ratios = bgSamples.map { contrastRatio(result.foreground, it) }
        val passFraction = ratios.count { it >= threshold }.toDouble() / ratios.size

        if (passFraction >= COMPLEX_BG_PASS_FRACTION) return false

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
        result: ContrastResult,
        threshold: Double,
    ): Boolean {
        if (result.ratio >= threshold) return false
        val bgSamples = result.backgroundSamples
        if (bgSamples.isEmpty() || !isComplexBackground(bgSamples)) return true

        val ratios = bgSamples.map { contrastRatio(result.foreground, it) }
        val passFraction = ratios.count { it >= threshold }.toDouble() / ratios.size
        if (passFraction >= 0.65) return false
        if (passFraction < 0.20 && result.ratio < 1.8) return true
        return passFraction < 0.40
    }

    /**
     * Seleziona il colore al percentile specificato ordinando i campioni per luminanza.
     *
     * @param colors Lista di colori ARGB campionati.
     * @param percentile Percentile da estrarre (0.0 = più scuro, 1.0 = più chiaro).
     * @return Colore ARGB corrispondente al percentile richiesto.
     */
    private fun percentileColorByLuminance(colors: List<Int>, percentile: Double): Int {
        val sorted = colors.sortedBy { relativeLuminance(it) }
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

    /**
     * Campiona pixel in un anello attorno al bounds per stimare il colore di sfondo.
     *
     * @param bitmap Screenshot sorgente.
     * @param bounds Area centrale attorno alla quale campionare lo sfondo.
     * @return Lista di colori ARGB opachi campionati ai lati e agli angoli esterni.
     */
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

    /**
     * Calcola il rapporto di contrasto WCAG tra due colori.
     *
     * @param foreground Colore ARGB del primo piano.
     * @param background Colore ARGB dello sfondo.
     * @return Rapporto (L_chiaro + 0.05) / (L_scuro + 0.05).
     */
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
    private fun channel(value: Int): Double {
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

    /**
     * Composita un colore semi-trasparente su sfondo bianco (tipico card banking).
     */
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

    /**
     * Soglia di confidenza minima dinamica in base a affidabilità della misura.
     */
    fun minConfidenceForMeasurement(
        result: ContrastResult,
        boundsWidthPx: Int,
        boundsHeightPx: Int,
        density: Float,
        isSmallIcon: Boolean,
        baseMin: Float,
    ): Float {
        var min = baseMin
        if (result.ratio < 2.5) {
            val separation = abs(relativeLuminance(result.foreground) - relativeLuminance(result.background))
            if (separation < 0.12) min = maxOf(min, 0.82f)
        }
        val minDimPx = (24 * density).toInt()
        if (isSmallIcon && boundsWidthPx < minDimPx && boundsHeightPx < minDimPx) {
            min = maxOf(min, 0.78f)
        }
        return min
    }

    private fun resolveEffectiveBackground(sampled: Int): Int {
        if (Color.alpha(sampled) >= 250) return sampled
        return compositeOverWhite(sampled)
    }

    private fun resolveEffectiveForeground(sampled: Int): Int {
        if (Color.alpha(sampled) >= 250) return sampled
        // Per il foreground (testo) semi-trasparente, compositiamo su bianco: è il caso più comune
        // nelle UI; questa scelta riduce FP su testi disabilitati (alpha) su superfici chiare.
        return compositeOverWhite(sampled, base = Color.WHITE)
    }
}
