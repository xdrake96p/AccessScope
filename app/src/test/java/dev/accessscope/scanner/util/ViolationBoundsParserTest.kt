package dev.accessscope.scanner.util

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ViolationBoundsParserTest {

    @Test
    fun parse_nodeSnapshotFormat() {
        val rect = ViolationBoundsParser.parse("120\u00D748 px @(5,100)")
        assertNotNull(rect)
        assertEquals(5, rect!!.left)
        assertEquals(100, rect.top)
        assertEquals(125, rect.right)
        assertEquals(148, rect.bottom)
    }

    @Test
    fun parse_legacyFormat() {
        val rect = ViolationBoundsParser.parse("10,20-130,80")
        assertNotNull(rect)
        assertEquals(10, rect!!.left)
        assertEquals(20, rect.top)
        assertEquals(130, rect.right)
        assertEquals(80, rect.bottom)
    }

    @Test
    fun rect_prefersStructuredFields() {
        val violation = AccessibilityViolation(
            type = ViolationType.MISSING_LABEL,
            viewClassName = "Button",
            screenTitle = "Home",
            packageName = "com.example",
            details = "test",
            bounds = "1\u00D71 px @(0,0)",
            boundsLeft = 50,
            boundsTop = 60,
            boundsRight = 150,
            boundsBottom = 120,
        )
        val rect = ViolationBoundsParser.rect(violation)
        assertNotNull(rect)
        assertEquals(50, rect!!.left)
        assertEquals(150, rect.right)
    }

    @Test
    fun parse_invalidReturnsNull() {
        assertNull(ViolationBoundsParser.parse(null))
        assertNull(ViolationBoundsParser.parse("invalid"))
    }
}
