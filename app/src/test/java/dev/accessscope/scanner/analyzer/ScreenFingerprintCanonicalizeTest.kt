package dev.accessscope.scanner.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [ScreenFingerprint.canonicalize] è logica pura su stringhe (nessun [android.view.accessibility.AccessibilityNodeInfo]),
 * quindi testabile senza Robolectric.
 */
class ScreenFingerprintCanonicalizeTest {

    @Test
    fun transientChromeAppearing_mergesIntoKnownFingerprint() {
        // Regressione: la stessa "Home" produceva 3 fingerprint diversi nella stessa sessione,
        // a seconda di quali elementi di chrome (es. collapsing toolbar) erano presenti al
        // momento esatto della cattura.
        val known = setOf("com.example.app::Home::topbar_toolbar")
        val candidateWithoutToolbar = ScreenFingerprint.formatForTest("com.example.app", "Home", emptyList())

        val canonical = ScreenFingerprint.canonicalize(candidateWithoutToolbar, known)

        assertEquals(known.first(), canonical)
    }

    @Test
    fun transientChromeDisappearing_mergesIntoKnownFingerprint() {
        val known = setOf(ScreenFingerprint.formatForTest("com.example.app", "Home", emptyList()))
        val candidateWithToolbar = "com.example.app::Home::topbar_toolbar"

        val canonical = ScreenFingerprint.canonicalize(candidateWithToolbar, known)

        assertEquals(known.first(), canonical)
    }

    @Test
    fun differentTabLabel_neverMerges() {
        // Un tab diverso è un cambio di contenuto reale, non chrome transitorio.
        val known = setOf(
            ScreenFingerprint.formatForTest("com.example.app", "Sezione", listOf("tv_tab", "tab:bonifici")),
        )
        val candidate = ScreenFingerprint.formatForTest("com.example.app", "Sezione", listOf("tv_tab", "tab:distinte"))

        val canonical = ScreenFingerprint.canonicalize(candidate, known)

        assertEquals(candidate, canonical)
        assertNotEquals(known.first(), canonical)
    }

    @Test
    fun differentTitle_neverMerges() {
        val known = setOf(ScreenFingerprint.formatForTest("com.example.app", "Home", emptyList()))
        val candidate = ScreenFingerprint.formatForTest("com.example.app", "Impostazioni", emptyList())

        assertEquals(candidate, ScreenFingerprint.canonicalize(candidate, known))
    }

    @Test
    fun partialCaptureSubsetOfLargerChrome_merges() {
        // Regressione reale (it.nexi.bff/MPS): "REGISTRA NUOVA UTENZA" catturata con 5, poi 3,
        // poi 0 elementi di chrome nella stessa sessione — ogni insieme più piccolo è un
        // sottoinsieme proprio del precedente (mai un chrome diverso), ma la vecchia regola
        // (differenza massima di 1 elemento) univa solo la coppia adiacente più vicina,
        // lasciando comunque 3 fingerprint invece di 1.
        val known = setOf(
            ScreenFingerprint.formatForTest(
                "com.example.app",
                "Home",
                listOf("action_bar_root", "toolbar", "topbar_icon_left", "topbar_icon_right", "topbar_title"),
            ),
        )
        val threeElements = ScreenFingerprint.formatForTest(
            "com.example.app",
            "Home",
            listOf("toolbar", "topbar_icon_right", "topbar_title"),
        )
        val noElements = ScreenFingerprint.formatForTest("com.example.app", "Home", emptyList())

        assertEquals(known.first(), ScreenFingerprint.canonicalize(threeElements, known))
        assertEquals(known.first(), ScreenFingerprint.canonicalize(noElements, known))
    }

    @Test
    fun disjointChromeSets_neverMerge() {
        // Caso reale (RUBRICA su it.nexi.bff/MPS): due catture con chrome completamente
        // diverso, senza relazione di sottoinsieme — restano schermate distinte piuttosto che
        // rischiare di unire contenuti davvero diversi.
        val known = setOf(
            ScreenFingerprint.formatForTest("com.example.app", "Rubrica", listOf("topbar_search_contact")),
        )
        val candidate = ScreenFingerprint.formatForTest("com.example.app", "Rubrica", listOf("toolbar"))

        assertEquals(candidate, ScreenFingerprint.canonicalize(candidate, known))
    }

    @Test
    fun exactMatch_returnsSameFingerprint() {
        val fp = ScreenFingerprint.formatForTest("com.example.app", "Home", listOf("topbar_toolbar"))
        assertEquals(fp, ScreenFingerprint.canonicalize(fp, setOf(fp)))
    }

    @Test
    fun emptyKnownSet_returnsCandidateUnchanged() {
        val candidate = ScreenFingerprint.formatForTest("com.example.app", "Home", emptyList())
        assertEquals(candidate, ScreenFingerprint.canonicalize(candidate, emptySet()))
    }
}
