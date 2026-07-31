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
    fun structuralIdWithoutFallback_isBlockingError() {
        // Rafforzamento del gate: un id strutturale come UNICO riferimento (nessun testo/cd,
        // nessuna alternativa nella chain) non garantisce zero-edit — deve bloccare il save,
        // non restare un semplice warning che pubblica comunque un YAML fragile.
        val actions = listOf(
            RecordedAction.LaunchApp("com.demo"),
            RecordedAction.Tap(
                packageName = "com.demo",
                viewId = "com.demo:id/drawer_layout",
            ),
        )
        val report = ZeroEditGate.evaluate(actions, healFirst = true)
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any {
                it.stepIndex == 1 && it.code == "STRUCTURAL_SELECTOR" && it.severity == ZeroEditSeverity.Error
            },
        )
    }

    @Test
    fun structuralIdWithFallbackText_staysWarningOnly() {
        // Se esiste un testo di fallback, il warning strutturale resta informativo:
        // il selettore esportato userà comunque il testo, non l'id fragile.
        val actions = listOf(
            RecordedAction.LaunchApp("com.demo"),
            RecordedAction.Tap(
                packageName = "com.demo",
                viewId = "com.demo:id/drawer_layout",
                text = "Apri menu",
            ),
        )
        val report = ZeroEditGate.evaluate(actions, healFirst = true)
        assertFalse(
            report.issues.any {
                it.stepIndex == 1 && it.code == "STRUCTURAL_SELECTOR" && it.severity == ZeroEditSeverity.Error
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
