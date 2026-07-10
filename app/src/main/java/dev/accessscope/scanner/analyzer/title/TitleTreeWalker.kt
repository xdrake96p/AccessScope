package dev.accessscope.scanner.analyzer.title

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

internal object TitleTreeWalker {

    fun collectViewIdShorts(root: AccessibilityNodeInfo): Set<String> {
                val ids = mutableSetOf<String>()
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(root)
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    node.viewIdResourceName?.substringAfterLast('/')?.lowercase()?.let { ids.add(it) }
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let(queue::add)
                    }
                }
                return ids
    }

    fun hasScrollableContent(root: AccessibilityNodeInfo): Boolean {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(root)
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    val cls = node.className?.toString().orEmpty()
                    if (cls.contains("ScrollView", true) ||
                        cls.contains("RecyclerView", true) ||
                        cls.contains("NestedScroll", true) ||
                        cls.contains("ViewPager", true)
                    ) {
                        return true
                    }
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let(queue::add)
                    }
                }
                return false
    }

    fun findPinScreen(root: AccessibilityNodeInfo): String? = TitlePinWalker.findPinScreen(root)
    fun findTopBarTitle(root: AccessibilityNodeInfo): String? = TitleTopBarWalker.findTopBarTitle(root)
    fun findSectionTitle(root: AccessibilityNodeInfo): String? = TitleSectionWalker.findSectionTitle(root)
    fun findModalTitle(root: AccessibilityNodeInfo): String? = TitleSectionWalker.findModalTitle(root)
    fun findProminentHeading(root: AccessibilityNodeInfo): String? = TitleSectionWalker.findProminentHeading(root)
}
