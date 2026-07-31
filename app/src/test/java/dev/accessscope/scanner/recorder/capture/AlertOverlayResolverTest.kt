/**
 * Test JVM per [AlertOverlayResolver] (dismiss alert_pop / OK HO CAPITO).
 */
package dev.accessscope.scanner.recorder.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertOverlayResolverTest {

    @Test
    fun toOptionalTap_usesIdAndTextChain() {
        val tap = AlertOverlayResolver.toOptionalTap(
            packageName = "it.nexi.bff",
            target = AlertDismissTarget(
                viewId = "it.nexi.bff:id/dismiss",
                text = "OK, HO CAPITO",
                title = "Attenzione!",
            ),
            timestampMs = 1L,
        )
        assertEquals("it.nexi.bff:id/dismiss", tap.viewId)
        assertEquals("OK, HO CAPITO", tap.text)
        assertTrue(tap.selectorChain.any { it.viewId?.endsWith("/dismiss") == true })
        assertTrue(tap.selectorChain.any { it.text == "OK, HO CAPITO" })
        assertEquals(
            dev.accessscope.scanner.recorder.model.StepExecutionMode.Optional,
            tap.executionMode,
        )
    }
}
