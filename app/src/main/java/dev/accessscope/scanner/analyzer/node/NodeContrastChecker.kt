package dev.accessscope.scanner.analyzer.node

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.analyzer.WcagContrast
import dev.accessscope.scanner.analyzer.precision.area
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType

internal object NodeContrastChecker {

    fun checkContrast(
        snap: NodeSnapshot,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        bitmap: Bitmap,
        all: List<NodeSnapshot>,
        viewport: Rect,
        checkCollector: CheckCollector,
        minTouchTargetPx: Int,
        density: Float,
        screenFingerprint: String?,
    ) {
        if (PrecisionRules.isLayoutContainer(snap.className)) return
        if (PrecisionRules.isLikelyStatusBadge(snap)) return
        if (PrecisionRules.isHomeChartDecorativeText(snap, all, packageName)) return
        if (PrecisionRules.isBrandedOrPrimaryCtaText(snap, all, packageName)) return
        val screenArea = bitmap.width * bitmap.height
        if (PrecisionRules.shouldSkipContrastCheck(snap, all, packageName, screenArea)) return
        if (PrecisionRules.shouldSkipTopBarIconContrast(snap, all, viewport)) return
        if (screenArea > 0 && snap.area() > screenArea * 0.6) return

        val largeTextIds = AppPrecisionProfiles.largeTextViewIds(packageName)
        val hintOnly = snap.isEditable && snap.text.isNullOrBlank() && !snap.hintText.isNullOrBlank()

        if (snap.hasVisibleText() || hintOnly) {
            if (snap.isImageClass()) return
            val isFieldLabel = PrecisionRules.isKnownContrastFieldLabel(snap, packageName)
            val sampleBounds = if (isFieldLabel) expandBoundsForMicroLabel(snap.bounds, density) else snap.bounds
            val large = WcagContrast.isLargeText(snap, density, largeTextIds) || isFieldLabel
            val result = if (PrecisionRules.isButtonLikeTapTarget(snap) && !hintOnly && !isFieldLabel) {
                WcagContrast.measureTextContrastWithInnerBackground(bitmap, sampleBounds, large)
            } else {
                WcagContrast.measureTextContrast(bitmap, sampleBounds, large)
            } ?: return
            if (WcagContrast.isLikelyRasterImageContent(result)) return
            if (!WcagContrast.isReliableMeasurement(result)) return
            val baseMinConfidence = when {
                PrecisionRules.viewIdShort(snap).startsWith("txt_data_") -> 0.45f
                isFieldLabel -> 0.50f
                hintOnly -> 0.55f
                else -> 0.72f
            }
            val minConfidence = WcagContrast.minConfidenceForMeasurement(
                result = result,
                boundsWidthPx = snap.bounds.width(),
                boundsHeightPx = snap.bounds.height(),
                density = density,
                isSmallIcon = false,
                baseMin = baseMinConfidence,
            )
            if (result.confidence < minConfidence) return
            if (!isFieldLabel && !hintOnly &&
                WcagContrast.relativeLuminance(result.foreground) > 0.80 &&
                snap.bounds.height() <= (minTouchTargetPx * 0.85f).toInt()
            ) {
                return
            }
            val threshold = if (large || isFieldLabel) {
                WcagContrast.MIN_LARGE_TEXT_CONTRAST
            } else {
                WcagContrast.MIN_TEXT_CONTRAST
            }
            if (WcagContrast.shouldReportTextContrastFailure(result, threshold, large || isFieldLabel)) {
                violations += ViolationBuilder.v(
                    screenFingerprint,
                    ViolationType.LOW_COLOR_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto ${"%.2f".format(result.ratio)}:1 (serve ≥ $threshold:1). " +
                        "${result.samplesUsed} campioni, confidenza ${(result.confidence * 100).toInt()}%.",
                    result.confidence,
                    measuredValue = "${"%.2f".format(result.ratio)}:1",
                    requiredValue = "≥ $threshold:1",
                    foregroundColorHex = WcagContrast.formatArgbHex(result.foreground),
                    backgroundColorHex = WcagContrast.formatArgbHex(result.background),
                )
            } else {
                checkCollector.recordPass(
                    ViolationArea.COLOR, screenTitle, packageName,
                    "Contrasto testo sufficiente",
                    snap, ViolationType.LOW_COLOR_CONTRAST.wcagRef,
                    "${"%.2f".format(result.ratio)}:1 (≥ $threshold:1)",
                    screenFingerprint = screenFingerprint,
                )
            }
        } else if (snap.isInteractiveClickable() || snap.isImageClass()) {
            if (PrecisionRules.isEmptyTextSurfaceWithoutContent(snap)) return
            if (PrecisionRules.shouldSkipUiContrastCheck(snap, all, packageName, screenArea)) return
            if (PrecisionRules.shouldSkipTopBarIconContrast(snap, all, viewport)) return
            val result = WcagContrast.measureUiContrast(bitmap, snap.bounds) ?: return
            if (WcagContrast.isLikelyRasterImageContent(result)) return
            if (!WcagContrast.isReliableMeasurement(result)) return
            val minConfidence = WcagContrast.minConfidenceForMeasurement(
                result = result,
                boundsWidthPx = snap.bounds.width(),
                boundsHeightPx = snap.bounds.height(),
                density = density,
                isSmallIcon = snap.isImageClass(),
                baseMin = 0.72f,
            )
            if (result.confidence < minConfidence) return
            if (WcagContrast.shouldReportUiContrastFailure(result, WcagContrast.MIN_NON_TEXT_CONTRAST)) {
                violations += ViolationBuilder.v(
                    screenFingerprint,
                    ViolationType.LOW_NON_TEXT_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto UI ${"%.2f".format(result.ratio)}:1 (serve ≥ ${WcagContrast.MIN_NON_TEXT_CONTRAST}:1).",
                    result.confidence,
                    measuredValue = "${"%.2f".format(result.ratio)}:1",
                    requiredValue = "≥ ${WcagContrast.MIN_NON_TEXT_CONTRAST}:1",
                    foregroundColorHex = WcagContrast.formatArgbHex(result.foreground),
                    backgroundColorHex = WcagContrast.formatArgbHex(result.background),
                )
            } else {
                checkCollector.recordPass(
                    ViolationArea.COLOR, screenTitle, packageName,
                    "Contrasto icona/controllo sufficiente",
                    snap, ViolationType.LOW_NON_TEXT_CONTRAST.wcagRef,
                    "${"%.2f".format(result.ratio)}:1",
                    screenFingerprint = screenFingerprint,
                )
            }
        }
    }

    private fun expandBoundsForMicroLabel(bounds: Rect, density: Float): Rect {
        val pad = (3 * density).toInt().coerceAtLeast(3)
        return Rect(
            bounds.left - pad,
            bounds.top - pad,
            bounds.right + pad,
            bounds.bottom + pad,
        )
    }
}
