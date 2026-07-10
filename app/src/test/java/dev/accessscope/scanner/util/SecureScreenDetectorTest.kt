package dev.accessscope.scanner.util

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.data.ScreenProtectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class SecureScreenDetectorTest {

    @Test
    fun titleProtection_impostazioni_isNone() {
        assertEquals(ScreenProtectionReason.NONE, SecureScreenDetector.titleProtectionReason("Impostazioni"))
    }

    @Test
    fun titleProtection_inserisciPin_isPinOrPassword() {
        assertEquals(
            ScreenProtectionReason.PIN_OR_PASSWORD,
            SecureScreenDetector.titleProtectionReason("Inserisci PIN"),
        )
    }

    @Test
    fun titleProtection_codiceFiscale_isNone() {
        assertEquals(
            ScreenProtectionReason.NONE,
            SecureScreenDetector.titleProtectionReason("Codice fiscale"),
        )
    }

    @Test
    fun assess_flagSecure_blocksContrast() {
        val root = obtainRoot()
        val capture = ScreenshotCapture(bitmap = null, flagSecure = true)
        val assessment = SecureScreenDetector.assess(root, "Home", "com.example.app", capture)
        assertEquals(ScreenProtectionReason.FLAG_SECURE, assessment.reason)
        assertFalse(assessment.allowContrast)
        assertTrue(assessment.useSecureEvidence)
    }

    @Test
    fun assess_screenshotBlocked_allowsContrastButMarksProtected() {
        val root = obtainRoot()
        val capture = ScreenshotCapture(bitmap = null, screenshotBlocked = true)
        val assessment = SecureScreenDetector.assess(root, "Home", "com.example.app", capture)
        assertEquals(ScreenProtectionReason.SCREENSHOT_BLOCKED, assessment.reason)
        assertTrue(assessment.allowContrast)
        assertFalse(assessment.useSecureEvidence)
    }

    @Test
    fun assess_pinTitle_isPinOrPassword() {
        val root = obtainRoot()
        val assessment = SecureScreenDetector.assess(root, "Inserisci PIN", "com.example.app", null)
        assertEquals(ScreenProtectionReason.PIN_OR_PASSWORD, assessment.reason)
        assertFalse(assessment.allowContrast)
        assertTrue(assessment.useSecureEvidence)
    }

    @Test
    fun assess_settingsScreen_notProtected() {
        val root = obtainRoot()
        val assessment = SecureScreenDetector.assess(root, "Impostazioni", "com.example.app", null)
        assertEquals(ScreenProtectionReason.NONE, assessment.reason)
    }

    private fun obtainRoot(): AccessibilityNodeInfo {
        val root = AccessibilityNodeInfo.obtain()
        root.className = "android.widget.FrameLayout"
        return root
    }
}
