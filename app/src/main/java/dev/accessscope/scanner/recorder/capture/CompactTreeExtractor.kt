/**
 * Estrazione albero accessibility compatto per revisione AI Maestro.
 */
package dev.accessscope.scanner.recorder.capture

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.model.CompactA11yNode

/**
 * Cammina l'albero a11y e raccoglie nodi interattivi/leggibili per contesto multimodal.
 */
object CompactTreeExtractor {

    private const val DEFAULT_MAX_NODES = 60
    private const val MAX_TEXT_LEN = 120

    /**
     * Estrae nodi significativi da [root] (ordinati per profondità e rilevanza).
     *
     * @param root Root finestra; se `null` restituisce lista vuota.
     * @return Al massimo [MAX_NODES] nodi con metadati completi.
     */
    fun extract(root: AccessibilityNodeInfo?, maxNodes: Int = DEFAULT_MAX_NODES): List<CompactA11yNode> {
        if (root == null) return emptyList()
        val limit = maxNodes.coerceIn(20, 150)
        val collected = mutableListOf<Pair<Int, CompactA11yNode>>()
        walk(root, depth = 0, collected = collected, maxNodes = limit)
        return collected
            .sortedWith(compareBy({ -score(it.second) }, { it.first }))
            .take(limit)
            .map { it.second }
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        collected: MutableList<Pair<Int, CompactA11yNode>>,
        maxNodes: Int,
    ) {
        if (collected.size >= maxNodes * 2) return
        val compact = toCompact(node, depth)
        if (compact != null) {
            collected += depth to compact
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, collected, maxNodes)
            child.recycle()
        }
    }

    private fun toCompact(node: AccessibilityNodeInfo, depth: Int): CompactA11yNode? {
        val viewId = MaestroSelectorHeuristics.shortViewId(node.viewIdResourceName)
        val text = node.text?.toString()?.trim()?.take(MAX_TEXT_LEN)?.takeIf { it.isNotBlank() }
        val cd = node.contentDescription?.toString()?.trim()?.take(MAX_TEXT_LEN)?.takeIf { it.isNotBlank() }
        val clickable = node.isClickable
        val editable = node.isEditable
        val password = node.isPassword
        val checked = if (node.isCheckable) node.isChecked else null
        val selected = if (node.isSelected) true else null
        val className = node.className?.toString()?.substringAfterLast('.')?.take(40)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val hasBounds = bounds.width() > 0 && bounds.height() > 0
        if (viewId.isNullOrBlank() && text.isNullOrBlank() && cd.isNullOrBlank() &&
            !clickable && !editable
        ) {
            return null
        }
        val role = buildList {
            if (clickable) add("clickable")
            if (editable) add("editable")
            if (node.isScrollable) add("scrollable")
            if (node.isFocusable) add("focusable")
        }.joinToString(",").takeIf { it.isNotBlank() }
        return CompactA11yNode(
            viewId = viewId,
            text = text,
            contentDescription = cd,
            className = className,
            role = role,
            boundsPx = if (hasBounds) listOf(bounds.left, bounds.top, bounds.right, bounds.bottom) else emptyList(),
            clickable = clickable,
            editable = editable,
            password = password,
            checked = checked,
            selected = selected,
            depth = depth,
        )
    }

    private fun score(node: CompactA11yNode): Int {
        var s = 0
        if (!node.viewId.isNullOrBlank()) s += 4
        if (!node.text.isNullOrBlank()) s += 3
        if (!node.contentDescription.isNullOrBlank()) s += 2
        if (node.clickable) s += 3
        if (node.editable) s += 3
        if (node.password) s += 2
        return s
    }
}
