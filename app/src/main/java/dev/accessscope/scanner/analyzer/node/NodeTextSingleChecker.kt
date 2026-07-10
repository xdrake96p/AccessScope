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


internal object NodeTextSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
        viewport: Rect,
        minTextHeightPx: Int,
        recommendedTextHeightPx: Int,

    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (includes(ViolationArea.TEXT) && snap.hasVisibleText() &&
                    !PrecisionRules.shouldSkipSmallTextCheck(snap, viewport, packageName) &&
                    !PrecisionRules.isOffScreenOrMarginalNode(snap, viewport, packageName)
                ) {
                    if (PrecisionRules.isAnomalousTouchBounds(snap)) return
                    if (snap.bounds.height() < minTextHeightPx) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                            "Altezza ~${snap.bounds.height()} px (< ${minTextHeightPx} px, circa 12sp).", 0.88f,
                        )
                    } else if (snap.isInteractiveClickable() && snap.bounds.height() < recommendedTextHeightPx) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                            "Testo cliccabile piccolo: ${snap.bounds.height()} px (consigliato ≥ $recommendedTextHeightPx px).", 0.75f,
                        )
                    }
                }
        
                val textValue = snap.text.orEmpty()
                if (includes(ViolationArea.TEXT) &&
                    (textValue.endsWith("…") || textValue.endsWith("...")) &&
                    snap.contentDescription.isNullOrBlank()
                ) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.TEXT_TRUNCATED, snap, packageName, screenTitle,
                        "Testo troncato senza descrizione completa.", 0.9f,
                    )
                }
    }
}
