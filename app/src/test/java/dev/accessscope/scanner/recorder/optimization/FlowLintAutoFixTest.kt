package dev.accessscope.scanner.recorder.optimization.lint

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowLintAutoFixTest {

    @Test
    fun insertsWaitAfterSubmitWithoutWait() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.LaunchApp(pkg),
            RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 1_000L),
            RecordedAction.Tap(pkg, text = "Home", timestampMs = 2_000L),
        )
        val fixed = FlowLintAutoFix.apply(raw, pkg)
        assertTrue(fixed.any { it is RecordedAction.WaitForAnimation })
        assertTrue(fixed.any { it is RecordedAction.Wait })
    }

    @Test
    fun enrichesBlindLongWait() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Wait(pkg, timeoutMs = 10_000L, timestampMs = 1_000L),
            RecordedAction.Tap(pkg, text = "Profilo", timestampMs = 2_000L),
        )
        val fixed = FlowLintAutoFix.apply(raw, pkg)
        val wait = fixed.filterIsInstance<RecordedAction.Wait>().first()
        assertTrue(!wait.visibleText.isNullOrBlank() || !wait.visibleId.isNullOrBlank())
    }
}
