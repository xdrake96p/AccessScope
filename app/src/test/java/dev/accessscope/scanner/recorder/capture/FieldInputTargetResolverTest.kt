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
}
