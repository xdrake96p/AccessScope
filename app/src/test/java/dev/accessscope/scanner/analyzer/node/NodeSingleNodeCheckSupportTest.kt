package dev.accessscope.scanner.analyzer.node

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSingleNodeCheckSupportTest {

    @Test
    fun looksLikeVisualErrorText_recognizesItalianKeywords() {
        // Prima il rilevamento matchava solo "error" in inglese, ironico per un'app italiana:
        // non intercettava mai messaggi come "Errore: campo obbligatorio".
        assertTrue(NodeSingleNodeCheckSupport.looksLikeVisualErrorText("Errore: formato non valido"))
        assertTrue(NodeSingleNodeCheckSupport.looksLikeVisualErrorText("Campo obbligatorio"))
        assertTrue(NodeSingleNodeCheckSupport.looksLikeVisualErrorText("Error: invalid input"))
    }

    @Test
    fun looksLikeVisualErrorText_ignoresUnrelatedText() {
        assertFalse(NodeSingleNodeCheckSupport.looksLikeVisualErrorText("Mario Rossi"))
    }
}
