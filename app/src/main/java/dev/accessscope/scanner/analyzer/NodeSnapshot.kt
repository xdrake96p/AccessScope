package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class NodeSnapshot(
    val className: String,
    val bounds: Rect,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val hintText: String?,
    val tooltipText: String?,
    val isClickable: Boolean,
    val isLongClickable: Boolean,
    val isFocusable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isScrollable: Boolean,
    val isEnabled: Boolean,
    val isPassword: Boolean,
    val isHeading: Boolean,
    val headingLevel: Int,
    val hasLabeledBy: Boolean,
    val hasLabelFor: Boolean,
    val errorText: String?,
    val stateDescription: String?,
    val isExpanded: Boolean?,
    val collectionRow: Int,
    val collectionColumn: Int,
    val childCount: Int,
    val isLikelyDecorative: Boolean,
    val traversalIndex: Int,
    val rangeCurrent: Float?,
    val rangeMin: Float?,
    val rangeMax: Float?,
    val unlabeledActionCount: Int,
    val minTextHeightPx: Int,
    val minTouchTargetPx: Int,
) {
    fun boundsLabel() = "${bounds.width()}×${bounds.height()} px @(${bounds.left},${bounds.top})"

    fun hasAccessibleName(): Boolean =
        !contentDescription.isNullOrBlank() || !text.isNullOrBlank() ||
            !hintText.isNullOrBlank() || hasLabeledBy

    fun accessibleName(): String? =
        contentDescription?.takeIf { it.isNotBlank() }
            ?: text?.takeIf { it.isNotBlank() }
            ?: hintText?.takeIf { it.isNotBlank() }

    fun hasInputLabel(): Boolean =
        hasAccessibleName() || hasLabeledBy || hasLabelFor || !errorText.isNullOrBlank()

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
        val standard = listOf("Button", "TextView", "Image", "Edit", "Check", "Switch", "Radio", "Spinner", "WebView")
        return standard.none { className.contains(it, ignoreCase = true) }
    }

    fun hasStandardRole(): Boolean =
        className.contains("Button", true) ||
            className.contains("CheckBox", true) ||
            className.contains("Switch", true) ||
            className.contains("EditText", true)

    fun isMediaControl(): Boolean {
        val name = accessibleName()?.lowercase().orEmpty()
        val cls = className.lowercase()
        return cls.contains("media") || cls.contains("exo") ||
            name in setOf("play", "pause", "stop", "riproduci", "pausa", "stop")
    }

    fun isWebView(): Boolean = className.contains("WebView", true)

    fun visualSortKey(): Int = bounds.top * 100_000 + bounds.left
}

fun AccessibilityNodeInfo.toSnapshot(
    traversalIndex: Int,
    minTextHeightPx: Int,
    minTouchTargetPx: Int,
): NodeSnapshot? {
    if (!isVisibleToUser) return null
    val bounds = Rect()
    getBoundsInScreen(bounds)
    if (bounds.width() <= 0 || bounds.height() <= 0) return null

    val range = rangeInfo
    val collectionItem = collectionItemInfo
    val isHeadingMarked = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        collectionItem?.isHeading == true
    } else {
        false
    }
    val headingLevel = when {
        isHeadingMarked -> estimateHeadingLevel(bounds.height())
        else -> 0
    }

    var unlabeledActions = 0
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        actionList?.forEach { action ->
            val label = action.label?.toString()
            if (label.isNullOrBlank() && action.id > AccessibilityNodeInfo.ACTION_FOCUS) {
                unlabeledActions++
            }
        }
    }

    return NodeSnapshot(
        className = className?.toString() ?: "unknown",
        bounds = bounds,
        viewId = viewIdResourceName,
        text = text?.toString(),
        contentDescription = contentDescription?.toString(),
        hintText = hintText?.toString(),
        tooltipText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tooltipText?.toString()
        } else {
            null
        },
        isClickable = isClickable,
        isLongClickable = isLongClickable,
        isFocusable = isFocusable,
        isEditable = isEditable || className?.toString().orEmpty().contains("EditText", true),
        isCheckable = isCheckable,
        isChecked = isChecked,
        isScrollable = isScrollable,
        isEnabled = isEnabled,
        isPassword = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) isPassword else false,
        isHeading = isHeadingMarked,
        headingLevel = headingLevel,
        hasLabeledBy = labeledBy != null,
        hasLabelFor = labelFor != null,
        errorText = error?.toString(),
        stateDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            stateDescription?.toString()
        } else {
            null
        },
        isExpanded = resolveExpandedState(this),
        collectionRow = collectionItemInfo?.rowIndex ?: -1,
        collectionColumn = collectionItemInfo?.columnIndex ?: -1,
        childCount = childCount,
        isLikelyDecorative = !isClickable && !isFocusable && !isCheckable &&
            (className?.toString().orEmpty().contains("Image", true)),
        traversalIndex = traversalIndex,
        rangeCurrent = range?.current,
        rangeMin = range?.min,
        rangeMax = range?.max,
        unlabeledActionCount = unlabeledActions,
        minTextHeightPx = minTextHeightPx,
        minTouchTargetPx = minTouchTargetPx,
    )
}

private fun estimateHeadingLevel(heightPx: Int): Int = when {
    heightPx >= 72 -> 1
    heightPx >= 56 -> 2
    heightPx >= 44 -> 3
    heightPx >= 36 -> 4
    else -> 5
}

private fun resolveExpandedState(node: AccessibilityNodeInfo): Boolean? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val state = node.stateDescription?.toString()?.lowercase().orEmpty()
        if (state.contains("espans") || state.contains("expand") || state.contains("apert")) return true
        if (state.contains("collass") || state.contains("collaps") || state.contains("chius")) return false
    }
    if (node.className?.toString().orEmpty().contains("Expandable", true)) return null
    return null
}
