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


internal object NodeMediaSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (includes(ViolationArea.MEDIA_WEB) && snap.isMediaControl() && !snap.hasAccessibleName()) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.MEDIA_CONTROL_UNLABELED, snap, packageName, screenTitle,
                        "Controllo media senza etichetta.", 0.92f,
                    )
                }
    }
}
