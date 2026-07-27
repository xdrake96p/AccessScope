package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordedTransition
import dev.accessscope.scanner.recorder.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitionTimingAnalyzerTest {

    @Test
    fun launchAnimationTimeout_clampsObserved() {
        val telemetry = FlowTelemetry(
            transitions = listOf(
                RecordedTransition(
                    fromIndex = 0,
                    toIndex = 1,
                    deltaMs = 3_000L,
                    fromFingerprint = "a",
                    toFingerprint = "b",
                    kind = TransitionKind.ScreenTransition,
                ),
            ),
        )
        val timeout = TransitionTimingAnalyzer.launchAnimationTimeoutMs(telemetry)
        assertEquals(3_900L, timeout)
    }

    @Test
    fun extendedWaitTimeout_notFixedTenSeconds() {
        val timeout = TransitionTimingAnalyzer.extendedWaitTimeoutMs(observedMs = 2_000L)
        assertEquals(3_000L, timeout)
    }

    @Test
    fun sameScreenShortWait_skipsLongWait() {
        assertNull(TransitionTimingAnalyzer.sameScreenShortWaitMs(800L))
        assertEquals(400L, TransitionTimingAnalyzer.sameScreenShortWaitMs(400L))
    }
}
