package dev.accessscope.scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ScreenshotAnalyzerTest {

    @Test
    fun nullBitmap_isBlackOrEmpty() {
        assertTrue(ScreenshotAnalyzer.isBlackOrEmpty(null))
    }

    @Test
    fun allBlackBitmap_isBlackOrEmpty() {
        val bmp = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLACK)
        try {
            assertTrue(ScreenshotAnalyzer.isBlackOrEmpty(bmp))
            assertFalse(ScreenshotAnalyzer.isUsableForContrast(bmp))
        } finally {
            bmp.recycle()
        }
    }

    @Test
    fun whiteBitmap_isUsable() {
        val bmp = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        try {
            assertFalse(ScreenshotAnalyzer.isBlackOrEmpty(bmp))
            assertTrue(ScreenshotAnalyzer.isUsableForContrast(bmp))
        } finally {
            bmp.recycle()
        }
    }
}
