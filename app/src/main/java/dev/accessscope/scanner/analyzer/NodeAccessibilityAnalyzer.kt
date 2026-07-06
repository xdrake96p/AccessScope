package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScanScope
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType

class NodeAccessibilityAnalyzer(
    private val minTouchTargetPx: Int,
    private val minTouchSpacingPx: Int,
    private val minTextHeightPx: Int,
    private val recommendedTextHeightPx: Int,
    private val density: Float,
    private val dynamicContentSilent: Boolean = false,
    private val scanScope: ScanScope = ScanScope.FULL,
) {

    private fun includes(area: ViolationArea): Boolean = scanScope.includes(area)

    private var analyzeFingerprint: String? = null

    fun analyzeTree(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        screenshot: Bitmap? = null,
        screenFingerprint: String? = null,
    ): AnalysisResult {
        analyzeFingerprint = screenFingerprint
        val violations = mutableListOf<AccessibilityViolation>()
        val snapshots = mutableListOf<NodeSnapshot>()
        var traversalIndex = 0
        collectSnapshots(root, snapshots, ArrayDeque(), { traversalIndex++ })
        val customActionEmitted = mutableSetOf<String>()

        snapshots.forEach { snap ->
            if (!snap.isAccessibilityExcluded) {
                checkSingleNode(
                    snap, snapshots, packageName, screenTitle,
                    violations, screenshot, customActionEmitted,
                )
            }
        }

        if (includes(ViolationArea.LABELS) || includes(ViolationArea.TOUCH)) {
            checkCrossNodeIssues(snapshots, packageName, screenTitle, violations)
        }
        if (includes(ViolationArea.STRUCTURE)) {
            checkModalTitle(root, packageName, screenTitle, violations)
            checkCollectionStructure(root, packageName, screenTitle, violations)
            checkTables(snapshots, packageName, screenTitle, violations)
            checkDuplicateViewIds(snapshots, packageName, screenTitle, violations)
            violations += FocusOrderAnalyzer.analyze(snapshots, packageName, screenTitle)
            violations += FocusOrderAnalyzer.analyzeHeadingLevels(snapshots, packageName, screenTitle)
        }
        if (includes(ViolationArea.LABELS)) {
            checkDuplicateLinks(snapshots, packageName, screenTitle, violations)
        }
        if (includes(ViolationArea.MEDIA_WEB)) {
            checkWebViews(snapshots, packageName, screenTitle, violations)
        }

        if (dynamicContentSilent && includes(ViolationArea.SCREEN_READER)) {
            violations += AccessibilityViolation(
                type = ViolationType.DYNAMIC_CONTENT_SILENT,
                viewClassName = "Schermata",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Il contenuto è cambiato più volte senza annunci TalkBack.",
                confidence = 0.85f,
            )
        }

        val screenReaderFindings = if (includes(ViolationArea.SCREEN_READER)) {
            TalkBackSimulator().simulate(root, packageName, screenTitle)
        } else {
            emptyList()
        }
        return AnalysisResult(violations, screenReaderFindings)
    }

    private fun collectSnapshots(
        node: AccessibilityNodeInfo,
        output: MutableList<NodeSnapshot>,
        headingStack: ArrayDeque<String>,
        nextIndex: () -> Int,
    ) {
        val sectionTitle = headingStack.lastOrNull()
        val index = nextIndex()
        val snap = node.toSnapshot(index, minTextHeightPx, minTouchTargetPx, sectionTitle)
        var pushedHeading: String? = null

        snap?.let { snapshot ->
            output.add(snapshot)
            val headingText = snapshot.text?.trim()?.takeIf { it.isNotBlank() }
            if (headingText != null && (snapshot.isHeading || snapshot.looksLikeStructuralHeading())) {
                headingStack.addLast(headingText)
                pushedHeading = headingText
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSnapshots(child, output, headingStack, nextIndex)
            child.recycle()
        }

        if (pushedHeading != null) {
            headingStack.removeLast()
        }
    }

    private fun checkSingleNode(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenshot: Bitmap?,
        customActionEmitted: MutableSet<String>,
    ) {
        if (includes(ViolationArea.LABELS)) {
            val missingLabel = (snap.isInteractiveClickable() || PrecisionRules.shouldReportMissingTopBarLabel(snap, all)) &&
                !snap.hasAccessibleName() &&
                !PrecisionRules.isIconInsideLabeledButton(snap, all) &&
                !PrecisionRules.shouldSkipContainerLabelCheck(snap, all)
            if (missingLabel) {
                violations += v(ViolationType.MISSING_LABEL, snap, packageName, screenTitle,
                    "Nessuna etichetta (testo, descrizione o hint).", 0.95f)
            }
        }
        if (snap.isInteractiveClickable() && includes(ViolationArea.TOUCH)) {
            if (!PrecisionRules.shouldSkipTouchTargetCheck(snap, all)) {
                if (snap.bounds.width() < minTouchTargetPx || snap.bounds.height() < minTouchTargetPx) {
                    violations += v(ViolationType.SMALL_TOUCH_TARGET, snap, packageName, screenTitle,
                        "Misura ${snap.bounds.width()}×${snap.bounds.height()} px, minimo ${minTouchTargetPx} px.", 0.92f)
                }
            }
        }

        if (includes(ViolationArea.SCREEN_READER) && snap.shouldBeFocusable() && !snap.isFocusable && !snap.isScrollable) {
            violations += v(ViolationType.NOT_FOCUSABLE, snap, packageName, screenTitle,
                "Interattivo ma non raggiungibile con TalkBack.", 0.9f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.hasInputLabel()) {
            violations += v(ViolationType.INPUT_LABEL, snap, packageName, screenTitle,
                "Campo senza etichetta associata.", 0.95f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && snap.errorText.isNullOrBlank() &&
            snap.text?.contains("error", true) == true
        ) {
            violations += v(ViolationType.INPUT_ERROR_MISSING, snap, packageName, screenTitle,
                "Errore visivo probabile senza messaggio accessibile.", 0.7f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && snap.isEnabled &&
            !PrecisionRules.isRequiredFieldHint(snap.hintText, snap.text, snap.contentDescription) &&
            snap.hintText?.contains('*') != true &&
            snap.className.contains("required", true)
        ) {
            violations += v(ViolationType.REQUIRED_FIELD_UNMARKED, snap, packageName, screenTitle,
                "Campo probabilmente obbligatorio non marcato.", 0.65f)
        }

        if (includes(ViolationArea.STRUCTURE) &&
            snap.looksLikeStructuralHeading() &&
            !snap.isHeading &&
            !PrecisionRules.shouldSkipHeadingCheck(snap) &&
            !snap.className.contains("Toolbar", true) &&
            snap.bounds.height() >= (minTextHeightPx * 1.2).toInt() &&
            (snap.text?.length ?: 0) <= 60
        ) {
            violations += v(ViolationType.HEADING_HIERARCHY, snap, packageName, screenTitle,
                "Titolo visibile non marcato come heading.", 0.85f)
        }

        if (includes(ViolationArea.TEXT) && snap.hasVisibleText() && !PrecisionRules.shouldSkipSmallTextCheck(snap)) {
            if (snap.bounds.height() < minTextHeightPx) {
                violations += v(ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                    "Altezza ~${snap.bounds.height()} px (< ${minTextHeightPx} px, circa 12sp).", 0.88f)
            } else if (snap.isInteractiveClickable() && snap.bounds.height() < recommendedTextHeightPx) {
                violations += v(ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                    "Testo cliccabile piccolo: ${snap.bounds.height()} px (consigliato ≥ $recommendedTextHeightPx px).", 0.75f)
            }
        }

        val textValue = snap.text.orEmpty()
        if (includes(ViolationArea.TEXT) &&
            (textValue.endsWith("…") || textValue.endsWith("...")) &&
            snap.contentDescription.isNullOrBlank()
        ) {
            violations += v(ViolationType.TEXT_TRUNCATED, snap, packageName, screenTitle,
                "Testo troncato senza descrizione completa.", 0.9f)
        }

        if (includes(ViolationArea.LABELS)) {
            when {
                snap.isImageWithoutAlt() && !PrecisionRules.isDecorative(snap) &&
                    !PrecisionRules.isIconInsideLabeledButton(snap, all) -> {
                    violations += v(ViolationType.IMAGE_MISSING_ALT, snap, packageName, screenTitle,
                        "Immagine senza testo alternativo.", 0.95f)
                }
                PrecisionRules.isDecorative(snap) && snap.hasAccessibleName() &&
                    !snap.contentDescription.isNullOrBlank() &&
                    !PrecisionRules.shouldSkipDecorativeLabeledCheck(snap, all) -> {
                    violations += v(ViolationType.DECORATIVE_IMAGE_LABELED, snap, packageName, screenTitle,
                        "Immagine decorativa con etichetta superflua.", 0.8f)
                }
            }

            snap.contentDescription?.let { cd ->
                if (snap.isImageClass() && PrecisionRules.isPoorAltText(cd)) {
                    violations += v(ViolationType.POOR_ALT_TEXT, snap, packageName, screenTitle,
                        "Descrizione generica: \"$cd\".", 0.85f)
                }
            }

            if (snap.isLikelyLink() && snap.hasAccessibleName() && isNonDescriptiveLink(snap.accessibleName()!!)) {
                violations += v(ViolationType.LINK_NOT_DESCRIPTIVE, snap, packageName, screenTitle,
                    "Link generico: \"${snap.accessibleName()}\".", 0.9f)
            }
        }

        if (includes(ViolationArea.STRUCTURE) && snap.isScrollable && !snap.hasAccessibleName()) {
            val screenArea = screenshot?.let { it.width * it.height } ?: estimateScreenArea(all)
            if (!PrecisionRules.shouldSkipScrollWithoutLabel(snap, all, screenArea)) {
                violations += v(ViolationType.SCROLLABLE_WITHOUT_LABEL, snap, packageName, screenTitle,
                    "Area scrollabile senza nome.", 0.88f)
            }
        }

        if (includes(ViolationArea.SCREEN_READER) && !snap.isEnabled && snap.isInteractiveClickable() && snap.stateDescription.isNullOrBlank()) {
            violations += v(ViolationType.DISABLED_WITHOUT_INDICATION, snap, packageName, screenTitle,
                "Controllo disabilitato senza stato esposto.", 0.82f)
        }

        if (includes(ViolationArea.SCREEN_READER) && snap.className.contains("Expandable", true) && snap.isExpanded == null) {
            violations += v(ViolationType.EXPANDABLE_STATE_MISSING, snap, packageName, screenTitle,
                "Espandibile senza stato aperto/chiuso.", 0.8f)
        }

        if (includes(ViolationArea.FORMS) && snap.isEditable && !snap.isPassword && snap.className.contains("password", true)) {
            violations += v(ViolationType.PASSWORD_NOT_MASKED, snap, packageName, screenTitle,
                "Campo password non marcato isPassword.", 0.95f)
        }

        if (includes(ViolationArea.LABELS) && snap.isClickable && snap.isCustomView() &&
            !snap.hasAccessibleName() && !snap.hasStandardRole() &&
            !PrecisionRules.shouldSkipContainerLabelCheck(snap, all) &&
            !(PrecisionRules.isCtaContainer(snap) && PrecisionRules.hasTvCustomDescendant(snap, all))
        ) {
            violations += v(ViolationType.ROLE_UNDEFINED, snap, packageName, screenTitle,
                "View custom cliccabile senza ruolo semantico.", 0.85f)
        }

        if (includes(ViolationArea.FORMS) && snap.rangeMin != null && snap.rangeMax != null &&
            (snap.rangeCurrent == null || snap.className.contains("SeekBar", true) || snap.className.contains("Slider", true))
        ) {
            val hasValue = snap.stateDescription?.isNotBlank() == true || snap.contentDescription?.contains("%") == true
            if (!hasValue && snap.rangeCurrent == snap.rangeMin) {
                violations += v(ViolationType.SLIDER_VALUE_MISSING, snap, packageName, screenTitle,
                    "Slider/progresso senza valore annunciato.", 0.78f)
            }
        }

        if (includes(ViolationArea.LABELS) && !snap.tooltipText.isNullOrBlank() && snap.contentDescription.isNullOrBlank() && !snap.isFocusable) {
            violations += v(ViolationType.TOOLTIP_INACCESSIBLE, snap, packageName, screenTitle,
                "Tooltip \"${snap.tooltipText}\" non accessibile a TalkBack.", 0.8f)
        }

        if (includes(ViolationArea.SCREEN_READER) && PrecisionRules.shouldReportCustomAction(snap, all)) {
            val actionKey = snap.viewId?.takeIf { it.isNotBlank() }
                ?: "${snap.className}@${snap.bounds.hashCode()}"
            if (customActionEmitted.add(actionKey)) {
                violations += v(ViolationType.CUSTOM_ACTION_UNLABELED, snap, packageName, screenTitle,
                    "${snap.unlabeledActionCount} azione/i personalizzata/e senza etichetta.", 0.88f)
            }
        }

        if (includes(ViolationArea.MEDIA_WEB) && snap.isMediaControl() && !snap.hasAccessibleName()) {
            violations += v(ViolationType.MEDIA_CONTROL_UNLABELED, snap, packageName, screenTitle,
                "Controllo media senza etichetta.", 0.92f)
        }

        if (includes(ViolationArea.COLOR)) {
            screenshot?.let { checkContrast(snap, packageName, screenTitle, violations, it) }
        }
    }

    private fun checkContrast(
        snap: NodeSnapshot,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        bitmap: Bitmap,
    ) {
        if (PrecisionRules.isLayoutContainer(snap.className)) return
        if (PrecisionRules.isLikelyStatusBadge(snap)) return
        val screenArea = bitmap.width * bitmap.height
        if (screenArea > 0 && snap.area() > screenArea * 0.6) return

        if (snap.hasVisibleText()) {
            val large = WcagContrast.isLargeText(snap.bounds.height(), density)
            val result = WcagContrast.measureTextContrast(bitmap, snap.bounds, large) ?: return
            if (!WcagContrast.isReliableMeasurement(result)) return
            val isFieldLabel = PrecisionRules.isKnownContrastFieldLabel(snap)
            val minConfidence = if (isFieldLabel) 0.60f else 0.72f
            if (result.confidence < minConfidence) return
            if (!isFieldLabel &&
                WcagContrast.relativeLuminance(result.foreground) > 0.80 &&
                snap.bounds.height() <= (minTouchTargetPx * 0.85f).toInt()
            ) {
                return
            }
            val threshold = if (large) WcagContrast.MIN_LARGE_TEXT_CONTRAST else WcagContrast.MIN_TEXT_CONTRAST
            if (result.ratio < threshold) {
                violations += v(ViolationType.LOW_COLOR_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto ${"%.2f".format(result.ratio)}:1 (serve ≥ $threshold:1). " +
                        "${result.samplesUsed} campioni, confidenza ${(result.confidence * 100).toInt()}%.",
                    result.confidence)
            }
        } else if (snap.isInteractiveClickable() || snap.isImageClass()) {
            val result = WcagContrast.measureUiContrast(bitmap, snap.bounds) ?: return
            if (!WcagContrast.isReliableMeasurement(result)) return
            if (result.confidence < 0.72f) return
            if (result.ratio < WcagContrast.MIN_NON_TEXT_CONTRAST) {
                violations += v(ViolationType.LOW_NON_TEXT_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto UI ${"%.2f".format(result.ratio)}:1 (serve ≥ ${WcagContrast.MIN_NON_TEXT_CONTRAST}:1).",
                    result.confidence)
            }
        }
    }

    private fun checkCrossNodeIssues(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
    ) {
        val clickables = snapshots.filter { it.isInteractiveClickable() }

        snapshots.mapNotNull { snap -> snap.accessibleName()?.lowercase()?.let { it to snap } }
            .groupBy({ it.first }, { it.second })
            .forEach { (name, nodes) ->
                if (name.isBlank() || nodes.size < 2) return@forEach
                val distinctBounds = nodes.map { it.bounds }.distinctBy { "${it.left},${it.top},${it.right},${it.bottom}" }
                if (distinctBounds.size < nodes.size) {
                    nodes.forEach { snap ->
                        violations += v(ViolationType.DUPLICATE_ACCESSIBLE_NAME, snap, packageName, screenTitle,
                            "Nome \"$name\" duplicato su elementi distinti.", 0.9f)
                    }
                }
            }

        for (i in clickables.indices) {
            for (j in i + 1 until clickables.size) {
                val a = clickables[i]
                val b = clickables[j]
                if (a.bounds.contains(b.bounds) || b.bounds.contains(a.bounds)) continue
                if (Rect.intersects(a.bounds, b.bounds)) {
                    val overlap = overlapArea(a.bounds, b.bounds)
                    val minArea = minOf(a.area(), b.area())
                    if (overlap > minArea * 0.3) {
                        violations += v(ViolationType.OVERLAPPING_TOUCH_TARGETS, a, packageName, screenTitle,
                            "Sovrapposizione ${overlap}px² con ${b.className}.", 0.88f)
                    }
                } else {
                    val distance = edgeDistance(a.bounds, b.bounds)
                    if (distance in 1 until minTouchSpacingPx &&
                        !PrecisionRules.shouldSkipTouchSpacingBetween(a, b)
                    ) {
                        violations += v(ViolationType.INSUFFICIENT_TOUCH_SPACING, a, packageName, screenTitle,
                            "Solo ${distance}px da un altro pulsante.", 0.85f)
                    }
                }
            }
        }

        snapshots.forEach { parent ->
            if (!parent.hasAccessibleName() || parent.isInteractiveClickable()) return@forEach
            snapshots.forEach { child ->
                if (parent == child || !parent.bounds.contains(child.bounds)) return@forEach
                if (parent.accessibleName() == child.accessibleName() && child.hasAccessibleName() && !child.isInteractiveClickable()) {
                    violations += v(ViolationType.REDUNDANT_ACCESSIBLE_NAME, child, packageName, screenTitle,
                        "Nome ripetuto dal contenitore.", 0.75f)
                }
            }
        }
    }

    private fun checkWebViews(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        snapshots.filter { it.isWebView() && it.childCount == 0 && it.bounds.width() > 100 }.forEach { snap ->
            violations += v(ViolationType.WEBVIEW_BARRIER, snap, packageName, screenTitle,
                "WebView senza contenuto accessibile esposto (${snap.bounds.width()}×${snap.bounds.height()} px).", 0.9f)
        }
    }

    private fun checkTables(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val gridItems = snapshots.filter { it.collectionRow >= 0 && it.collectionColumn >= 0 }
        if (gridItems.size < 6) return
        val rows = gridItems.map { it.collectionRow }.distinct().size
        val cols = gridItems.map { it.collectionColumn }.distinct().size
        if (rows < 2 || cols < 2) return
        val hasHeader = gridItems.any { it.isHeading || it.collectionRow == 0 || it.collectionColumn == 0 }
        if (!hasHeader) {
            violations += AccessibilityViolation(
                type = ViolationType.TABLE_HEADER_MISSING,
                viewClassName = "Collection/Grid",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Griglia ${rows}×${cols} senza intestazioni marcate.",
                confidence = 0.8f,
            )
        }
    }

    private fun checkDuplicateViewIds(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        snapshots.mapNotNull { snap -> snap.viewId?.let { it to snap } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .forEach { (id, nodes) ->
                if (isListItemTemplate(id, nodes)) return@forEach
                val representative = nodes.minByOrNull { it.traversalIndex } ?: return@forEach
                violations += v(
                    ViolationType.DUPLICATE_VIEW_ID,
                    representative,
                    packageName,
                    screenTitle,
                    "ID $id condiviso da ${nodes.size} elementi (anomalo, non template lista).",
                    0.95f,
                )
            }
    }

    private fun isListItemTemplate(viewId: String, nodes: List<NodeSnapshot>): Boolean {
        if (nodes.size < 2) return false
        if (PrecisionRules.isKnownListTemplateId(viewId)) {
            if (nodes.map { it.className }.distinct().size == 1) return true
        }
        val sameClass = nodes.map { it.className }.distinct().size == 1
        if (!sameClass) return false
        val heights = nodes.map { it.bounds.height() }
        val avg = heights.average()
        if (heights.all { kotlin.math.abs(it - avg) <= avg * 0.15 + 2 }) return true
        // Carousel: stessa larghezza, altezze diverse, item impilati verticalmente
        val widths = nodes.map { it.bounds.width() }
        val widthAvg = widths.average()
        val widthSimilar = widths.all { kotlin.math.abs(it - widthAvg) <= widthAvg * 0.12 + 4 }
        val distinctTops = nodes.map { it.bounds.top }.distinct().size >= 2
        return widthSimilar && distinctTops
    }

    private fun checkDuplicateLinks(snapshots: List<NodeSnapshot>, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        snapshots.filter { it.isLikelyLink() && it.hasAccessibleName() }
            .groupBy { it.accessibleName()!!.lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (text, links) ->
                val distinctIds = links.mapNotNull { it.viewId }.distinct()
                if (distinctIds.size > 1 || links.map { it.bounds }.distinct().size > 1) {
                    links.forEach { snap ->
                        violations += v(ViolationType.DUPLICATE_LINK_TEXT, snap, packageName, screenTitle,
                            "Link \"$text\" ripetuto con destinazioni probabilmente diverse.", 0.82f)
                    }
                }
            }
    }

    private fun checkModalTitle(root: AccessibilityNodeInfo, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val className = root.className?.toString().orEmpty()
        val isModal = listOf("Dialog", "BottomSheet", "Popup", "AlertDialog").any { className.contains(it, true) }
        if (isModal && (screenTitle == "Schermata" || screenTitle.isBlank())) {
            violations += AccessibilityViolation(
                type = ViolationType.MODAL_WITHOUT_TITLE,
                viewClassName = className,
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Modale senza titolo accessibile.",
                confidence = 0.9f,
            )
        }
    }

    private fun checkCollectionStructure(root: AccessibilityNodeInfo, packageName: String, screenTitle: String, violations: MutableList<AccessibilityViolation>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val collection = node.collectionInfo
            if (collection != null && node.childCount > 4) {
                val hasStructure = collection.rowCount > 0 || collection.columnCount > 0
                if (!hasStructure) {
                    violations += AccessibilityViolation(
                        type = ViolationType.COLLECTION_WITHOUT_STRUCTURE,
                        viewClassName = node.className?.toString() ?: "unknown",
                        screenTitle = screenTitle,
                        packageName = packageName,
                        details = "Lista con ${node.childCount} elementi senza struttura esposta.",
                        viewId = node.viewIdResourceName,
                        confidence = 0.85f,
                    )
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
    }

    private fun overlapArea(a: Rect, b: Rect): Int {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (left < right && top < bottom) (right - left) * (bottom - top) else 0
    }

    private fun edgeDistance(a: Rect, b: Rect): Int {
        val dx = when {
            a.right < b.left -> b.left - a.right
            b.right < a.left -> a.left - b.right
            else -> 0
        }
        val dy = when {
            a.bottom < b.top -> b.top - a.bottom
            b.bottom < a.top -> a.top - b.bottom
            else -> 0
        }
        return maxOf(dx, dy)
    }

    private fun isNonDescriptiveLink(name: String): Boolean {
        val n = name.trim().lowercase()
        return NON_DESCRIPTIVE_LINKS.any { n == it || n.matches(Regex("^$it\\W*")) }
    }

    private fun v(type: ViolationType, snap: NodeSnapshot, pkg: String, screen: String, details: String, confidence: Float) =
        AccessibilityViolation(
            type = type,
            viewClassName = snap.className,
            screenTitle = screen,
            packageName = pkg,
            details = details,
            viewId = snap.viewId,
            bounds = snap.boundsLabel(),
            sectionTitle = snap.sectionTitle,
            confidence = confidence,
            screenFingerprint = analyzeFingerprint,
        )

    private fun NodeSnapshot.area() = bounds.width() * bounds.height()

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

    data class AnalysisResult(
        val violations: List<AccessibilityViolation>,
        val screenReaderFindings: List<ScreenReaderFinding>,
    )

    companion object {
        private val NON_DESCRIPTIVE_LINKS = setOf(
            "click here", "tap here", "here", "more", "read more", "learn more", "details", "link",
            "continue", "go", "ok", "submit", "clicca qui", "qui", "altro", "leggi", "leggi tutto",
            "scopri", "continua", "dettagli", "vai", "info", "apri", "tap",
        )

        fun create(
            density: Float,
            dynamicContentSilent: Boolean = false,
            scanScope: ScanScope = ScanScope.FULL,
        ) = NodeAccessibilityAnalyzer(
            minTouchTargetPx = (48 * density).toInt(),
            minTouchSpacingPx = (8 * density).toInt(),
            minTextHeightPx = (12 * density).toInt(),
            recommendedTextHeightPx = (16 * density).toInt(),
            density = density,
            dynamicContentSilent = dynamicContentSilent,
            scanScope = scanScope,
        )
    }
}
