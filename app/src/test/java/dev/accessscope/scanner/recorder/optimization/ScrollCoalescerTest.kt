package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.ScrollDirection
import dev.accessscope.scanner.recorder.optimization.scroll.ScrollCoalescer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test di regressione per [ScrollCoalescer] (piano M1-A2).
 */
class ScrollCoalescerTest {

    private fun scroll(ts: Long, dir: ScrollDirection = ScrollDirection.DOWN) =
        RecordedAction.Scroll(packageName = "com.example.app", direction = dir, timestampMs = ts)

    @Test
    fun twoScrollsPlusTapWithId_becomeScrollUntilVisiblePlusTap() {
        val result = ScrollCoalescer.coalesce(
            listOf(
                scroll(1_000),
                scroll(1_600),
                RecordedAction.Tap(
                    packageName = "com.example.app",
                    viewId = "com.example.app:id/btn_pay",
                    timestampMs = 2_000,
                ),
            ),
        )
        assertEquals(2, result.size)
        val until = result[0] as RecordedAction.ScrollUntilVisible
        assertEquals("com.example.app:id/btn_pay", until.visibleId)
        assertEquals(ScrollDirection.DOWN, until.direction)
        assertTrue(result[1] is RecordedAction.Tap)
    }

    @Test
    fun twoScrollsPlusTapWithTextOnly_becomeScrollUntilVisibleOnText() {
        val result = ScrollCoalescer.coalesce(
            listOf(
                scroll(1_000),
                scroll(1_600),
                RecordedAction.Tap(packageName = "com.example.app", text = "Esci", timestampMs = 2_000),
            ),
        )
        val until = result[0] as RecordedAction.ScrollUntilVisible
        assertEquals("Esci", until.visibleText)
    }

    @Test
    fun twoScrollsPlusPointTap_collapseToSingleScroll() {
        val result = ScrollCoalescer.coalesce(
            listOf(
                scroll(1_000),
                scroll(1_600),
                RecordedAction.Tap(
                    packageName = "com.example.app",
                    pointPercentX = 0.5f,
                    pointPercentY = 0.8f,
                    timestampMs = 2_000,
                ),
            ),
        )
        assertEquals(2, result.size)
        assertTrue(result[0] is RecordedAction.Scroll)
        assertTrue(result[1] is RecordedAction.Tap)
    }

    @Test
    fun singleScrollPlusTap_isNotConverted() {
        val result = ScrollCoalescer.coalesce(
            listOf(
                scroll(1_000),
                RecordedAction.Tap(
                    packageName = "com.example.app",
                    viewId = "com.example.app:id/btn",
                    timestampMs = 2_000,
                ),
            ),
        )
        assertEquals(2, result.size)
        assertTrue(result[0] is RecordedAction.Scroll)
    }

    @Test
    fun scrollRunWithoutTap_collapseToOne() {
        val result = ScrollCoalescer.coalesce(
            listOf(scroll(1_000), scroll(1_500), scroll(2_000)),
        )
        assertEquals(1, result.size)
        assertTrue(result[0] is RecordedAction.Scroll)
    }

    @Test
    fun differentDirections_areSeparateRuns() {
        val result = ScrollCoalescer.coalesce(
            listOf(
                scroll(1_000, ScrollDirection.DOWN),
                scroll(1_500, ScrollDirection.UP),
            ),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun nonScrollActionsBetweenScrolls_keepOrder() {
        val result = ScrollCoalescer.coalesce(
            listOf(
                scroll(1_000),
                RecordedAction.Wait(packageName = "com.example.app", timeoutMs = 500),
                scroll(2_000),
            ),
        )
        assertEquals(3, result.size)
        assertTrue(result[1] is RecordedAction.Wait)
    }
}
