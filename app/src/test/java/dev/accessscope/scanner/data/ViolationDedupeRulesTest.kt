package dev.accessscope.scanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ViolationDedupeRulesTest {

    @Test
    fun sameViewIdDifferentFingerprint_producesSameKey() {
        val base = violation(
            viewId = "com.example:id/login_button",
            screenFingerprint = "fp-a",
        )
        val scrolled = base.copy(screenFingerprint = "fp-b-scroll")
        assertEquals(ViolationDedupeRules.keyFor(base), ViolationDedupeRules.keyFor(scrolled))
    }

    @Test
    fun recyclerViewIndexSuffix_normalizedToSameKey() {
        val item0 = violation(viewId = "com.example:id/row_item_0")
        val item1 = violation(viewId = "com.example:id/row_item_1")
        assertEquals(ViolationDedupeRules.keyFor(item0), ViolationDedupeRules.keyFor(item1))
    }

    @Test
    fun noViewId_slightlyDifferentBounds_quantizedToSameKey() {
        val a = violation(
            viewId = null,
            bounds = "108×48 px @(10,100)",
            viewClassName = "android.widget.TextView",
        )
        val b = violation(
            viewId = null,
            bounds = "108×48 px @(25,115)",
            viewClassName = "android.widget.TextView",
        )
        assertEquals(ViolationDedupeRules.keyFor(a), ViolationDedupeRules.keyFor(b))
    }

    @Test
    fun noViewId_distinctElements_produceDifferentKeys() {
        val a = violation(
            viewId = null,
            bounds = "108×48 px @(10,100)",
            screenTitle = "Home",
            viewClassName = "android.widget.TextView",
        )
        val b = violation(
            viewId = null,
            bounds = "108×48 px @(200,400)",
            screenTitle = "Home",
            viewClassName = "android.widget.TextView",
        )
        assertNotEquals(ViolationDedupeRules.keyFor(a), ViolationDedupeRules.keyFor(b))
    }

    @Test
    fun globalTopbarWidget_usesGlobalScope() {
        val home = violation(viewId = "com.example:id/topbar_icon_left", screenTitle = "Home")
        val settings = violation(viewId = "com.example:id/topbar_icon_left", screenTitle = "Settings")
        assertEquals(ViolationDedupeRules.keyFor(home), ViolationDedupeRules.keyFor(settings))
    }

    @Test
    fun normalizeViewId_stripsNumericSuffix() {
        assertEquals("row_item", ViolationDedupeRules.normalizeViewId("com.app:id/row_item_42"))
    }

    @Test
    fun quantizeBoundsLabel_parsesNodeSnapshotFormat() {
        assertEquals("0,96", ViolationDedupeRules.quantizeBoundsLabel("120×40 px @(5,100)"))
    }

    @Test
    fun noViewId_sameElementLabel_differentBounds_sameKey() {
        val a = violation(
            viewId = null,
            bounds = "108×48 px @(10,100)",
            screenTitle = "Home",
            elementLabel = "DISTINTE (3)",
        )
        val b = a.copy(bounds = "108×48 px @(680,1069)")
        assertEquals(ViolationDedupeRules.keyFor(a), ViolationDedupeRules.keyFor(b))
    }

    @Test
    fun noViewId_distinctLabels_differentKeys() {
        val a = violation(
            viewId = null,
            bounds = "108×48 px @(10,100)",
            elementLabel = "DISTINTE (3)",
        )
        val b = violation(
            viewId = null,
            bounds = "108×48 px @(10,100)",
            elementLabel = "BONIFICI (2)",
        )
        assertNotEquals(ViolationDedupeRules.keyFor(a), ViolationDedupeRules.keyFor(b))
    }

    private fun violation(
        viewId: String? = "com.example:id/btn",
        screenFingerprint: String? = "fp-1",
        bounds: String? = "48×48 px @(0,0)",
        screenTitle: String = "Login",
        viewClassName: String = "android.widget.Button",
        type: ViolationType = ViolationType.MISSING_LABEL,
        packageName: String = "com.example",
        elementLabel: String? = null,
    ) = AccessibilityViolation(
        type = type,
        viewClassName = viewClassName,
        screenTitle = screenTitle,
        packageName = packageName,
        details = "test",
        viewId = viewId,
        bounds = bounds,
        screenFingerprint = screenFingerprint,
        elementLabel = elementLabel,
    )
}
