package dev.accessscope.scanner.recorder.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldInputTargetResolverTest {

    @Test
    fun looksLikeFieldLabel_requiredHint() {
        assertTrue(FieldInputTargetResolver.looksLikeFieldLabel("Inserisci dati beneficiario (obbligatorio)"))
        assertTrue(FieldInputTargetResolver.looksLikeFieldLabel("Email address (required)"))
    }

    @Test
    fun looksLikeFieldLabel_notShortButton() {
        assertFalse(FieldInputTargetResolver.looksLikeFieldLabel("OK"))
        assertFalse(FieldInputTargetResolver.looksLikeFieldLabel("Pay"))
    }

    @Test
    fun isSelectionPickerTitle_detectsRubricaAndIban() {
        assertTrue(FieldInputTargetResolver.isSelectionPickerTitle("RUBRICA"))
        assertTrue(FieldInputTargetResolver.isSelectionPickerTitle("SELEZIONA IBAN"))
        assertTrue(FieldInputTargetResolver.isSelectionPickerTitle("Beneficiario"))
        assertFalse(FieldInputTargetResolver.isSelectionPickerTitle("Torna indietro"))
    }

    @Test
    fun isSelectionPickerTitle_ignoresFieldHintsWithParentheses() {
        // Bug reale su it.nexi.bff/Banca MPS: l'etichetta del campo IBAN nel form BONIFICO SEPA
        // ("IBAN (obbligatorio)") veniva scambiata per il titolo dello sheet "SELEZIONA IBAN",
        // facendo credere al recorder che il picker fosse ancora aperto anche a form tornato
        // normale — bloccando per sempre la rilevazione della selezione.
        assertFalse(FieldInputTargetResolver.isSelectionPickerTitle("IBAN (obbligatorio)"))
        assertFalse(
            FieldInputTargetResolver.isSelectionPickerTitle(
                "Inserisci dati beneficiario (obbligatorio)",
            ),
        )
    }

    @Test
    fun looksLikePickerListItem_beneficiaryAndIban() {
        assertTrue(FieldInputTargetResolver.looksLikePickerListItem("Fornitore Demo Srl"))
        assertTrue(FieldInputTargetResolver.looksLikePickerListItem("IT20A0000000000000000000000"))
        assertFalse(FieldInputTargetResolver.looksLikePickerListItem("Inserisci dati beneficiario (obbligatorio)"))
        assertFalse(FieldInputTargetResolver.looksLikePickerListItem("chiudi"))
    }

    @Test
    fun isPickerBackedViewId_detectsBeneficiaryAndIban() {
        assertTrue(FieldInputTargetResolver.isPickerBackedViewId("it.nexi.bff:id/edt_beneficiary"))
        assertTrue(FieldInputTargetResolver.isPickerBackedViewId("it.nexi.bff:id/iban_field"))
        assertFalse(FieldInputTargetResolver.isPickerBackedViewId("it.nexi.bff:id/importo_currency"))
    }

    @Test
    fun isPickerListLabel_matchesTextOrContentDescription() {
        assertTrue(FieldInputTargetResolver.isPickerListLabel("Fornitore Demo Srl", null))
        assertTrue(FieldInputTargetResolver.isPickerListLabel(null, "IT20A0000000000000000000000"))
        assertFalse(
            FieldInputTargetResolver.isPickerListLabel(
                "Inserisci dati beneficiario (obbligatorio)",
                null,
            ),
        )
    }
}
