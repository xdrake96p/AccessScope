package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regressione per pattern UI Android generici (WebView, Map, media, skeleton, modal, Compose, Lottie). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PlatformPatternsRegressionTest {

    private val density = 3f

    @Test
    fun skeletonPlaceholder_skippedFromStructuralNoise() {
        val skeleton = snap(id = "skeleton_row_1", bounds = Rect(0, 200, 1080, 280))
        assertTrue(PrecisionRules.isSkeletonPlaceholder(skeleton))
        assertTrue(
            PrecisionRules.shouldSkipStructuralNoise(skeleton, Rect(0, 0, 1080, 2400), 1080),
        )
    }

    @Test
    fun lottieNonInteractive_isDecorative() {
        val lottie = snap(
            id = "loading_lottie",
            bounds = Rect(400, 1000, 680, 1280),
            className = "com.airbnb.lottie.LottieAnimationView",
        )
        assertTrue(PrecisionRules.isLottieAnimation(lottie))
        assertTrue(PrecisionRules.isDecorative(lottie))
    }

    @Test
    fun mapMarkerInsideMap_skipsTouchTarget() {
        val map = snap(
            id = "map_view",
            bounds = Rect(0, 400, 1080, 1400),
            className = "com.google.android.gms.maps.MapView",
        )
        val marker = snap(
            id = "marker_pin",
            bounds = Rect(500, 800, 540, 840),
            className = "android.view.View",
            clickable = true,
        )
        val all = listOf(map, marker)
        assertTrue(PrecisionRules.isInsideMapOrMediaSurface(marker, all))
        assertTrue(PrecisionRules.shouldSkipTouchTargetCheck(marker, all))
    }

    @Test
    fun mediaPlayerSurface_skipsSilentDynamicContent() {
        val player = snap(
            id = "video_player",
            bounds = Rect(0, 300, 1080, 900),
            className = "com.google.android.exoplayer2.ui.PlayerView",
        )
        assertTrue(PrecisionRules.shouldSkipSilentDynamicContent("VIDEO", listOf(player), "com.example"))
    }

    @Test
    fun modalOverlay_obscuresBackgroundNodes() {
        val dialog = snap(
            id = "bottom_sheet_container",
            bounds = Rect(0, 600, 1080, 2400),
            className = "com.google.android.material.bottomsheet.BottomSheetDialog",
        )
        val backgroundBtn = snap(
            id = "continua",
            text = "Continua",
            bounds = Rect(100, 200, 980, 300),
            clickable = true,
        )
        val all = listOf(dialog, backgroundBtn, viewport())
        assertTrue(PrecisionRules.isObscuredByModalOverlay(backgroundBtn, all))
        assertFalse(PrecisionRules.isObscuredByModalOverlay(dialog, all))
    }

    @Test
    fun webView_withAccessibleChild_noBarrier() {
        val web = snap(
            id = "webview_content",
            bounds = Rect(0, 200, 1080, 1800),
            className = "android.webkit.WebView",
        )
        val link = snap(
            id = "",
            text = "Apri dettaglio",
            bounds = Rect(100, 400, 500, 460),
            clickable = true,
        )
        val all = listOf(web.copy(childCount = 0), link)
        assertFalse(PrecisionRules.shouldReportWebViewBarrier(web, all))
    }

    @Test
    fun webView_empty_reportsBarrier() {
        val web = snap(
            id = "webview_content",
            bounds = Rect(0, 200, 1080, 1800),
            className = "android.webkit.WebView",
        )
        assertTrue(PrecisionRules.shouldReportWebViewBarrier(web, listOf(web, viewport())))
    }

    @Test
    fun composeSemanticsWithoutViewId_skipsContrast() {
        val compose = snap(
            id = "",
            text = "Saldo disponibile",
            bounds = Rect(100, 300, 500, 360),
            className = "androidx.compose.ui.platform.AndroidComposeView",
        )
        assertTrue(PrecisionRules.shouldSkipComposeContrast(compose))
    }

    @Test
    fun composeClickableWithLabel_keepsTouchCheck() {
        val btn = snap(
            id = "",
            text = "Conferma",
            bounds = Rect(100, 1800, 980, 1950),
            className = "androidx.compose.ui.semantics.SemanticsNode",
            clickable = true,
        )
        assertFalse(PrecisionRules.shouldSkipComposeTouch(btn))
    }

    @Test
    fun overlapInsideMap_skipped() {
        val map = snap(
            id = "map",
            bounds = Rect(0, 0, 1080, 1200),
            className = "com.google.android.gms.maps.MapView",
        )
        val a = snap(id = "pin_a", bounds = Rect(400, 500, 440, 540), clickable = true)
        val b = snap(id = "pin_b", bounds = Rect(420, 510, 460, 550), clickable = true)
        val all = listOf(map, a, b)
        assertTrue(PrecisionRules.shouldSkipOverlapBetween(a, b, all, "com.example", 1080))
    }

    private fun viewport() = snap(
        id = "content",
        bounds = Rect(0, 0, 1080, 2400),
        className = "android.widget.FrameLayout",
    )

    private fun snap(
        id: String,
        text: String? = null,
        bounds: Rect = Rect(0, 0, 100, 100),
        className: String = "android.widget.TextView",
        clickable: Boolean = false,
        childCount: Int = 0,
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
        isScrollable = false,
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
        childCount = childCount,
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
