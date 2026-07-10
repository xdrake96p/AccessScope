/**
 * Misurazione del contrasto colore su screenshot secondo le formule WCAG 2.x.
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.contrast.WcagContrastMath
import dev.accessscope.scanner.analyzer.contrast.WcagContrastMeasurement
import dev.accessscope.scanner.analyzer.contrast.WcagContrastPolicy
import dev.accessscope.scanner.analyzer.contrast.WcagContrastTypes

typealias ContrastResult = WcagContrastTypes.ContrastResult

object WcagContrast {
    const val MIN_TEXT_CONTRAST = WcagContrastTypes.MIN_TEXT_CONTRAST
    const val MIN_LARGE_TEXT_CONTRAST = WcagContrastTypes.MIN_LARGE_TEXT_CONTRAST
    const val MIN_NON_TEXT_CONTRAST = WcagContrastTypes.MIN_NON_TEXT_CONTRAST
    const val MIN_RELIABLE_RATIO = WcagContrastTypes.MIN_RELIABLE_RATIO
    const val MIN_LUMINANCE_SEPARATION = WcagContrastTypes.MIN_LUMINANCE_SEPARATION
    const val MIN_SAMPLES = WcagContrastTypes.MIN_SAMPLES

    fun isReliableMeasurement(result: ContrastResult) = WcagContrastMeasurement.isReliableMeasurement(result)
    fun measureTextContrast(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean) =
        WcagContrastMeasurement.measureTextContrast(bitmap, bounds, isLargeText)
    fun measureTextContrastWithInnerBackground(bitmap: Bitmap, bounds: Rect, isLargeText: Boolean) =
        WcagContrastMeasurement.measureTextContrastWithInnerBackground(bitmap, bounds, isLargeText)
    fun measureUiContrast(bitmap: Bitmap, bounds: Rect) = WcagContrastMeasurement.measureUiContrast(bitmap, bounds)
    fun isComplexBackground(backgroundSamples: List<Int>) = WcagContrastPolicy.isComplexBackground(backgroundSamples)
    fun isLikelyRasterImageContent(result: ContrastResult) = WcagContrastPolicy.isLikelyRasterImageContent(result)
    fun shouldReportTextContrastFailure(result: ContrastResult, threshold: Double, isLargeText: Boolean) =
        WcagContrastPolicy.shouldReportTextContrastFailure(result, threshold, isLargeText)
    fun shouldReportUiContrastFailure(result: ContrastResult, threshold: Double) =
        WcagContrastPolicy.shouldReportUiContrastFailure(result, threshold)
    fun contrastRatio(foreground: Int, background: Int) = WcagContrastMath.contrastRatio(foreground, background)
    fun formatArgbHex(color: Int) = WcagContrastMath.formatArgbHex(color)
    fun relativeLuminance(color: Int) = WcagContrastMath.relativeLuminance(color)
    fun isLargeText(boundsHeightPx: Int, density: Float) = WcagContrastMath.isLargeText(boundsHeightPx, density)
    fun isLargeText(snap: NodeSnapshot, density: Float, largeTextViewIds: Set<String> = emptySet()) =
        WcagContrastMath.isLargeText(snap, density, largeTextViewIds)
    fun compositeOverWhite(color: Int, base: Int = android.graphics.Color.WHITE) =
        WcagContrastMath.compositeOverWhite(color, base)
    fun colorToHex(color: Int) = WcagContrastMath.colorToHex(color)
    fun minConfidenceForMeasurement(
        result: ContrastResult,
        boundsWidthPx: Int,
        boundsHeightPx: Int,
        density: Float,
        isSmallIcon: Boolean,
        baseMin: Float,
    ) = WcagContrastPolicy.minConfidenceForMeasurement(result, boundsWidthPx, boundsHeightPx, density, isSmallIcon, baseMin)
}
