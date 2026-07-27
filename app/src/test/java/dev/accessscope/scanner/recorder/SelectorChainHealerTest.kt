package dev.accessscope.scanner.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SelectorChainHealerTest {

    @Test
    fun promoteNext_usesSecondCandidate() {
        val tap = RecordedAction.Tap(
            "com.app",
            viewId = "com.app:id/header",
            text = "Sezione",
            selectorChain = listOf(
                dev.accessscope.scanner.recorder.model.SelectorCandidate(viewId = "com.app:id/header"),
                dev.accessscope.scanner.recorder.model.SelectorCandidate(text = "Sezione"),
            ),
        )
        val promoted = SelectorChainHealer.promoteNext(tap)
        assertNotNull(promoted)
        assertEquals("Sezione", promoted!!.text)
        assertNull(promoted.viewId)
    }

    @Test
    fun parseStepIndex() {
        assertEquals(2, SelectorChainHealer.parseStepIndex("Step 3: Tap non trovato"))
        assertNull(SelectorChainHealer.parseStepIndex("errore generico"))
    }
}
