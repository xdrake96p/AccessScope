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
class ContrastComplexBackgroundTest {

    @Test
    fun uniformLowContrast_stillReported() {
        val bitmap = solidBitmap(300, 120, Color.WHITE)
        fillRect(bitmap, Rect(40, 30, 260, 90), Color.parseColor("#C8C8C8"))
        val bounds = Rect(50, 40, 250, 80)
        val result = WcagContrast.measureTextContrast(bitmap, bounds, isLargeText = false)
        requireNotNull(result)
        assertFalse(WcagContrast.isComplexBackground(result.backgroundSamples))
        assertTrue(
            WcagContrast.shouldReportTextContrastFailure(
                result,
                WcagContrast.MIN_TEXT_CONTRAST,
                isLargeText = false,
            ),
        )
    }

    @Test
    fun darkHeadingOnIllustratedHero_majorityPasses_notReported() {
        val fg = Color.parseColor("#1A1A1A")
        val light = Color.parseColor("#F3E5F0")
        val accent = Color.parseColor("#5A5A5A")
        val bgSamples = listOf(light, light, light, accent, light, light)
        val worstRatio = WcagContrast.contrastRatio(fg, accent)
        val result = ContrastResult(
            ratio = worstRatio,
            foreground = fg,
            background = accent,
            confidence = 0.92f,
            samplesUsed = 16,
            backgroundSamples = bgSamples,
        )
        assertTrue(WcagContrast.isComplexBackground(bgSamples))
        assertTrue(worstRatio < WcagContrast.MIN_LARGE_TEXT_CONTRAST)
        assertFalse(
            WcagContrast.shouldReportTextContrastFailure(
                result,
                WcagContrast.MIN_LARGE_TEXT_CONTRAST,
                isLargeText = true,
            ),
        )
    }

    @Test
    fun catastrophicContrastOnComplexBg_stillReported() {
        val fg = Color.parseColor("#D0D0D0")
        val light = Color.parseColor("#F5F5F5")
        val bgSamples = List(6) { light }
        val result = ContrastResult(
            ratio = WcagContrast.contrastRatio(fg, light),
            foreground = fg,
            background = light,
            confidence = 0.85f,
            samplesUsed = 14,
            backgroundSamples = bgSamples,
        )
        assertFalse(WcagContrast.isComplexBackground(bgSamples))
        assertTrue(
            WcagContrast.shouldReportTextContrastFailure(
                result,
                WcagContrast.MIN_TEXT_CONTRAST,
                isLargeText = false,
            ),
        )
    }

    @Test
    fun syntheticHighContrast_onWhiteBitmap_unchanged() {
        val bitmap = solidBitmap(400, 200, Color.WHITE)
        fillRect(bitmap, Rect(50, 50, 350, 150), Color.parseColor("#333333"))
        val bounds = Rect(60, 60, 340, 140)
        val result = WcagContrast.measureTextContrast(bitmap, bounds, isLargeText = false)
        requireNotNull(result)
        assertTrue(result.ratio >= 10.0)
        assertFalse(
            WcagContrast.shouldReportTextContrastFailure(
                result,
                WcagContrast.MIN_TEXT_CONTRAST,
                isLargeText = false,
            ),
        )
    }

    private fun solidBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
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
