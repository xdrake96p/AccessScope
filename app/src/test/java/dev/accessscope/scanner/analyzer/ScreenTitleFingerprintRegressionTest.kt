package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ScreenTitleFingerprintRegressionTest {

    @Test
    fun fingerprintTitle_returnsDisplayTitle() {
        val root = obtainRoot()
        assertEquals("AZIENDA 1", ScreenFingerprint.fingerprintTitle(root, "AZIENDA 1"))
    }

    @Test
    fun tabLabel_inChromeSeparatesFingerprint() {
        val baseTitle = "SEZIONE"
        val fpA = ScreenFingerprint.formatForTest(
            "com.example.app",
            baseTitle,
            listOf("topbar_title", "tv_tab", "tab:distinte (3)"),
        )
        val fpB = ScreenFingerprint.formatForTest(
            "com.example.app",
            baseTitle,
            listOf("topbar_title", "tv_tab", "tab:bonifici (2)"),
        )
        assertNotEquals(fpA, fpB)
    }

    @Test
    fun eventText_ignoredWhenStrongerCandidatePresent() {
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolver.titleCandidateForTest("Home", 74, "section_title"),
                ScreenTitleResolver.titleCandidateForTest("Transiente evento", 55, "event_text"),
            ),
            emptySet(),
        )
        assertEquals("Home", chosen)
    }

    @Test
    fun contentChanged_neverUsesSourceNodeClassNameAsTitle() {
        // Regressione: su TYPE_WINDOW_CONTENT_CHANGED `event.className` è il nodo sorgente
        // cambiato (qualunque ViewGroup/RecyclerView durante lo scroll), non l'Activity.
        // Usarlo come titolo fa cambiare fingerprint ad ogni scroll e frammenta il report
        // in tante "schermate" fasulle — vedi PROJECT.md 31 luglio 2026.
        val root = obtainRoot()
        val eventA = contentChangedEvent("android.view.ViewGroup")
        val eventB = contentChangedEvent("androidx.recyclerview.widget.RecyclerView")

        val titleA = ScreenTitleResolver.resolve(root, eventA)
        val titleB = ScreenTitleResolver.resolve(root, eventB)

        assertEquals("Stesso layout radice: il titolo non deve dipendere da quale nodo scatena l'evento", titleA, titleB)
        assertNotEquals("Viewgroup", titleA)
        assertNotEquals("Recyclerview", titleA)
    }

    private fun contentChangedEvent(sourceClassName: String): AccessibilityEvent {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.className = sourceClassName
        return event
    }

    private fun obtainRoot(): AccessibilityNodeInfo {
        val root = AccessibilityNodeInfo.obtain()
        root.className = "android.widget.FrameLayout"
        root.setVisibleToUser(true)
        root.setBoundsInScreen(Rect(0, 0, 1080, 2400))
        return root
    }
}
