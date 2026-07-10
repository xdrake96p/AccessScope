package dev.accessscope.scanner.analyzer.node

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.toSnapshot
import java.util.ArrayDeque

internal object NodeTreeCollector {

    fun collectSnapshots(
        node: AccessibilityNodeInfo,
        output: MutableList<NodeSnapshot>,
        headingStack: ArrayDeque<String>,
        nextIndex: () -> Int,
        minTextHeightPx: Int,
        minTouchTargetPx: Int,
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
            collectSnapshots(child, output, headingStack, nextIndex, minTextHeightPx, minTouchTargetPx)
            child.recycle()
        }

        if (pushedHeading != null) {
            headingStack.removeLast()
        }
    }
}
