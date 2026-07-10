package dev.accessscope.scanner.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import dev.accessscope.scanner.data.ViolationSeverity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ViolationEvidenceAnnotatorTest {

    @Test
    fun annotateCrop_drawsInsideBitmap() {
        val screenshot = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        screenshot.eraseColor(Color.WHITE)
        val bounds = Rect(40, 40, 100, 100)
        val annotated = ViolationEvidenceAnnotator.annotateCrop(
            screenshot = screenshot,
            bounds = bounds,
            severity = ViolationSeverity.CRITICAL,
        )
        try {
            assertTrue(annotated.width > 0)
            assertTrue(annotated.height > 0)
            assertTrue(maxOf(annotated.width, annotated.height) <= 560)
            assertTrue(annotated.width >= 200)
        } finally {
            screenshot.recycle()
            annotated.recycle()
        }
    }
}
