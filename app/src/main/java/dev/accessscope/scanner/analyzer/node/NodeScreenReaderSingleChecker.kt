package dev.accessscope.scanner.analyzer.node

import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.CheckCollector
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.analyzer.ViolationConfidencePolicy
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType


internal object NodeScreenReaderSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
        customActionEmitted: MutableSet<String>,
        inMaterialCalendar: Boolean,

    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (!inMaterialCalendar &&
                    includes(ViolationArea.SCREEN_READER) &&
                    snap.shouldBeFocusable() &&
                    !snap.isFocusable &&
                    !snap.isScrollable
                ) {
                    if (PrecisionRules.hasFocusableOrEditableDescendant(snap, all)) return
                    if (snap.isEditable && snap.hasAccessibleName()) return
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.NOT_FOCUSABLE, snap, packageName, screenTitle,
                        "Interattivo ma non raggiungibile con TalkBack.", 0.9f,
                    )
                }
                if (includes(ViolationArea.SCREEN_READER) && !snap.isEnabled && snap.isInteractiveClickable() &&
                    snap.stateDescription.isNullOrBlank() &&
                    !PrecisionRules.shouldSkipCarouselListItemAnalysis(snap, all, packageName)
                ) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.DISABLED_WITHOUT_INDICATION, snap, packageName, screenTitle,
                        "Controllo disabilitato senza stato esposto.", 0.82f,
                    )
                }
        
                if (includes(ViolationArea.SCREEN_READER) && snap.className.contains("Expandable", true) && snap.isExpanded == null) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.EXPANDABLE_STATE_MISSING, snap, packageName, screenTitle,
                        "Espandibile senza stato aperto/chiuso.", 0.8f,
                    )
                }
                if (!inMaterialCalendar &&
                    includes(ViolationArea.SCREEN_READER) &&
                    PrecisionRules.shouldReportCustomAction(snap, all, packageName)
                ) {
                    val actionKey = snap.viewId?.takeIf { it.isNotBlank() }
                        ?: "${snap.className}@${snap.bounds.hashCode()}"
                    if (customActionEmitted.add(actionKey)) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.CUSTOM_ACTION_UNLABELED, snap, packageName, screenTitle,
                            "${snap.unlabeledActionCount} azione/i personalizzata/e senza etichetta.",
                            ViolationConfidencePolicy.customActionConfidence(snap),
                        )
                    }
                }
    }
}
