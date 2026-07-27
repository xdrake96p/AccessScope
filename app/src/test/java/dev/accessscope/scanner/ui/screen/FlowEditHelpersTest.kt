/**
 * Test helper editor step (duplica).
 */
package dev.accessscope.scanner.ui.screen

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica copia azione con timestamp nuovo.
 */
class FlowEditHelpersTest {

    @Test
    fun copyActionWithNewTimestamp_preservesFields() {
        val src = RecordedAction.Tap(
            packageName = "com.app",
            viewId = "com.app:id/btn",
            text = "OK",
            timestampMs = 100L,
        )
        val copy = copyActionWithNewTimestamp(src, now = 999L)
        assertTrue(copy is RecordedAction.Tap)
        val tap = copy as RecordedAction.Tap
        assertEquals("com.app:id/btn", tap.viewId)
        assertEquals("OK", tap.text)
        assertEquals(999L, tap.timestampMs)
        assertNotEquals(src.timestampMs, tap.timestampMs)
    }

    @Test
    fun copyActionWithNewTimestamp_inputText() {
        val src = RecordedAction.InputText(
            "com.app",
            "121212",
            viewId = "com.app:id/pincode",
            timestampMs = 1L,
        )
        val copy = copyActionWithNewTimestamp(src, now = 50L) as RecordedAction.InputText
        assertEquals("121212", copy.text)
        assertEquals(50L, copy.timestampMs)
    }
}
