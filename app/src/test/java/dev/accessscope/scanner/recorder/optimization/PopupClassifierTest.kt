package dev.accessscope.scanner.recorder.optimization.conditional

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.recorder.model.TransitionKind
import dev.accessscope.scanner.recorder.model.RecordedTransition
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupClassifierTest {

    @Test
    fun dismissTextOnOverlay_isOptional() {
        val telemetry = FlowTelemetry(
            transitions = listOf(
                RecordedTransition(
                    fromIndex = 0,
                    toIndex = 1,
                    deltaMs = 500L,
                    fromFingerprint = "com.app::home",
                    toFingerprint = "com.app::overlay",
                    kind = TransitionKind.PossibleOverlay,
                ),
            ),
        )
        val tap = RecordedAction.Tap("com.app", text = "Non ora")
        val meta = PopupClassifier.classifyTap(tap, 1, telemetry, ScanIntelligenceBundle(mainPathFingerprints = listOf("com.app::home")))
        assertEquals(StepExecutionMode.Optional, meta.executionMode)
    }

    @Test
    fun nonOraWithoutTelemetry_isOptional() {
        val tap = RecordedAction.Tap("com.app", text = "Non ora")
        val meta = PopupClassifier.classifyTap(tap, 0, null, null)
        assertEquals(StepExecutionMode.Optional, meta.executionMode)
    }
}
