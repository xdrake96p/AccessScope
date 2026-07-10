package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ScreenTitleFingerprintRegressionTest {

    @Test
    fun homeFingerprint_stableAcrossScrollContentIds() {
        val scrollA = setOf("card_home", "entrate_home", "rotate_display", "scrollview_port", "row_1")
        val scrollB = setOf("card_home", "entrate_home", "rotate_display", "scrollview_port", "row_99")
        assertEquals(
            ScreenTitleResolver.inferTitleFromContentMarkers(scrollA),
            ScreenTitleResolver.inferTitleFromContentMarkers(scrollB),
        )
        assertEquals("Home", ScreenTitleResolver.inferTitleFromContentMarkers(scrollA))
    }

    @Test
    fun fingerprintTitle_prefersContentMarkersWhenRootHasHomeIds() {
        val ids = setOf("card_home", "entrate_home", "scrollview_port")
        assertEquals("Home", ScreenTitleResolver.inferTitleFromContentMarkers(ids))
    }

    @Test
    fun fingerprintTitle_fallsBackToDisplayWhenNoMarkers() {
        val root = obtainRoot()
        assertEquals("AZIENDA 1", ScreenFingerprint.fingerprintTitle(root, "AZIENDA 1"))
    }

    @Test
    fun distinteMarkers_contentTitleIsHomeOrDistinctive() {
        val homeOnly = setOf("card_home", "scrollview_port")
        assertEquals("Home", ScreenTitleResolver.inferTitleFromContentMarkers(homeOnly))
        val distinte = setOf("recycler_distinte", "vop_info", "topbar_title")
        assertNull(ScreenTitleResolver.inferTitleFromContentMarkers(distinte))
    }

    @Test
    fun insolutiWidgetOnHome_notUsedAsFingerprintTitle() {
        val homeWithInsoluti = setOf("card_home", "entrate_home", "insoluti_title", "see_all_insolved")
        assertEquals("Home", ScreenTitleResolver.inferTitleFromContentMarkers(homeWithInsoluti))
    }

    @Test
    fun tabLabel_inChromeSeparatesFingerprint() {
        val baseTitle = "AUTORIZZA DISTINTE"
        val fpDistinte = ScreenFingerprint.formatForTest(
            "it.nexi.bff",
            baseTitle,
            listOf("topbar_title", "tv_tab", "tab:distinte (3)"),
        )
        val fpBonifici = ScreenFingerprint.formatForTest(
            "it.nexi.bff",
            baseTitle,
            listOf("topbar_title", "tv_tab", "tab:bonifici (2)"),
        )
        assertNotEquals(fpDistinte, fpBonifici)
    }

    @Test
    fun eventText_ignoredWhenContentMarkersPresent() {
        val ids = setOf("card_home", "entrate_home", "scrollview_port")
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolver.titleCandidateForTest("Home", 100, "content_markers"),
                ScreenTitleResolver.titleCandidateForTest("Transiente evento", 55, "event_text"),
            ),
            ids,
        )
        assertEquals("Home", chosen)
    }

    private fun obtainRoot(): AccessibilityNodeInfo {
        val root = AccessibilityNodeInfo.obtain()
        root.className = "android.widget.FrameLayout"
        root.setVisibleToUser(true)
        root.setBoundsInScreen(Rect(0, 0, 1080, 2400))
        return root
    }
}
