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


internal object NodeLabelsSingleChecker {
    fun check(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        scanScope: ScanScope,
        screenFingerprint: String?,
        inMaterialCalendar: Boolean,
        checkCollector: CheckCollector,

    ) {
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)
                if (includes(ViolationArea.LABELS)) {
                    val missingLabel = !inMaterialCalendar &&
                        (snap.isInteractiveClickable() || PrecisionRules.shouldReportMissingTopBarLabel(snap, all)) &&
                        !snap.hasAccessibleName() &&
                        !PrecisionRules.isIconInsideLabeledButton(snap, all) &&
                        !PrecisionRules.shouldSkipContainerLabelCheck(snap, all, packageName)
                    if (missingLabel) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.MISSING_LABEL, snap, packageName, screenTitle,
                            "Nessuna etichetta (testo, descrizione o hint).", 0.95f,
                        )
                    } else if (snap.isInteractiveClickable() && snap.hasAccessibleName()) {
                        checkCollector.recordPass(
                            ViolationArea.LABELS, screenTitle, packageName,
                            "Etichetta accessibile presente", snap, ViolationType.MISSING_LABEL.wcagRef,
                            screenFingerprint = screenFingerprint,
                        )
                    } else if (PrecisionRules.shouldReportMissingTopBarLabel(snap, all) && snap.hasAccessibleName()) {
                        checkCollector.recordPass(
                            ViolationArea.LABELS, screenTitle, packageName,
                            "Icona toolbar con descrizione", snap, ViolationType.MISSING_LABEL.wcagRef,
                            screenFingerprint = screenFingerprint,
                        )
                    }
                }
                if (includes(ViolationArea.LABELS)) {
                    when {
                        snap.isImageWithoutAlt() && !PrecisionRules.isDecorative(snap) &&
                            !PrecisionRules.isIconInsideLabeledButton(snap, all) -> {
                            violations += ViolationBuilder.v(
                                screenFingerprint,
                                ViolationType.IMAGE_MISSING_ALT, snap, packageName, screenTitle,
                                "Immagine senza testo alternativo.", 0.95f,
                            )
                        }
                        PrecisionRules.isDecorative(snap) && snap.hasAccessibleName() &&
                            !snap.contentDescription.isNullOrBlank() &&
                            !PrecisionRules.shouldSkipDecorativeLabeledCheck(snap, all) -> {
                            violations += ViolationBuilder.v(
                                screenFingerprint,
                                ViolationType.DECORATIVE_IMAGE_LABELED, snap, packageName, screenTitle,
                                "Immagine decorativa con etichetta superflua.", 0.8f,
                            )
                        }
                    }
        
                    snap.contentDescription?.let { cd ->
                        if (snap.isImageClass() && PrecisionRules.isPoorAltText(cd)) {
                            violations += ViolationBuilder.v(
                                screenFingerprint,
                                ViolationType.POOR_ALT_TEXT, snap, packageName, screenTitle,
                                "Descrizione generica: \"$cd\".", 0.85f,
                            )
                        }
                    }
        
                    if (snap.isLikelyLink() && snap.hasAccessibleName() && NodeSingleNodeCheckSupport.isNonDescriptiveLink(snap.accessibleName()!!)) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.LINK_NOT_DESCRIPTIVE, snap, packageName, screenTitle,
                            "Link generico: \"${snap.accessibleName()}\".", 0.9f,
                        )
                    }
                }
                if (includes(ViolationArea.LABELS) && snap.isClickable && snap.isCustomView() &&
                    !snap.hasAccessibleName() && !snap.hasStandardRole() &&
                    !inMaterialCalendar &&
                    !PrecisionRules.shouldSkipContainerLabelCheck(snap, all, packageName) &&
                    !PrecisionRules.isCarouselContentContainer(snap, all, packageName) &&
                    !PrecisionRules.shouldSkipHomeWidgetAnalysis(snap, all, packageName) &&
                    !(PrecisionRules.isCtaContainer(snap, packageName) && PrecisionRules.hasTvCustomDescendant(snap, all))
                ) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.ROLE_UNDEFINED, snap, packageName, screenTitle,
                        "View custom cliccabile senza ruolo semantico.", 0.85f,
                    )
                }
                if (includes(ViolationArea.LABELS) && !snap.tooltipText.isNullOrBlank() && snap.contentDescription.isNullOrBlank() && !snap.isFocusable) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.TOOLTIP_INACCESSIBLE, snap, packageName, screenTitle,
                        "Tooltip \"${snap.tooltipText}\" non accessibile a TalkBack.", 0.8f,
                    )
                }
    }
}
