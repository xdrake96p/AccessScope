package dev.accessscope.scanner.analyzer.node

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType


internal object NodeTouchSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
        checkCollector: CheckCollector,
        minTouchTargetPx: Int,
        inMaterialCalendar: Boolean,

    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (snap.isInteractiveClickable() && includes(ViolationArea.TOUCH)) {
                    if (!inMaterialCalendar && !PrecisionRules.shouldSkipTouchTargetCheck(snap, all, packageName)) {
                        if (snap.bounds.width() < minTouchTargetPx || snap.bounds.height() < minTouchTargetPx) {
                            violations += ViolationBuilder.v(
                                screenFingerprint,
                                ViolationType.SMALL_TOUCH_TARGET, snap, packageName, screenTitle,
                                "Misura ${snap.bounds.width()}×${snap.bounds.height()} px, minimo ${minTouchTargetPx} px.",
                                0.92f,
                                measuredValue = "${snap.bounds.width()}×${snap.bounds.height()} px",
                                requiredValue = "≥ ${minTouchTargetPx}×${minTouchTargetPx} px",
                            )
                        } else {
                            checkCollector.recordPass(
                                ViolationArea.TOUCH, screenTitle, packageName,
                                "Target di tocco sufficiente",
                                snap, ViolationType.SMALL_TOUCH_TARGET.wcagRef,
                                "${snap.bounds.width()}×${snap.bounds.height()} px",
                            )
                        }
                    }
                }
    }
}
