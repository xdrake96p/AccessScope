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

internal object NodeSingleNodeChecker {

    private val NON_DESCRIPTIVE_LINKS = setOf(
        "click here", "tap here", "here", "more", "read more", "learn more", "details", "link",
        "continue", "go", "ok", "submit", "clicca qui", "qui", "altro", "leggi", "leggi tutto",
        "scopri", "continua", "dettagli", "vai", "info", "apri", "tap",
    )

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
        fun includes(area: ViolationArea): Boolean = scanScope.includes(area)

        if (PrecisionRules.shouldSkipDrawerNode(snap)) return
        if (PrecisionRules.shouldSkipPinPadWhenNotPinScreen(snap, screenTitle, packageName)) return
        if (PrecisionRules.shouldSkipHomeWidgetAnalysis(snap, all, packageName)) return
        if (PrecisionRules.shouldSkipStructuralNoise(snap, viewport, screenWidth, packageName)) return
        if (PrecisionRules.shouldSkipPlatformNoiseAnalysis(snap, all, packageName)) return
        val inMaterialCalendar = PrecisionRules.isMaterialCalendarRelatedNode(snap, screenTitle, all)

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
                )
            } else if (PrecisionRules.shouldReportMissingTopBarLabel(snap, all) && snap.hasAccessibleName()) {
                checkCollector.recordPass(
                    ViolationArea.LABELS, screenTitle, packageName,
                    "Icona toolbar con descrizione", snap, ViolationType.MISSING_LABEL.wcagRef,
                )
            }
        }
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

        if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.hasInputLabel()) {
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.INPUT_LABEL, snap, packageName, screenTitle,
                "Campo senza etichetta associata.", 0.95f,
            )
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && snap.errorText.isNullOrBlank() &&
            snap.text?.contains("error", true) == true
        ) {
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.INPUT_ERROR_MISSING, snap, packageName, screenTitle,
                "Errore visivo probabile senza messaggio accessibile.", 0.7f,
            )
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && snap.isEnabled &&
            !PrecisionRules.isRequiredFieldHint(snap.hintText, snap.text, snap.contentDescription) &&
            snap.hintText?.contains('*') != true &&
            snap.className.contains("required", true)
        ) {
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.REQUIRED_FIELD_UNMARKED, snap, packageName, screenTitle,
                "Campo probabilmente obbligatorio non marcato.", 0.65f,
            )
        }

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

            if (snap.isLikelyLink() && snap.hasAccessibleName() && isNonDescriptiveLink(snap.accessibleName()!!)) {
                violations += ViolationBuilder.v(
                    screenFingerprint,
                    ViolationType.LINK_NOT_DESCRIPTIVE, snap, packageName, screenTitle,
                    "Link generico: \"${snap.accessibleName()}\".", 0.9f,
                )
            }
        }

        if (includes(ViolationArea.STRUCTURE) && snap.isScrollable && !snap.hasAccessibleName()) {
            val screenArea = screenshot?.let { it.width * it.height } ?: estimateScreenArea(all)
            if (!PrecisionRules.shouldSkipScrollWithoutLabel(snap, all, screenArea, packageName)) {
                violations += ViolationBuilder.v(
                    screenFingerprint,
                    ViolationType.SCROLLABLE_WITHOUT_LABEL, snap, packageName, screenTitle,
                    "Area scrollabile senza nome.", 0.88f,
                )
            }
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

        if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.isPassword && snap.className.contains("password", true)) {
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.PASSWORD_NOT_MASKED, snap, packageName, screenTitle,
                "Campo password non marcato isPassword.", 0.95f,
            )
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

        if (includes(ViolationArea.FORMS) && snap.rangeMin != null && snap.rangeMax != null &&
            (snap.rangeCurrent == null || snap.className.contains("SeekBar", true) || snap.className.contains("Slider", true))
        ) {
            val hasValue = snap.stateDescription?.isNotBlank() == true || snap.contentDescription?.contains("%") == true
            if (!hasValue && snap.rangeCurrent == snap.rangeMin) {
                violations += ViolationBuilder.v(
                    screenFingerprint,
                    ViolationType.SLIDER_VALUE_MISSING, snap, packageName, screenTitle,
                    "Slider/progresso senza valore annunciato.", 0.78f,
                )
            }
        }

        if (includes(ViolationArea.LABELS) && !snap.tooltipText.isNullOrBlank() && snap.contentDescription.isNullOrBlank() && !snap.isFocusable) {
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.TOOLTIP_INACCESSIBLE, snap, packageName, screenTitle,
                "Tooltip \"${snap.tooltipText}\" non accessibile a TalkBack.", 0.8f,
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
                    "${snap.unlabeledActionCount} azione/i personalizzata/e senza etichetta.", 0.88f,
                )
            }
        }

        if (includes(ViolationArea.MEDIA_WEB) && snap.isMediaControl() && !snap.hasAccessibleName()) {
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.MEDIA_CONTROL_UNLABELED, snap, packageName, screenTitle,
                "Controllo media senza etichetta.", 0.92f,
            )
        }

        if (includes(ViolationArea.COLOR)) {
            screenshot?.let {
                NodeContrastChecker.checkContrast(
                    snap, packageName, screenTitle, violations, it, all, viewport, checkCollector,
                    minTouchTargetPx, density, screenFingerprint,
                )
            }
        }
    }

    private fun isNonDescriptiveLink(name: String): Boolean {
        val n = name.trim().lowercase()
        return NON_DESCRIPTIVE_LINKS.any { n == it || n.matches(Regex("^$it\\W*")) }
    }

    private fun estimateScreenArea(snapshots: List<NodeSnapshot>): Int {
        if (snapshots.isEmpty()) return 0
        var maxRight = 0
        var maxBottom = 0
        snapshots.forEach { snap ->
            maxRight = maxOf(maxRight, snap.bounds.right)
            maxBottom = maxOf(maxBottom, snap.bounds.bottom)
        }
        return maxRight * maxBottom
    }
}
