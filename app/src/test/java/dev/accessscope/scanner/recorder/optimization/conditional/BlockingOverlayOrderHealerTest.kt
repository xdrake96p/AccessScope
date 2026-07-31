/**
 * Test: riordino dismiss alert dopo CONTINUA prima degli input PIN.
 */
package dev.accessscope.scanner.recorder.optimization.conditional

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingOverlayOrderHealerTest {

    @Test
    fun movesOkHoCapitoBeforePinInputs() {
        val pkg = "it.nexi.bff"
        val raw = listOf(
            RecordedAction.LaunchApp(pkg),
            RecordedAction.Tap(pkg, text = "CONTINUA"),
            RecordedAction.WaitForAnimation(pkg, timeoutMs = 1000L),
            RecordedAction.InputText(pkg, text = "1", viewId = "$pkg:id/edit1"),
            RecordedAction.InputText(pkg, text = "2", viewId = "$pkg:id/edit2"),
            RecordedAction.Tap(
                pkg,
                viewId = "$pkg:id/dismiss",
                text = "OK, HO CAPITO",
                executionMode = StepExecutionMode.Optional,
            ),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/uno", text = "1"),
        )
        val out = BlockingOverlayOrderHealer.reorder(raw)
        val types = out.map {
            when (it) {
                is RecordedAction.Tap -> "Tap:${it.text ?: it.viewId?.substringAfterLast('/')}"
                is RecordedAction.InputText -> "Input:${it.viewId?.substringAfterLast('/')}"
                is RecordedAction.WaitForAnimation -> "Anim"
                is RecordedAction.LaunchApp -> "Launch"
                else -> it::class.simpleName
            }
        }
        val continua = types.indexOf("Tap:CONTINUA")
        val dismiss = types.indexOf("Tap:OK, HO CAPITO")
        val edit1 = types.indexOf("Input:edit1")
        assertTrue(continua >= 0 && dismiss >= 0 && edit1 >= 0)
        assertTrue("dismiss deve stare dopo CONTINUA", dismiss > continua)
        assertTrue("dismiss deve stare prima di edit1", dismiss < edit1)
    }

    @Test
    fun leavesCorrectOrderUntouched() {
        val pkg = "it.nexi.bff"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "CONTINUA"),
            RecordedAction.Tap(
                pkg,
                text = "OK, HO CAPITO",
                executionMode = StepExecutionMode.Optional,
            ),
            RecordedAction.InputText(pkg, text = "1", viewId = "$pkg:id/edit1"),
        )
        val out = BlockingOverlayOrderHealer.reorder(raw)
        assertEquals(3, out.size)
        assertEquals("OK, HO CAPITO", (out[1] as RecordedAction.Tap).text)
    }
}
