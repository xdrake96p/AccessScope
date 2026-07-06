package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType

class NodeAccessibilityAnalyzer(
    private val minTouchTargetPx: Int,
    private val minTouchSpacingPx: Int,
    private val minTextHeightPx: Int,
    private val recommendedTextHeightPx: Int,
    private val density: Float,
) {

    fun analyzeTree(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        screenshot: Bitmap? = null,
    ): AnalysisResult {
        val violations = mutableListOf<AccessibilityViolation>()
        val snapshots = mutableListOf<NodeSnapshot>()
        collectSnapshots(root, snapshots)

        snapshots.forEach { snap ->
            checkSingleNode(snap, packageName, screenTitle, violations, screenshot)
        }

        checkCrossNodeIssues(snapshots, packageName, screenTitle, violations)
        checkModalTitle(root, packageName, screenTitle, violations)
        checkCollectionStructure(root, packageName, screenTitle, violations)

        val screenReaderFindings = TalkBackSimulator().simulate(root, packageName, screenTitle)

        return AnalysisResult(violations, screenReaderFindings)
    }

    private fun collectSnapshots(node: AccessibilityNodeInfo, output: MutableList<NodeSnapshot>) {
        if (!node.isVisibleToUser) {
            recycleChildren(node)
            return
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            output += NodeSnapshot(
                className = node.className?.toString() ?: "unknown",
                bounds = bounds,
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                hintText = node.hintText?.toString(),
                isClickable = node.isClickable,
                isLongClickable = node.isLongClickable,
                isFocusable = node.isFocusable,
                isEditable = node.isEditable || node.className?.toString().orEmpty().contains("EditText", true),
                isCheckable = node.isCheckable,
                isChecked = node.isChecked,
                isScrollable = node.isScrollable,
                isEnabled = node.isEnabled,
                isPassword = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.isPassword else false,
                isHeading = node.collectionItemInfo?.heading == true,
                hasLabeledBy = node.labeledBy != null,
                hasLabelFor = node.labelFor != null,
                errorText = node.error?.toString(),
                stateDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    node.stateDescription?.toString()
                } else {
                    null
                },
                isExpanded = resolveExpanded(node),
                collectionRow = node.collectionItemInfo?.rowIndex ?: -1,
                collectionColumn = node.collectionItemInfo?.columnIndex ?: -1,
                childCount = node.childCount,
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectSnapshots(child, output)
            child.recycle()
        }
    }

    private fun recycleChildren(node: AccessibilityNodeInfo) {
        for (i in 0 until node.childCount) {
            node.getChild(i)?.recycle()
        }
    }

    private fun resolveExpanded(node: AccessibilityNodeInfo): Boolean? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val state = node.stateDescription?.toString()?.lowercase().orEmpty()
            if (state.contains("espans") || state.contains("expand")) return true
            if (state.contains("collass") || state.contains("collaps")) return false
        }
        val className = node.className?.toString().orEmpty()
        if (className.contains("Expandable", ignoreCase = true)) {
            return null
        }
        return null
    }

    private fun checkSingleNode(
        snap: NodeSnapshot,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenshot: Bitmap?,
    ) {
        val boundsLabel = snap.boundsLabel()
        val className = snap.className

        if (snap.isInteractiveClickable()) {
            if (!snap.hasAccessibleName()) {
                violations += violation(
                    ViolationType.MISSING_LABEL, snap, packageName, screenTitle,
                    "Elemento cliccabile senza contentDescription, testo o hint.",
                )
            }
            if (snap.bounds.width() < minTouchTargetPx || snap.bounds.height() < minTouchTargetPx) {
                violations += violation(
                    ViolationType.SMALL_TOUCH_TARGET, snap, packageName, screenTitle,
                    "Dimensioni ${snap.bounds.width()}x${snap.bounds.height()} px (minimo $minTouchTargetPx px).",
                )
            }
        }

        if (snap.shouldBeFocusable() && !snap.isFocusable) {
            violations += violation(
                ViolationType.NOT_FOCUSABLE, snap, packageName, screenTitle,
                "Elemento interattivo non focalizzabile per screen reader.",
            )
        }

        if (snap.isEditable && !snap.hasInputLabel()) {
            violations += violation(
                ViolationType.INPUT_LABEL, snap, packageName, screenTitle,
                "Campo di input senza hint, label o contentDescription.",
            )
        }

        if (snap.isEditable && snap.isEnabled && snap.errorText.isNullOrBlank() && snap.text.isNullOrBlank()) {
            // Campo vuoto senza errore esplicito non è violazione; controlliamo hint mancante sopra
        }

        if (snap.isEditable && !snap.isEnabled && snap.errorText.isNullOrBlank()) {
            // potenziale campo con validazione visiva ma senza error text accessibile
        }

        if (snap.isEditable && snap.hintText.isNullOrBlank() && snap.hasAccessibleName() &&
            snap.text?.contains("error", ignoreCase = true) == true && snap.errorText.isNullOrBlank()
        ) {
            violations += violation(
                ViolationType.INPUT_ERROR_MISSING, snap, packageName, screenTitle,
                "Possibile stato di errore visivo senza messaggio accessibile associato.",
            )
        }

        if (snap.looksLikeStructuralHeading() && !snap.isHeading && !className.contains("Toolbar", true)) {
            violations += violation(
                ViolationType.HEADING_HIERARCHY, snap, packageName, screenTitle,
                "Testo strutturale non marcato come heading.",
            )
        }

        if (snap.hasVisibleText() && snap.bounds.height() < minTextHeightPx) {
            violations += violation(
                ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                "Altezza testo stimata ${snap.bounds.height()} px (< $minTextHeightPx px, ~12sp).",
            )
        } else if (snap.hasVisibleText() && snap.bounds.height() < recommendedTextHeightPx && snap.isClickable) {
            violations += violation(
                ViolationType.TEXT_TOO_SMALL, snap, packageName, screenTitle,
                "Testo cliccabile piccolo: ${snap.bounds.height()} px (consigliato ≥ $recommendedTextHeightPx px).",
            )
        }

        val textValue = snap.text.orEmpty()
        if (textValue.endsWith("…") || textValue.endsWith("...")) {
            if (snap.contentDescription.isNullOrBlank()) {
                violations += violation(
                    ViolationType.TEXT_TRUNCATED, snap, packageName, screenTitle,
                    "Testo troncato senza contentDescription con testo completo.",
                )
            }
        }

        if (snap.isImageWithoutAlt()) {
            violations += violation(
                ViolationType.IMAGE_MISSING_ALT, snap, packageName, screenTitle,
                "Immagine o icona senza testo alternativo.",
            )
        }

        if (snap.isLikelyLink() && snap.hasAccessibleName() && isNonDescriptiveLink(snap.accessibleName())) {
            violations += violation(
                ViolationType.LINK_NOT_DESCRIPTIVE, snap, packageName, screenTitle,
                "Link con testo generico: \"${snap.accessibleName()}\".",
            )
        }

        if (snap.isScrollable && !snap.hasAccessibleName()) {
            violations += violation(
                ViolationType.SCROLLABLE_WITHOUT_LABEL, snap, packageName, screenTitle,
                "Contenitore scrollabile senza nome accessibile.",
            )
        }

        if (!snap.isEnabled && snap.isInteractiveClickable() && snap.stateDescription.isNullOrBlank()) {
            violations += violation(
                ViolationType.DISABLED_WITHOUT_INDICATION, snap, packageName, screenTitle,
                "Controllo disabilitato senza stateDescription.",
            )
        }

        if (snap.className.contains("Expandable", ignoreCase = true) && snap.isExpanded == null) {
            violations += violation(
                ViolationType.EXPANDABLE_STATE_MISSING, snap, packageName, screenTitle,
                "Elemento espandibile senza stato espanso/collassato esposto.",
            )
        }

        if (snap.isEditable && snap.isPassword.not() && snap.className.contains("password", true)) {
            violations += violation(
                ViolationType.PASSWORD_NOT_MASKED, snap, packageName, screenTitle,
                "Campo password non marcato come isPassword.",
            )
        }

        if (snap.isClickable && snap.isCustomView() && !snap.hasAccessibleName() && !snap.hasStandardRole()) {
            violations += violation(
                ViolationType.ROLE_UNDEFINED, snap, packageName, screenTitle,
                "View personalizzata cliccabile senza ruolo o etichetta semantica.",
            )
        }

        screenshot?.let { bitmap ->
            checkContrast(snap, packageName, screenTitle, violations, bitmap)
        }
    }

    private fun checkContrast(
        snap: NodeSnapshot,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        bitmap: Bitmap,
    ) {
        val fg = WcagContrast.sampleForeground(bitmap, snap.bounds) ?: return
        val bg = WcagContrast.sampleBackground(bitmap, snap.bounds) ?: return
        val ratio = WcagContrast.contrastRatio(fg, bg)

        if (snap.hasVisibleText()) {
            val threshold = if (WcagContrast.isLargeText(snap.bounds.height(), density)) {
                WcagContrast.MIN_LARGE_TEXT_CONTRAST
            } else {
                WcagContrast.MIN_TEXT_CONTRAST
            }
            if (ratio < threshold) {
                violations += violation(
                    ViolationType.LOW_COLOR_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto stimato ${"%.2f".format(ratio)}:1 (richiesto ≥ $threshold:1).",
                )
            }
        } else if (snap.isInteractiveClickable() || snap.isImageWithoutAlt().not() && snap.isImageClass()) {
            if (ratio < WcagContrast.MIN_NON_TEXT_CONTRAST) {
                violations += violation(
                    ViolationType.LOW_NON_TEXT_CONTRAST, snap, packageName, screenTitle,
                    "Contrasto UI stimato ${"%.2f".format(ratio)}:1 (richiesto ≥ ${WcagContrast.MIN_NON_TEXT_CONTRAST}:1).",
                )
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
        val nameGroups = snapshots
            .mapNotNull { snap -> snap.accessibleName()?.let { it.lowercase() to snap } }
            .groupBy({ it.first }, { it.second })

        nameGroups.forEach { (name, nodes) ->
            if (name.isNotBlank() && nodes.size > 1) {
                nodes.forEach { snap ->
                    violations += violation(
                        ViolationType.DUPLICATE_ACCESSIBLE_NAME, snap, packageName, screenTitle,
                        "Nome accessibile \"$name\" duplicato su più elementi.",
                    )
                }
            }
        }

        for (i in clickables.indices) {
            for (j in i + 1 until clickables.size) {
                val a = clickables[i]
                val b = clickables[j]
                if (Rect.intersects(a.bounds, b.bounds)) {
                    val overlap = overlapArea(a.bounds, b.bounds)
                    val minArea = minOf(a.bounds.width() * a.bounds.height(), b.bounds.width() * b.bounds.height())
                    if (overlap > minArea * 0.25) {
                        violations += violation(
                            ViolationType.OVERLAPPING_TOUCH_TARGETS, a, packageName, screenTitle,
                            "Target sovrapposto con ${b.className} (${overlap}px²).",
                        )
                    }
                } else {
                    val distance = edgeDistance(a.bounds, b.bounds)
                    if (distance in 0 until minTouchSpacingPx) {
                        violations += violation(
                            ViolationType.INSUFFICIENT_TOUCH_SPACING, a, packageName, screenTitle,
                            "Distanza ${distance}px da altro target (< $minTouchSpacingPx px).",
                        )
                    }
                }
            }
        }

        snapshots.forEach { parent ->
            if (!parent.hasAccessibleName()) return@forEach
            snapshots.forEach { child ->
                if (parent == child) return@forEach
                if (!parent.bounds.contains(child.bounds)) return@forEach
                if (parent.accessibleName() == child.accessibleName() && child.hasAccessibleName()) {
                    violations += violation(
                        ViolationType.REDUNDANT_ACCESSIBLE_NAME, child, packageName, screenTitle,
                        "Nome accessibile ridondante con contenitore padre.",
                    )
                }
            }
        }
    }

    private fun checkModalTitle(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
    ) {
        val className = root.className?.toString().orEmpty()
        val isModal = className.contains("Dialog", true) ||
            className.contains("BottomSheet", true) ||
            className.contains("Popup", true) ||
            className.contains("AlertDialog", true)

        if (!isModal) return
        if (screenTitle == "Schermata" || screenTitle.isBlank()) {
            violations += AccessibilityViolation(
                type = ViolationType.MODAL_WITHOUT_TITLE,
                viewClassName = className,
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Finestra modale senza titolo accessibile rilevato.",
                bounds = null,
            )
        }
    }

    private fun checkCollectionStructure(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val collection = node.collectionInfo
            if (collection != null && node.childCount > 3) {
                val hasStructure = collection.rowCount > 0 || collection.columnCount > 0
                if (!hasStructure) {
                    violations += AccessibilityViolation(
                        type = ViolationType.COLLECTION_WITHOUT_STRUCTURE,
                        viewClassName = node.className?.toString() ?: "unknown",
                        screenTitle = screenTitle,
                        packageName = packageName,
                        details = "Lista/griglia con ${node.childCount} figli senza metadati di struttura.",
                        viewId = node.viewIdResourceName,
                    )
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
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
        val normalized = name.trim().lowercase()
        return NON_DESCRIPTIVE_LINKS.any { normalized == it || normalized.matches(Regex("^$it\\W*")) }
    }

    private fun violation(
        type: ViolationType,
        snap: NodeSnapshot,
        packageName: String,
        screenTitle: String,
        details: String,
    ) = AccessibilityViolation(
        type = type,
        viewClassName = snap.className,
        screenTitle = screenTitle,
        packageName = packageName,
        details = details,
        viewId = snap.viewId,
        bounds = snap.boundsLabel(),
    )

    data class AnalysisResult(
        val violations: List<AccessibilityViolation>,
        val screenReaderFindings: List<ScreenReaderFinding>,
    )

    data class NodeSnapshot(
        val className: String,
        val bounds: Rect,
        val viewId: String?,
        val text: String?,
        val contentDescription: String?,
        val hintText: String?,
        val isLongClickable: Boolean,
        val isClickable: Boolean,
        val isFocusable: Boolean,
        val isEditable: Boolean,
        val isCheckable: Boolean,
        val isChecked: Boolean,
        val isScrollable: Boolean,
        val isEnabled: Boolean,
        val isPassword: Boolean,
        val isHeading: Boolean,
        val hasLabeledBy: Boolean,
        val hasLabelFor: Boolean,
        val errorText: String?,
        val stateDescription: String?,
        val isExpanded: Boolean?,
        val collectionRow: Int,
        val collectionColumn: Int,
        val childCount: Int,
    ) {
        fun boundsLabel() = "${bounds.width()}x${bounds.height()} @(${bounds.left},${bounds.top})"

        fun hasAccessibleName(): Boolean =
            !contentDescription.isNullOrBlank() || !text.isNullOrBlank() ||
                !hintText.isNullOrBlank() || hasLabeledBy

        fun accessibleName(): String? =
            contentDescription?.takeIf { it.isNotBlank() }
                ?: text?.takeIf { it.isNotBlank() }
                ?: hintText?.takeIf { it.isNotBlank() }

        fun hasInputLabel(): Boolean = hasAccessibleName() || hasLabeledBy || hasLabelFor || !errorText.isNullOrBlank()

        fun hasVisibleText(): Boolean = !text.isNullOrBlank()

        fun isInteractiveClickable(): Boolean {
            if (isClickable || isLongClickable) return true
            return className.contains("Button", true) ||
                className.contains("ImageButton", true) ||
                className.contains("CheckBox", true) ||
                className.contains("Switch", true) ||
                className.contains("Toggle", true)
        }

        fun shouldBeFocusable(): Boolean =
            isClickable || isCheckable || isEditable || isScrollable

        fun looksLikeStructuralHeading(): Boolean {
            val value = text?.trim().orEmpty()
            if (value.isEmpty() || value.length > 80) return false
            return className.contains("TextView", true) && !isClickable && value.split(" ").size <= 12
        }

        fun isImageClass(): Boolean =
            className.contains("Image", true) || className.contains("Icon", true)

        fun isImageWithoutAlt(): Boolean =
            isImageClass() && contentDescription.isNullOrBlank() && text.isNullOrBlank()

        fun isLikelyLink(): Boolean =
            isClickable && (
                className.contains("TextView", true) ||
                    accessibleName()?.contains("http", true) == true ||
                    className.contains("Link", true)
                )

        fun isCustomView(): Boolean {
            val standard = listOf("Button", "TextView", "Image", "Edit", "Check", "Switch", "Radio", "Spinner")
            return standard.none { className.contains(it, ignoreCase = true) }
        }

        fun hasStandardRole(): Boolean =
            className.contains("Button", true) ||
                className.contains("CheckBox", true) ||
                className.contains("Switch", true) ||
                className.contains("EditText", true)
    }

    companion object {
        private val NON_DESCRIPTIVE_LINKS = setOf(
            "click here", "tap here", "here", "more", "read more", "learn more",
            "details", "link", "continue", "go", "ok", "submit",
            "clicca qui", "qui", "altro", "leggi", "leggi tutto", "scopri",
            "continua", "dettagli", "vai", "info", "apri", "tap",
        )

        fun create(density: Float): NodeAccessibilityAnalyzer = NodeAccessibilityAnalyzer(
            minTouchTargetPx = (48 * density).toInt(),
            minTouchSpacingPx = (8 * density).toInt(),
            minTextHeightPx = (12 * density).toInt(),
            recommendedTextHeightPx = (16 * density).toInt(),
            density = density,
        )
    }
}
