package dev.accessscope.scanner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootAcquisitionHelperTest {

    private val target = "com.example.target"

    @Test
    fun prioritizeCandidates_prefersFocusedOverBackground() {
        val result = RootAcquisitionHelper.prioritizeCandidates(
            target,
            listOf(
                RootCandidateMeta(target, RootSource.BACKGROUND_WINDOW, windowId = 2, dedupeKey = "w:2"),
                RootCandidateMeta(target, RootSource.FOCUSED_WINDOW, windowId = 1, dedupeKey = "w:1"),
            ),
        )
        assertEquals(RootSource.FOCUSED_WINDOW, result.first().source)
        assertEquals(2, result.size)
    }

    @Test
    fun prioritizeCandidates_prefersEventSourceOverBackground() {
        val result = RootAcquisitionHelper.prioritizeCandidates(
            target,
            listOf(
                RootCandidateMeta(target, RootSource.BACKGROUND_WINDOW, dedupeKey = "w:9"),
                RootCandidateMeta(target, RootSource.EVENT_SOURCE, dedupeKey = "event:1"),
            ),
        )
        assertEquals(RootSource.EVENT_SOURCE, result.first().source)
    }

    @Test
    fun prioritizeCandidates_ignoresNonTargetPackages() {
        val result = RootAcquisitionHelper.prioritizeCandidates(
            target,
            listOf(
                RootCandidateMeta("dev.accessscope.scanner", RootSource.ACTIVE_WINDOW, dedupeKey = "a:1"),
                RootCandidateMeta(target, RootSource.FOCUSED_WINDOW, dedupeKey = "w:1"),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(target, result.first().packageName)
    }

    @Test
    fun prioritizeCandidates_deduplicatesByKey() {
        val result = RootAcquisitionHelper.prioritizeCandidates(
            target,
            listOf(
                RootCandidateMeta(target, RootSource.FOCUSED_WINDOW, dedupeKey = "w:1"),
                RootCandidateMeta(target, RootSource.BACKGROUND_WINDOW, dedupeKey = "w:1"),
            ),
        )
        assertEquals(1, result.size)
    }

    @Test
    fun prioritizeCandidates_emptyForNoMatch() {
        val result = RootAcquisitionHelper.prioritizeCandidates(
            target,
            listOf(
                RootCandidateMeta("other.app", RootSource.ACTIVE_WINDOW, dedupeKey = "a:1"),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun prioritizeCandidates_ordersActiveBeforeBackground() {
        val result = RootAcquisitionHelper.prioritizeCandidates(
            target,
            listOf(
                RootCandidateMeta(target, RootSource.BACKGROUND_WINDOW, dedupeKey = "w:2"),
                RootCandidateMeta(target, RootSource.ACTIVE_WINDOW, dedupeKey = "active:1"),
            ),
        )
        assertEquals(RootSource.ACTIVE_WINDOW, result.first().source)
    }
}
