package dev.accessscope.scanner.analyzer

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object ScreenFingerprint {

    fun compute(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
    ): String {
        val viewIds = mutableListOf<String>()
        collectInteractiveIds(root, viewIds, limit = 20)
        val sortedIds = viewIds.sorted().joinToString("|")
        return "$packageName::$screenTitle::$sortedIds::${root.childCount}"
    }

    private fun collectInteractiveIds(
        node: AccessibilityNodeInfo,
        output: MutableList<String>,
        limit: Int,
    ) {
        if (output.size >= limit) return
        if (!node.isVisibleToUser) return

        val id = node.viewIdResourceName
        val interactive = node.isClickable || node.isFocusable || node.isEditable
        if (interactive && !id.isNullOrBlank()) {
            output.add(id)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInteractiveIds(child, output, limit)
            child.recycle()
        }
    }
}
