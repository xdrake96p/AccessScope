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


internal object NodeStructureSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
        screenshot: Bitmap?,
        minTextHeightPx: Int,

    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (includes(ViolationArea.STRUCTURE) &&
                    snap.looksLikeStructuralHeading() &&
                    !snap.isHeading &&
                    !PrecisionRules.shouldSkipHeadingCheck(snap) &&
                    !PrecisionRules.isInsideCarouselOrListItem(snap, all, packageName) &&
                    !snap.className.contains("Toolbar", true) &&
                    snap.bounds.height() >= (minTextHeightPx * 1.5).toInt() &&
                    (snap.text?.length ?: 0) in 4..60
                ) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.HEADING_HIERARCHY, snap, packageName, screenTitle,
                        "Titolo visibile non marcato come heading.", 0.85f,
                    )
                }
                if (includes(ViolationArea.STRUCTURE) && snap.isScrollable && !snap.hasAccessibleName()) {
                    val screenArea = screenshot?.let { it.width * it.height } ?: NodeSingleNodeCheckSupport.estimateScreenArea(all)
                    if (!PrecisionRules.shouldSkipScrollWithoutLabel(snap, all, screenArea, packageName)) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.SCROLLABLE_WITHOUT_LABEL, snap, packageName, screenTitle,
                            "Area scrollabile senza nome.", 0.88f,
                        )
                    }
                }
    }
}
