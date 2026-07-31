package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.analyzer.title.TitleCandidateLogic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ScreenTitleResolverTest {

    @Test
    fun pickBestTitle_prefersHigherWeight() {
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("I miei documenti", 72, "toolbar"),
                ScreenTitleResolverTestHelper.candidate("Home", 100, "pin"),
            ),
            emptySet(),
        )
        assertEquals("Home", chosen)
    }

    @Test
    fun toolbarWinsWhenAlone() {
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("I miei documenti", 72, "toolbar"),
            ),
            emptySet(),
        )
        assertEquals("I miei documenti", chosen)
    }

    @Test
    fun pickBestTitle_prefersStrongerSourceOnEqualWeight() {
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("Main", 64, "activity"),
                ScreenTitleResolverTestHelper.candidate("Home", 64, "section_title"),
            ),
            emptySet(),
        )
        assertEquals("Home", chosen)
    }

    @Test
    fun pickBestTitle_ignoresGenericActionTitles() {
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("Indietro", 90, "pane_title"),
                ScreenTitleResolverTestHelper.candidate("Sezione", 76, "section_title"),
            ),
            emptySet(),
        )
        assertEquals("Sezione", chosen)
    }

    @Test
    fun isAndroidFrameworkViewClassName_rejectsGenericContainers() {
        // Bug: su TYPE_WINDOW_CONTENT_CHANGED `event.className` è il nodo sorgente cambiato
        // (es. un ViewGroup/RecyclerView qualunque), non l'Activity — usarlo come titolo
        // frammenta il report in tante "schermate" fasulle a ogni scroll.
        assertTrue(TitleCandidateLogic.isAndroidFrameworkViewClassName("ViewGroup"))
        assertTrue(TitleCandidateLogic.isAndroidFrameworkViewClassName("RecyclerView"))
        assertTrue(TitleCandidateLogic.isAndroidFrameworkViewClassName("FrameLayout"))
        assertFalse(TitleCandidateLogic.isAndroidFrameworkViewClassName("PaymentDetailActivity"))
        assertFalse(TitleCandidateLogic.isAndroidFrameworkViewClassName("HomeFragment"))
    }
}

/** Espone [ScreenTitleResolver.TitleCandidate] ai test nello stesso package. */
private object ScreenTitleResolverTestHelper {
    fun candidate(title: String, weight: Int, source: String) =
        ScreenTitleResolver.titleCandidateForTest(title, weight, source)
}
