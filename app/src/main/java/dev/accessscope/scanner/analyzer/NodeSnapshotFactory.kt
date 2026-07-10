/**
 * Factory per convertire nodi accessibility in [NodeSnapshot].
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Converte un [AccessibilityNodeInfo] visibile in [NodeSnapshot], oppure `null` se escluso.
 *
 * @param traversalIndex Indice di attraversamento depth-first assegnato al nodo.
 * @param minTextHeightPx Soglia minima altezza testo in pixel.
 * @param minTouchTargetPx Soglia minima target di tocco in pixel.
 * @param sectionTitle Titolo heading della sezione corrente, se noto.
 * @return Snapshot del nodo, oppure `null` se non visibile o con bounds invalidi.
 */
fun AccessibilityNodeInfo.toSnapshot(
    traversalIndex: Int,
    minTextHeightPx: Int,
    minTouchTargetPx: Int,
    sectionTitle: String? = null,
): NodeSnapshot? {
    if (!isVisibleToUser) return null
    val bounds = Rect()
    getBoundsInScreen(bounds)
    if (bounds.width() <= 0 || bounds.height() <= 0) return null

    val range = rangeInfo
    val collectionItem = collectionItemInfo
    val isHeadingMarked = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P -> isHeading
        else -> false
    } || (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        collectionItem?.isHeading == true
    } else {
        false
    })
    val headingLevel = when {
        isHeadingMarked -> estimateHeadingLevel(bounds.height())
        else -> 0
    }

    var unlabeledActions = 0
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        actionList?.forEach { action ->
            if (action.id < CUSTOM_ACTION_ID_MIN) return@forEach
            val label = action.label?.toString()
            if (label.isNullOrBlank()) unlabeledActions++
        }
    }

    val isExcluded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isImportantForAccessibility

    val smallThreshold = minTouchTargetPx / 2
    val classStr = className?.toString().orEmpty()
    val isImage = classStr.contains("Image", true) || classStr.contains("Icon", true)
    val density = minTouchTargetPx / 48f
    val textSizeSp = if (classStr.contains("TextView", true) && bounds.height() > 0) {
        // Approssimazione: l'altezza bounds è più vicina alla line-height (~1.2× textSize).
        // Usiamo density come proxy di scaledDensity (fontScale=1) per evitare falsi "large text"
        // su testi normali (es. 14sp che spesso misura ~18dp di altezza).
        bounds.height() / (density * 1.2f)
    } else {
        null
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
        isAccessibilityExcluded = isExcluded,
        isLikelyDecorative = isExcluded ||
            (bounds.width() < smallThreshold && bounds.height() < smallThreshold && isImage) ||
            (!isClickable && !isFocusable && !isCheckable && isImage),
        traversalIndex = traversalIndex,
        rangeCurrent = range?.current,
        rangeMin = range?.min,
        rangeMax = range?.max,
        unlabeledActionCount = unlabeledActions,
        minTextHeightPx = minTextHeightPx,
        minTouchTargetPx = minTouchTargetPx,
        textSizeSp = textSizeSp,
        sectionTitle = sectionTitle,
    )
}

/** Soglia minima ID per le azioni personalizzate accessibility (0x01000000). */
private const val CUSTOM_ACTION_ID_MIN = 0x01000000

/**
 * Stima il livello heading (1–5) dall'altezza del testo in pixel.
 *
 * @param heightPx Altezza del bounds in pixel.
 * @return Livello stimato, dove 1 è il titolo più prominente.
 */
private fun estimateHeadingLevel(heightPx: Int): Int = when {
    heightPx >= 72 -> 1
    heightPx >= 56 -> 2
    heightPx >= 44 -> 3
    heightPx >= 36 -> 4
    else -> 5
}

/**
 * Risolve lo stato espanso/collassato da stateDescription o tipo ExpandableListView.
 *
 * @param node Nodo di cui determinare lo stato.
 * @return `true` se espanso, `false` se collassato, `null` se non determinabile.
 */
private fun resolveExpandedState(node: AccessibilityNodeInfo): Boolean? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val state = node.stateDescription?.toString()?.lowercase().orEmpty()
        if (state.contains("espans") || state.contains("expand") || state.contains("apert")) return true
        if (state.contains("collass") || state.contains("collaps") || state.contains("chius")) return false
    }
    if (node.className?.toString().orEmpty().contains("Expandable", true)) return null
    return null
}
