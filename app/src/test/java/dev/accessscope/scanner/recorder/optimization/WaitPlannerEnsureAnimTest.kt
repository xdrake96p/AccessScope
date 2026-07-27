package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitPlannerEnsureAnimTest {

    @Test
    fun ensureAnimationWaits_insertsBetweenTaps() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.LaunchApp(pkg),
            RecordedAction.Tap(pkg, text = "A", timestampMs = 1_000L),
            RecordedAction.Tap(pkg, text = "B", timestampMs = 2_000L),
        )
        val out = WaitPlanner.ensureAnimationWaits(raw, pkg)
        assertTrue(out.any { it is RecordedAction.WaitForAnimation })
        assertTrue(out.filterIsInstance<RecordedAction.WaitForAnimation>().size >= 2)
    }

    @Test
    fun ensureAnimationWaits_skipsIfAlreadyPresent() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "A", timestampMs = 1_000L),
            RecordedAction.WaitForAnimation(pkg, timeoutMs = 800L, timestampMs = 1_100L),
            RecordedAction.Tap(pkg, text = "B", timestampMs = 2_000L),
        )
        val out = WaitPlanner.ensureAnimationWaits(raw, pkg)
        // Solo dopo B non serve (fine); dopo A già c’è wait → 1 wait + eventualmente dopo B niente
        assertTrue(out.count { it is RecordedAction.WaitForAnimation } == 1)
    }

    @Test
    fun enrich_alwaysAddsAnimAfterTap() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "Home", timestampMs = 1_000L),
            RecordedAction.Tap(pkg, text = "Profilo", timestampMs = 1_200L),
        )
        val out = WaitPlanner.enrich(raw, pkg, null, null)
        assertTrue(out.any { it is RecordedAction.WaitForAnimation })
    }
}
