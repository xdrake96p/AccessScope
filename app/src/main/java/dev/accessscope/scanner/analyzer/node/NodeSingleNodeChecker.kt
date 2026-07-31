package dev.accessscope.scanner.analyzer.node

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea

internal object NodeSingleNodeChecker {

    fun checkSingleNode(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenshot: Bitmap?,
        customActionEmitted: MutableSet<String>,
        viewport: Rect,
        screenWidth: Int,
        checkCollector: CheckCollector,
        scanScope: ScanScope,
        minTouchTargetPx: Int,
        minTextHeightPx: Int,
        recommendedTextHeightPx: Int,
        density: Float,
        screenFingerprint: String?,
    ) {
        if (PrecisionRules.shouldSkipDrawerNode(snap)) return
        if (PrecisionRules.shouldSkipPinPadWhenNotPinScreen(snap, screenTitle, packageName)) return
        if (PrecisionRules.shouldSkipStructuralNoise(snap, viewport, screenWidth, packageName)) return
        if (PrecisionRules.shouldSkipPlatformNoiseAnalysis(snap, all, packageName)) return
        val inMaterialCalendar = PrecisionRules.isMaterialCalendarRelatedNode(snap, all)

        NodeLabelsSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
            inMaterialCalendar, checkCollector,
        )
        NodeTouchSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
            checkCollector, minTouchTargetPx, inMaterialCalendar,
        )
        NodeScreenReaderSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
            customActionEmitted, inMaterialCalendar,
        )
        NodeFormsSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
        )
        NodeStructureSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
            screenshot, minTextHeightPx,
        )
        NodeTextSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
            viewport, minTextHeightPx, recommendedTextHeightPx,
        )
        NodeMediaSingleChecker.check(
            snap, all, packageName, screenTitle, violations, scanScope, screenFingerprint,
        )

        if (scanScope.includes(ViolationArea.COLOR)) {
            screenshot?.let {
                NodeContrastChecker.checkContrast(
                    snap, packageName, screenTitle, violations, it, all, viewport, checkCollector,
                    minTouchTargetPx, density, screenFingerprint,
                )
            }
        }
    }
}
