/**
 * Rileva schermate protette (PIN, password, OTP) dove lo screenshot è bloccato.
 */
package dev.accessscope.scanner.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.ScreenTitleResolver

object SecureScreenDetector {

    private val SECURE_TITLE_KEYWORDS = listOf("pin", "otp", "password", "passcode", "codice")

    fun isSecureContext(
        root: AccessibilityNodeInfo,
        screenTitle: String,
        packageName: String,
    ): Boolean {
        if (ScreenTitleResolver.isPinScreen(root)) return true
        if (SECURE_TITLE_KEYWORDS.any { screenTitle.contains(it, ignoreCase = true) }) return true
        return hasSecureNodes(root, packageName)
    }

    private fun hasSecureNodes(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val pinKeys = AppPrecisionProfiles.pinPadKeyIds(packageName)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            if (id in pinKeys || id.contains("pin_pad") || id.contains("pinpad")) return true
            if (node.isPassword) return true
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("Password", ignoreCase = true)) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return false
    }

    /**
     * Viewport schermata dai bounds della radice.
     */
    fun rootViewport(root: AccessibilityNodeInfo): Rect {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) return rect
        return Rect(0, 0, 1080, 2400)
    }

    /**
     * Fino a [maxNodes] rettangoli vicini al focus per contesto wireframe.
     */
    fun collectNearbyBounds(
        root: AccessibilityNodeInfo,
        focus: Rect,
        maxNodes: Int = 4,
    ): List<Rect> {
        val expanded = Rect(focus).apply { inset(-focus.width() / 2, -focus.height()) }
        val results = mutableListOf<Rect>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && results.size < maxNodes) {
            val node = queue.removeFirst()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 8 && bounds.height() > 8 &&
                Rect.intersects(expanded, bounds) &&
                bounds.left != focus.left || bounds.top != focus.top ||
                    bounds.right != focus.right || bounds.bottom != focus.bottom
            ) {
                results.add(bounds)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return results
    }
}
