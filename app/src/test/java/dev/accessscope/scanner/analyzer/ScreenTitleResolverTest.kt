package dev.accessscope.scanner.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ScreenTitleResolverTest {

    @Test
    fun myAxaLanding_notDocumentToolbar() {
        val ids = setOf("titlehello", "buttonaltro", "policyname", "policynumber", "productslist")
        assertEquals("Home", ScreenTitleResolver.inferTitleFromContentMarkers(ids))
        assertFalse(
            ScreenTitleResolver.isToolbarConsistentWithContent("I miei documenti", ids),
        )
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("I miei documenti", 72, "toolbar"),
                ScreenTitleResolverTestHelper.candidate("Home", 100, "content_markers"),
            ),
            ids,
        )
        assertEquals("Home", chosen)
    }

    @Test
    fun nexiHome_infersHome() {
        val ids = setOf("card_home", "entrate_home", "rotate_display", "scrollview_port")
        assertEquals("Home", ScreenTitleResolver.inferTitleFromContentMarkers(ids))
    }

    @Test
    fun nexiRubrica_infersRubrica() {
        val ids = setOf("labelcontacts", "iban_account", "edt_ragione_sociale")
        assertEquals("RUBRICA", ScreenTitleResolver.inferTitleFromContentMarkers(ids))
    }

    @Test
    fun myAxaDocuments_infersDocumentSection() {
        val ids = setOf("name", "numero", "recycler_documents", "documentcard")
        assertEquals("I miei documenti", ScreenTitleResolver.inferTitleFromContentMarkers(ids))
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("Home", 72, "toolbar"),
                ScreenTitleResolverTestHelper.candidate("I miei documenti", 100, "content_markers"),
            ),
            ids,
        )
        assertEquals("I miei documenti", chosen)
    }

    @Test
    fun toolbarWinsWhenNoContentConflict() {
        val ids = setOf("name", "numero", "recycler_documents")
        assertEquals("I miei documenti", ScreenTitleResolver.inferTitleFromContentMarkers(ids))
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("I miei documenti", 72, "toolbar"),
            ),
            ids,
        )
        assertEquals("I miei documenti", chosen)
    }

    @Test
    fun pickBestTitle_prefersStrongerSourceOnEqualWeight() {
        val ids = setOf("card_home", "scrollview_port")
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("Main", 64, "activity"),
                ScreenTitleResolverTestHelper.candidate("Home", 64, "distinctive_ids"),
            ),
            ids,
        )
        assertEquals("Home", chosen)
    }

    @Test
    fun pickBestTitle_ignoresGenericActionTitles() {
        val chosen = ScreenTitleResolver.pickBestTitle(
            listOf(
                ScreenTitleResolverTestHelper.candidate("Indietro", 90, "pane_title"),
                ScreenTitleResolverTestHelper.candidate("RUBRICA", 76, "nexi_text"),
            ),
            emptySet(),
        )
        assertEquals("RUBRICA", chosen)
    }

    @Test
    fun cacheRejectedWhenContentChangesToRubrica() {
        val homeIds = setOf("card_home", "entrate_home")
        val rubricaIds = setOf("labelcontacts", "iban_account")
        assertTrue(ScreenTitleResolver.inferTitleFromContentMarkers(homeIds) != null)
        assertEquals("RUBRICA", ScreenTitleResolver.inferTitleFromContentMarkers(rubricaIds))
    }
}

/** Espone [ScreenTitleResolver.TitleCandidate] ai test nello stesso package. */
private object ScreenTitleResolverTestHelper {
    fun candidate(title: String, weight: Int, source: String) =
        ScreenTitleResolver.titleCandidateForTest(title, weight, source)
}
