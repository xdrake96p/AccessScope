package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitPlannerTest {

    @Test
    fun sameScreenTapToInput_noExtendedWait() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", viewId = "com.app:id/username", timestampMs = 1_000L),
            RecordedAction.InputText("com.app", "user", viewId = "com.app:id/username", timestampMs = 1_200L),
        )
        val enriched = WaitPlanner.enrich(actions, "com.app", null, null)
        assertFalse(enriched.any { it is RecordedAction.Wait && it.timeoutMs >= 10_000L })
    }

    @Test
    fun inputText_doesNotInsertHideKeyboard() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.InputText("com.app", "user"),
            RecordedAction.Tap("com.app", text = "Next"),
        )
        val enriched = WaitPlanner.enrich(actions, "com.app", null, null)
        assertTrue(enriched.none { it is RecordedAction.HideKeyboard })
    }

    @Test
    fun navigation_insertsAnimationWait() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", viewId = "com.app:id/signInButton", timestampMs = 1_000L),
            RecordedAction.Tap("com.app", viewId = "com.app:id/username", timestampMs = 4_000L),
        )
        val enriched = WaitPlanner.enrich(actions, "com.app", null, null)
        assertTrue(enriched.any { it is RecordedAction.WaitForAnimation })
    }

    @Test
    fun continuaTap_insertsWaitBeforeNextInput() {
        val actions = listOf(
            RecordedAction.Tap("com.app", text = "CONTINUA", timestampMs = 1_000L),
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode", timestampMs = 18_000L),
        )
        val enriched = WaitPlanner.enrich(actions, "com.app", null, null)
        assertTrue(enriched.any { it is RecordedAction.WaitForAnimation })
        assertTrue(enriched.any { it is RecordedAction.Wait && it.visibleId != null })
        assertTrue(WaitPlanner.isSubmitLikeTap(actions[0] as RecordedAction.Tap))
    }

    @Test
    fun attachBlindWaits_fillsPincodeVisibleId() {
        val actions = listOf(
            RecordedAction.Tap("com.app", text = "CONTINUA"),
            RecordedAction.Wait("com.app", timeoutMs = 10_000L),
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode"),
        )
        val attached = WaitPlanner.attachBlindWaitsToNextTarget(actions, "com.app")
        val wait = attached[1] as RecordedAction.Wait
        assertTrue(wait.visibleId!!.contains("pincode"))
        assertEquals(10_000L, wait.timeoutMs)
    }
}
