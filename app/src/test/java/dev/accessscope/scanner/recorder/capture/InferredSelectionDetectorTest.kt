package dev.accessscope.scanner.recorder.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Inferenza selezione da confronto stato schermo — logica pura, senza `AccessibilityNodeInfo`.
 *
 * Fixture dal bug reale su it.nexi.bff/Banca MPS: il tocco su una riga della rubrica non genera
 * `TYPE_VIEW_CLICKED` (riga `clickable="true"` ma con touch handler grezzo) e il campo viene
 * popolato via `setText()` senza `TYPE_VIEW_TEXT_CHANGED` — il passaggio spariva dal flusso.
 */
class InferredSelectionDetectorTest {

    private val rubricaTexts = setOf(
        "RUBRICA",
        "Seleziona beneficiario",
        "Fornitore Demo Srl",
        "IT20A0000000000000000000000",
        "Cliente Beta Spa",
        "IT60X0542811101000000123456",
    )

    @Test
    fun fieldFilledWithPreviouslyVisibleText_isInferredSelection() {
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/edt_ragione_sociale" to "Fornitore Demo Srl"),
            recentVisibleTexts = rubricaTexts,
        )
        assertEquals("Fornitore Demo Srl", inferred?.matchedVisibleText)
        assertEquals("it.nexi.bff:id/edt_ragione_sociale", inferred?.fieldViewId)
    }

    @Test
    fun ibanFieldFilledFromList_isInferredSelection() {
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/edt_iban" to "IT60X0542811101000000123456"),
            recentVisibleTexts = rubricaTexts,
        )
        assertEquals("IT60X0542811101000000123456", inferred?.matchedVisibleText)
    }

    @Test
    fun ibanFormattedDifferentlyInListAndField_stillMatches() {
        // La lista mostra l'IBAN a gruppi, il campo lo compatta: il confronto normalizza spazi.
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/edt_iban" to "IT60X0542811101000000123456"),
            recentVisibleTexts = setOf("IT60 X054 2811 1010 0000 0123 456"),
        )
        assertEquals("IT60 X054 2811 1010 0000 0123 456", inferred?.matchedVisibleText)
    }

    @Test
    fun unchangedField_isNotSelection() {
        val before = mapOf("it.nexi.bff:id/edt_ragione_sociale" to "Fornitore Demo Srl")
        val inferred = InferredSelectionDetector.inferSelection(
            before = before,
            after = before,
            recentVisibleTexts = rubricaTexts,
        )
        assertNull(inferred)
    }

    @Test
    fun typedValueNeverSeenOnScreen_isNotSelection() {
        // Digitare "Mario Rossi" a mano cambia il campo, ma quel testo non era una voce di lista:
        // niente prova strutturale, nessuno step sintetico (resta coperto da InputText).
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/edt_ragione_sociale" to "Mario Rossi"),
            recentVisibleTexts = rubricaTexts,
        )
        assertNull(inferred)
    }

    @Test
    fun typedFieldIsIgnoredEvenIfValueMatchesVisibleText() {
        // Guardia anti-duplicato: se l'utente ha digitato in quel campo, lo step esiste già.
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/edt_ragione_sociale" to "Fornitore Demo Srl"),
            recentVisibleTexts = rubricaTexts,
            ignoredFieldIds = setOf("it.nexi.bff:id/edt_ragione_sociale"),
        )
        assertNull(inferred)
    }

    @Test
    fun shortValue_isNotSelection() {
        // Valori corti (es. un importo "100") sono troppo ambigui per essere prova di selezione.
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/importo_currency" to "100"),
            recentVisibleTexts = rubricaTexts + "100",
        )
        assertNull(inferred)
    }

    @Test
    fun noRecentTexts_isNotSelection() {
        val inferred = InferredSelectionDetector.inferSelection(
            before = emptyMap(),
            after = mapOf("it.nexi.bff:id/edt_ragione_sociale" to "Fornitore Demo Srl"),
            recentVisibleTexts = emptySet(),
        )
        assertNull(inferred)
    }
}
