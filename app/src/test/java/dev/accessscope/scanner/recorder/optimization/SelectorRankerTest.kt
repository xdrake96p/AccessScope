package dev.accessscope.scanner.recorder.optimization.selector

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorRankerTest {

    @Test
    fun prefersIdOverPoint() {
        val actions = listOf(
            RecordedAction.Tap(
                "com.app",
                viewId = "com.app:id/signInButton",
                pointPercentX = 50f,
                pointPercentY = 50f,
            ),
            RecordedAction.Tap("com.app", viewId = "com.app:id/signInButton"),
        )
        val tap = actions[0]
        assertTrue(SelectorRanker.shouldExportIdOnly(tap, actions))
    }

    @Test
    fun structuralDrawer_prefersTextNotId() {
        val tap = RecordedAction.Tap(
            "com.app",
            viewId = "com.app:id/drawer_layout",
            text = "POLIZZA N. 404347818",
            pointPercentX = 50f,
            pointPercentY = 50f,
        )
        assertTrue(MaestroSelectorHeuristics.isStructuralContainerViewId(tap.viewId))
        assertFalse(SelectorRanker.shouldExportIdOnly(tap, listOf(tap)))
        val normalized = SelectorNormalizer.normalizeViewIds(listOf(tap), "com.app")
        assertNull((normalized[0] as RecordedAction.Tap).viewId)
        assertTrue((normalized[0] as RecordedAction.Tap).text!!.contains("POLIZZA"))
    }
}
