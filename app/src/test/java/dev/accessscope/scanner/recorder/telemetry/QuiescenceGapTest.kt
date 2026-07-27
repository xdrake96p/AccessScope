package dev.accessscope.scanner.recorder.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuiescenceGapTest {

    @Test
    fun buildsGapFromContentChanges() {
        val stamps = listOf(1_000L, 3_000L)
        val changes = listOf(1_200L, 1_500L, 2_200L)
        val gaps = RecordingTelemetry.buildQuiescenceGaps(stamps, changes)
        assertEquals(1, gaps.size)
        assertEquals(0, gaps[0].afterActionIndex)
        assertEquals(800L, gaps[0].quietMs) // 3000-2200
        assertTrue(gaps[0].contentBurstMs >= 1_000L)
        val wait = RecordingTelemetry.suggestedWaitMs(gaps[0])
        assertTrue(wait in 700L..8_000L)
    }
}
