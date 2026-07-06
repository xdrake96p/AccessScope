package dev.accessscope.scanner.analyzer

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object ScreenTitleResolver {

    fun resolve(root: AccessibilityNodeInfo, event: AccessibilityEvent): String {
        event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            return humanizeTitle(it)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root.paneTitle?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
                return humanizeTitle(it)
            }
        }

        findModalTitle(root)?.let { return it }
        findAppBarTitle(root)?.let { return it }
        findProminentHeading(root)?.let { return it }

        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            return humanizeTitle(it)
        }

        val activityName = event.className?.toString()?.substringAfterLast('.').orEmpty()
        if (activityName.isNotBlank()) return humanizeActivityName(activityName)

        return "Schermata"
    }

    private fun findAppBarTitle(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val className = node.className?.toString().orEmpty()
            val isBar = className.contains("Toolbar", true) ||
                className.contains("ActionBar", true) ||
                className.contains("AppBar", true) ||
                className.contains("CollapsingToolbar", true) ||
                node.viewIdResourceName?.contains("toolbar", true) == true ||
                node.viewIdResourceName?.contains("action_bar", true) == true

            if (isBar) {
                node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return humanizeTitle(it) }
                node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    return humanizeTitle(it)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun findModalTitle(root: AccessibilityNodeInfo): String? {
        val className = root.className?.toString().orEmpty()
        val isModal = listOf("Dialog", "BottomSheet", "Popup", "AlertDialog", "Modal")
            .any { className.contains(it, true) }
        if (!isModal) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val isHeading = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.collectionItemInfo?.isHeading == true
            } else {
                false
            }
            val text = node.text?.toString()?.trim().orEmpty()
            if ((isHeading || node.className?.toString().orEmpty().contains("Title", true)) &&
                text.isNotBlank() && text.length <= 80
            ) {
                return humanizeTitle(text)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun findProminentHeading(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val topThreshold = screenBounds.top + (screenBounds.height() * 0.28f).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: Pair<String, Int>? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString()?.trim().orEmpty()
            val isHeading = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.collectionItemInfo?.isHeading == true
            } else {
                false
            }
            val looksLikeTitle = isHeading ||
                (node.className?.toString().orEmpty().contains("TextView", true) &&
                    !node.isClickable && text.isNotBlank() && text.length <= 60)

            if (looksLikeTitle && bounds.top <= topThreshold && text.isNotBlank()) {
                val score = bounds.height()
                if (best == null || score > best!!.second) {
                    best = humanizeTitle(text) to score
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return best?.first
    }

    private fun humanizeActivityName(name: String): String {
        val cleaned = name
            .removeSuffix("Activity")
            .removeSuffix("Fragment")
            .removeSuffix("Screen")
            .removeSuffix("Page")
        return humanizeTitle(
            cleaned.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").trim().ifBlank { name },
        )
    }

    private fun humanizeTitle(title: String): String =
        title.trim().replace(Regex("\\s+"), " ")
}
