/**
 * Rileva schermate protette (PIN, password, OTP, FLAG_SECURE) e valuta l'impatto su screenshot/contrasto.
 */
package dev.accessscope.scanner.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.data.ScreenProtectionReason

/** Esito del rilevamento protezione schermata per la pipeline di scansione. */
data class SecureScreenAssessment(
    val isProtected: Boolean,
    val reason: ScreenProtectionReason,
    val allowContrast: Boolean,
) {
    /** Usa evidenza wireframe invece di screenshot reale. */
    val useSecureEvidence: Boolean
        get() = reason == ScreenProtectionReason.FLAG_SECURE ||
            reason == ScreenProtectionReason.PIN_OR_PASSWORD

    companion object {
        val NONE = SecureScreenAssessment(
            isProtected = false,
            reason = ScreenProtectionReason.NONE,
            allowContrast = true,
        )
    }
}

object SecureScreenDetector {

    private val SECURE_TITLE_PATTERNS = listOf(
        Regex("""\bpin\b""", RegexOption.IGNORE_CASE),
        Regex("""\botp\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpassword\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpasscode\b""", RegexOption.IGNORE_CASE),
        Regex("""inserisci\s+codice""", RegexOption.IGNORE_CASE),
        Regex("""codice\s+pin""", RegexOption.IGNORE_CASE),
    )

    /**
     * Valuta protezione combinando albero a11y e esito screenshot.
     *
     * @param root Radice dell'albero analizzato.
     * @param screenTitle Titolo risolto della schermata.
     * @param packageName Package dell'app target.
     * @param capture Esito acquisizione screenshot (può essere null su API < 30).
     */
    fun assess(
        root: AccessibilityNodeInfo,
        screenTitle: String,
        packageName: String,
        capture: ScreenshotCapture?,
    ): SecureScreenAssessment {
        if (capture?.flagSecure == true) {
            return SecureScreenAssessment(
                isProtected = true,
                reason = ScreenProtectionReason.FLAG_SECURE,
                allowContrast = false,
            )
        }
        val treeReason = treeProtectionReason(root, screenTitle, packageName)
        if (treeReason != ScreenProtectionReason.NONE) {
            return SecureScreenAssessment(
                isProtected = true,
                reason = treeReason,
                allowContrast = false,
            )
        }
        if (capture?.screenshotBlocked == true) {
            return SecureScreenAssessment(
                isProtected = true,
                reason = ScreenProtectionReason.SCREENSHOT_BLOCKED,
                allowContrast = true,
            )
        }
        return SecureScreenAssessment.NONE
    }

    internal fun titleProtectionReason(screenTitle: String): ScreenProtectionReason {
        if (screenTitle.isBlank()) return ScreenProtectionReason.NONE
        return if (SECURE_TITLE_PATTERNS.any { it.containsMatchIn(screenTitle) }) {
            ScreenProtectionReason.PIN_OR_PASSWORD
        } else {
            ScreenProtectionReason.NONE
        }
    }

    private fun treeProtectionReason(
        root: AccessibilityNodeInfo,
        screenTitle: String,
        packageName: String,
    ): ScreenProtectionReason {
        if (ScreenTitleResolver.isPinScreen(root)) return ScreenProtectionReason.PIN_OR_PASSWORD
        val titleReason = titleProtectionReason(screenTitle)
        if (titleReason != ScreenProtectionReason.NONE) return titleReason
        return if (hasSecureNodes(root, packageName)) {
            ScreenProtectionReason.PIN_OR_PASSWORD
        } else {
            ScreenProtectionReason.NONE
        }
    }

    private fun hasSecureNodes(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val pinKeys = AppPrecisionProfiles.pinPadKeyIds(packageName)
        var hasPinPadMarker = false
        var hasNumericPinKey = false
        var hasPasswordInput = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            if (id.contains("pin_pad") || id.contains("pinpad")) {
                hasPinPadMarker = true
            }
            if (id in pinKeys) {
                hasNumericPinKey = true
            }
            val cls = node.className?.toString().orEmpty()
            if (node.isPassword && isPasswordInputClass(cls)) {
                hasPasswordInput = true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        if (hasPinPadMarker && (hasNumericPinKey || hasPasswordInput)) return true
        return listOf(hasPinPadMarker, hasNumericPinKey, hasPasswordInput).count { it } >= 2
    }

    private fun isPasswordInputClass(className: String): Boolean =
        className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInputEditText", ignoreCase = true)

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
                (bounds.left != focus.left || bounds.top != focus.top ||
                    bounds.right != focus.right || bounds.bottom != focus.bottom)
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
