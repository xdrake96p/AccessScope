package dev.accessscope.scanner.recorder.optimization.selector

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
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

    @Test
    fun buildChain_ordersIdThenTextThenPoint() {
        val tap = RecordedAction.Tap(
            "com.app",
            viewId = "com.app:id/signInButton",
            text = "ACCEDI",
            pointPercentX = 50f,
            pointPercentY = 80f,
        )
        val chain = SelectorRanker.buildChain(tap, listOf(tap))
        assertTrue(chain.size >= 2)
        assertEquals("com.app:id/signInButton", chain.first().viewId)
        assertTrue(chain.any { it.text == "ACCEDI" })
        assertTrue(chain.last().pointPercentX != null || chain.any { it.pointPercentX != null })
    }

    @Test
    fun buildChain_ambiguousHeader_prefersText() {
        val tap = RecordedAction.Tap(
            "com.app",
            viewId = "com.app:id/header",
            text = "Le mie garanzie",
            pointPercentX = 50f,
            pointPercentY = 40f,
        )
        val chain = SelectorRanker.buildChain(tap, listOf(tap))
        assertTrue(chain.first().text == "Le mie garanzie" || chain.first().viewId == null)
        assertTrue(chain.none { MaestroSelectorHeuristics.shortViewId(it.viewId) == "header" } ||
            chain.first().text != null)
    }

    @Test
    fun attachChains_populatesEmpty() {
        val raw = listOf(
            RecordedAction.Tap("com.app", viewId = "com.app:id/ok", text = "OK"),
        )
        val out = SelectorRanker.attachChains(raw)
        val tap = out[0] as RecordedAction.Tap
        assertTrue(tap.selectorChain.isNotEmpty())
    }
}
