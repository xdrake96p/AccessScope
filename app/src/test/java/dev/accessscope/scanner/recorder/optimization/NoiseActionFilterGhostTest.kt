package dev.accessscope.scanner.recorder.optimization.noise

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseActionFilterGhostTest {

    @Test
    fun dropsPointOnlyTapAfterScroll() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Scroll(pkg, timestampMs = 1_000L),
            RecordedAction.Tap(
                pkg,
                pointPercentX = 50f,
                pointPercentY = 50f,
                timestampMs = 1_100L,
            ),
            RecordedAction.Tap(pkg, text = "OK", timestampMs = 2_000L),
        )
        val out = NoiseActionFilter.dropGhostTapsAfterScrollOrIme(raw)
        assertEquals(2, out.size)
        assertTrue(out.last() is RecordedAction.Tap && (out.last() as RecordedAction.Tap).text == "OK")
    }

    @Test
    fun dropsDuplicateSectionTapsAcrossWaits() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 1_000L),
            RecordedAction.WaitForAnimation(pkg, timeoutMs = 500L, timestampMs = 1_100L),
            RecordedAction.Wait(pkg, timeoutMs = 2_000L, timestampMs = 1_200L),
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 1_500L),
        )
        val out = NoiseActionFilter.dropDuplicateTapsAcrossWaits(raw)
        assertEquals(3, out.size)
        assertEquals(1, out.count { it is RecordedAction.Tap })
    }
}
