package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ImageContrastSkipTest {

    @Test
    fun decorativeImage_skipsUiContrast() {
        val image = imageSnap(bounds = Rect(100, 400, 500, 900), decorative = true)
        assertTrue(PrecisionRules.shouldSkipUiContrastCheck(image, emptyList(), screenAreaPx = 1080 * 2400))
    }

    @Test
    fun largeHeroImage_skipsUiContrast() {
        val hero = imageSnap(bounds = Rect(0, 300, 1080, 1200), decorative = false, clickable = false)
        assertTrue(PrecisionRules.shouldSkipUiContrastCheck(hero, emptyList(), screenAreaPx = 1080 * 2400))
    }

    @Test
    fun mediumDecorativeImage_skipsUiContrast() {
        val image = imageSnap(bounds = Rect(100, 400, 700, 1000), decorative = false, clickable = false)
        assertTrue(PrecisionRules.shouldSkipUiContrastCheck(image, emptyList(), screenAreaPx = 1080 * 2400))
    }

    @Test
    fun policyTextOverIllustration_skipsTextContrast() {
        val policy = textSnap(
            id = "policyNumber",
            text = "POLIZZA N. 404347818",
            bounds = Rect(331, 1931, 750, 1949),
        )
        val illustration = imageSnap(
            bounds = Rect(200, 1700, 950, 2100),
            decorative = false,
            clickable = false,
        )
        val all = listOf(policy, illustration)
        assertTrue(PrecisionRules.isTextOverIllustratedBackground(policy, all, 1080 * 2400))
        assertTrue(PrecisionRules.shouldSkipContrastCheck(policy, all, screenAreaPx = 1080 * 2400))
    }

    @Test
    fun smallInteractiveIcon_notSkippedByAreaRule() {
        val icon = imageSnap(bounds = Rect(900, 100, 980, 180), decorative = false, clickable = true)
        assertFalse(PrecisionRules.shouldSkipUiContrastCheck(icon, emptyList(), screenAreaPx = 1080 * 2400))
    }

    @Test
    fun photographicBitmap_skipsRasterContentCheck() {
        val bitmap = gradientBitmap(200, 200)
        val bounds = Rect(20, 20, 180, 180)
        val result = WcagContrast.measureUiContrast(bitmap, bounds)
        requireNotNull(result)
        assertTrue(WcagContrast.isLikelyRasterImageContent(result))
        bitmap.recycle()
    }

    @Test
    fun flatIconBitmap_notRasterContent() {
        val bitmap = solidBitmap(120, 120, Color.WHITE)
        fillRect(bitmap, Rect(30, 30, 90, 90), Color.parseColor("#333333"))
        val result = WcagContrast.measureUiContrast(bitmap, Rect(35, 35, 85, 85))
        requireNotNull(result)
        assertFalse(WcagContrast.isLikelyRasterImageContent(result))
        bitmap.recycle()
    }

    private fun imageSnap(
        bounds: Rect,
        decorative: Boolean,
        clickable: Boolean = false,
    ) = NodeSnapshot(
        className = "android.widget.ImageView",
        bounds = bounds,
        viewId = "it.example:id/hero_image",
        text = null,
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
        childCount = 0,
        isAccessibilityExcluded = false,
        isLikelyDecorative = decorative,
        traversalIndex = 0,
        rangeCurrent = null,
        rangeMin = null,
        rangeMax = null,
        unlabeledActionCount = 0,
        minTextHeightPx = 42,
        minTouchTargetPx = 126,
        textSizeSp = null,
        sectionTitle = null,
    )

    private fun textSnap(
        id: String,
        text: String,
        bounds: Rect,
    ) = NodeSnapshot(
        className = "android.widget.TextView",
        bounds = bounds,
        viewId = "it.example:id/$id",
        text = text,
        contentDescription = null,
        hintText = null,
        tooltipText = null,
        isClickable = false,
        isLongClickable = false,
        isFocusable = false,
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
        childCount = 0,
        isAccessibilityExcluded = false,
        isLikelyDecorative = false,
        traversalIndex = 0,
        rangeCurrent = null,
        rangeMin = null,
        rangeMax = null,
        unlabeledActionCount = 0,
        minTextHeightPx = 42,
        minTouchTargetPx = 126,
        textSizeSp = 12f,
        sectionTitle = null,
    )

    private fun solidBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun gradientBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = Color.rgb((x * 255 / w), (y * 255 / h), 128)
                bmp.setPixel(x, y, c)
            }
        }
        return bmp
    }

    private fun fillRect(bitmap: Bitmap, rect: Rect, color: Int) {
        for (y in rect.top.coerceAtLeast(0) until rect.bottom.coerceAtMost(bitmap.height)) {
            for (x in rect.left.coerceAtLeast(0) until rect.right.coerceAtMost(bitmap.width)) {
                bitmap.setPixel(x, y, color)
            }
        }
    }
}
