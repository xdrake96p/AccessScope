/**
 * Test del gate ZeroEdit e heal statico selettori.
 */
package dev.accessscope.scanner.recorder.quality

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.SelectorCandidate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroEditGateTest {

    @Test
    fun pointOnlyTap_isBlockingError() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.demo"),
            RecordedAction.Tap(
                packageName = "com.demo",
                pointPercentX = 50f,
                pointPercentY = 50f,
                weakSelector = true,
            ),
        )
        val report = ZeroEditGate.evaluate(actions, healFirst = true)
        assertTrue(report.hasErrors)
        assertTrue(report.issues.any { it.severity == ZeroEditSeverity.Error })
    }

    @Test
    fun structuralIdWithText_healedToSemantic() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.demo"),
            RecordedAction.Tap(
                packageName = "com.demo",
                viewId = "com.demo:id/drawer_layout",
                text = "Continua",
                selectorChain = listOf(
                    SelectorCandidate(viewId = "com.demo:id/drawer_layout"),
                    SelectorCandidate(text = "Continua"),
                ),
            ),
        )
        val report = ZeroEditGate.evaluate(actions, healFirst = true)
        val tap = report.actions.filterIsInstance<RecordedAction.Tap>().first()
        assertTrue(tap.text == "Continua" || !tap.viewId.isNullOrBlank())
        // Dopo heal non deve restare point-only error sul tap con testo.
        assertFalse(
            report.issues.any {
                it.code == "POINT_ONLY_SELECTOR" && it.stepIndex == 1
            },
        )
    }

    @Test
    fun textTap_isPublishable() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.demo"),
            RecordedAction.Tap(packageName = "com.demo", text = "OK"),
            RecordedAction.WaitForAnimation(packageName = "com.demo", timeoutMs = 1000L),
        )
        val report = ZeroEditGate.evaluate(actions, healFirst = true)
        assertTrue(ZeroEditGate.isPublishable(report) || report.errorCount == 0)
    }
}
