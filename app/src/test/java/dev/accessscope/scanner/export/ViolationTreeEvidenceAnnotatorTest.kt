package dev.accessscope.scanner.export

import android.graphics.Rect
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ViolationTreeEvidenceAnnotatorTest {

    @Test
    fun annotateWireframe_producesBitmap() {
        val violation = AccessibilityViolation(
            type = ViolationType.MISSING_LABEL,
            viewClassName = "android.widget.Button",
            screenTitle = "Inserisci PIN",
            packageName = "it.example",
            details = "test",
            viewId = "it.example:id/uno",
            boundsLeft = 100,
            boundsTop = 500,
            boundsRight = 200,
            boundsBottom = 560,
        )
        val viewport = Rect(0, 0, 1080, 2400)
        val focus = Rect(100, 500, 200, 560)
        val bitmap = ViolationTreeEvidenceAnnotator.annotateWireframe(
            violation = violation,
            viewport = viewport,
            focusBounds = focus,
            nearbyBounds = listOf(Rect(80, 480, 220, 580)),
        )
        try {
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
        } finally {
            bitmap.recycle()
        }
    }
}
