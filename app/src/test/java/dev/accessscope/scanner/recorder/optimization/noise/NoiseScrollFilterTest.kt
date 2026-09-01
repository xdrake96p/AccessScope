package dev.accessscope.scanner.recorder.optimization.noise

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseScrollFilterTest {

    @Test
    fun isIntentionalScrollRun_detectsAdjacentPair() {
        val actions = listOf(
            RecordedAction.Tap("com.app", text = "X"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
        )
        assertTrue(NoiseActionFilter.isIntentionalScrollRun(actions, 1))
        assertTrue(NoiseActionFilter.isIntentionalScrollRun(actions, 2))
    }

    @Test
    fun dropNoiseScrolls_keepsPairAfterTap() {
        val actions = listOf(
            RecordedAction.Tap("com.app", text = "NON ORA", timestampMs = 1_000L),
            RecordedAction.Scroll("com.app", timestampMs = 1_200L),
            RecordedAction.Scroll("com.app", timestampMs = 1_400L),
            RecordedAction.Tap(
                "com.app",
                viewId = "com.app:id/policy_row",
                text = "404347818",
                timestampMs = 2_000L,
            ),
        )
        val result = NoiseActionFilter.dropNoiseScrolls(actions)
        assertEquals(2, result.count { it is RecordedAction.Scroll })
    }
}
