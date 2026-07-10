package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regressione anti-FP per SMALL_TOUCH_TARGET (shell layout, zone vuote, phantom). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TouchTargetPrecisionTest {

    private val density = 3f

    @Test
    fun contentShell_smallClickable_skipsTouch() {
        val content = snap(
            id = "content",
            bounds = Rect(0, 0, 1080, 400),
            className = "android.widget.FrameLayout",
            clickable = true,
        )
        val all = listOf(content, viewport())
        assertTrue(PrecisionRules.isClickableLayoutShell(content))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(content, all))
    }

    @Test
    fun scrollviewPortShell_skipsTouch() {
        val scroll = snap(
            id = "scrollview_port",
            bounds = Rect(0, 200, 1080, 1800),
            className = "android.widget.ScrollView",
            clickable = true,
            scrollable = true,
        )
        val all = listOf(scroll, viewport())
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(scroll, all))
    }

    @Test
    fun gridTileFrameLayout_skipsTouch() {
        val tile = snap(
            id = "tile",
            bounds = Rect(26, 300, 350, 550),
            className = "android.widget.FrameLayout",
            clickable = true,
        )
        val all = listOf(tile, viewport())
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(tile, all))
    }

    @Test
    fun realButton40dp_insideCard_keepsTouchCheck() {
        val card = snap(
            id = "card",
            bounds = Rect(0, 400, 1080, 900),
            className = "android.widget.FrameLayout",
            clickable = true,
        )
        val button = snap(
            id = "renew",
            bounds = Rect(800, 800, 920, 840),
            className = "android.widget.Button",
            clickable = true,
        )
        val all = listOf(card, button, viewport())
        assertFalse(PrecisionRules.isClickableLayoutShell(button))
        assertFalse(PrecisionRules.shouldSkipTouchTargetCheck(button, all))
    }

    @Test
    fun vopInfoIcon24dp_keepsTouchCheck() {
        val icon = snap(
            id = "vop_info",
            bounds = Rect(900, 400, 924, 424),
            className = "android.widget.ImageView",
            clickable = true,
        )
        val all = listOf(icon, viewport())
        assertFalse(PrecisionRules.shouldSkipTouchTargetCheck(icon, all))
    }

    @Test
    fun phantomHorizontalStrip_skipsTouch() {
        val strip = snap(
            id = "drawer_scrim",
            bounds = Rect(0, 0, 1080, 30),
            className = "android.view.View",
            clickable = true,
        )
        val all = listOf(strip, viewport())
        assertTrue(PrecisionRules.isPhantomClickableBounds(strip))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(strip, all))
    }

    @Test
    fun emptyClickableTextView_skipsTouch() {
        val empty = snap(
            id = "background",
            bounds = Rect(100, 500, 200, 530),
            className = "android.widget.TextView",
            clickable = true,
        )
        val all = listOf(empty, viewport())
        assertTrue(PrecisionRules.isEmptyClickableHitArea(empty, all))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(empty, all))
    }

    @Test
    fun composeSemanticsSmallWithoutLabel_skipsTouch() {
        val semantics = snap(
            id = "",
            bounds = Rect(10, 10, 40, 40),
            className = "androidx.compose.ui.semantics.SemanticsNode",
            clickable = true,
        )
        val all = listOf(semantics, viewport())
        assertTrue(PrecisionRules.shouldSkipComposeTouch(semantics))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(semantics, all))
    }

    @Test
    fun webViewDescendantWithoutLabel_skipsTouch() {
        val web = snap(
            id = "webview",
            bounds = Rect(0, 200, 1080, 2000),
            className = "android.webkit.WebView",
        )
        val inner = snap(
            id = "btn_inner",
            bounds = Rect(100, 400, 130, 430),
            className = "android.view.View",
            clickable = true,
        )
        val all = listOf(web, inner, viewport())
        assertTrue(PrecisionRules.isInsideWebView(inner, all))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(inner, all))
    }

    @Test
    fun fullscreenEmptyClickable_skipsTouch() {
        val emptyState = snap(
            id = "empty_container",
            bounds = Rect(0, 200, 1080, 2000),
            className = "android.widget.LinearLayout",
            clickable = true,
        )
        val all = listOf(emptyState, viewport())
        assertTrue(PrecisionRules.isEmptyClickableHitArea(emptyState, all))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(emptyState, all))
    }

    private fun viewport() = snap(
        id = "root",
        bounds = Rect(0, 0, 1080, 2400),
        className = "android.widget.FrameLayout",
    )

    private fun snap(
        id: String,
        text: String? = null,
        bounds: Rect = Rect(0, 0, 100, 100),
        className: String = "android.widget.TextView",
        clickable: Boolean = false,
        scrollable: Boolean = false,
    ) = NodeSnapshot(
        className = className,
        bounds = bounds,
        viewId = if (id.isBlank()) null else "com.example:id/$id",
        text = text,
        contentDescription = null,
        hintText = null,
        tooltipText = null,
        isClickable = clickable,
        isLongClickable = false,
        isFocusable = clickable,
        isEditable = false,
        isCheckable = false,
        isChecked = false,
        isScrollable = scrollable,
        isEnabled = true,
        isPassword = false,
        isHeading = false,
        headingLevel = 0,
        hasLabeledBy = false,
        hasLabelFor = false,
        errorText = null,
        stateDescription = null,
        isExpanded = null,
        collectionRow = -1,
        collectionColumn = -1,
        childCount = 0,
        isAccessibilityExcluded = false,
        isLikelyDecorative = false,
        traversalIndex = 0,
        rangeCurrent = null,
        rangeMin = null,
        rangeMax = null,
        unlabeledActionCount = 0,
        minTextHeightPx = 36,
        minTouchTargetPx = 144,
        textSizeSp = if (className.contains("TextView")) bounds.height() / density else null,
    )
}
