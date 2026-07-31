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
    fun tooManyChromeDifferences_treatedAsGenuinelyDifferentScreen() {
        // Più di una differenza di chrome non è più "transitorio", è un layout diverso.
        val known = setOf(
            ScreenFingerprint.formatForTest("com.example.app", "Home", listOf("topbar_toolbar", "bottom_nav")),
        )
        val candidate = ScreenFingerprint.formatForTest("com.example.app", "Home", emptyList())

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
