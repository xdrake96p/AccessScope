package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.ReportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WcagContrastHexTest {

    @Test
    fun formatArgbHex_opaqueUsesRgb() {
        assertEquals("#FF5722", WcagContrast.formatArgbHex(0xFFFF5722.toInt()))
    }

    @Test
    fun formatArgbHex_transparentIncludesAlpha() {
        val hex = WcagContrast.formatArgbHex(0x80FF5722.toInt())
        assertTrue(hex.startsWith("#"))
        assertEquals(9, hex.length)
    }

    @Test
    fun contrastColorLine_shownForContrastViolations() {
        val v = AccessibilityViolation(
            type = ViolationType.LOW_COLOR_CONTRAST,
            viewClassName = "TextView",
            screenTitle = "Home",
            packageName = "com.example",
            details = "test",
            foregroundColorHex = "#212121",
            backgroundColorHex = "#FFFFFF",
        )
        assertEquals(
            "Colori: primo piano #212121 · sfondo #FFFFFF",
            ReportHelper.contrastColorLine(v),
        )
    }

    @Test
    fun contrastColorLine_hiddenForOtherTypes() {
        val v = AccessibilityViolation(
            type = ViolationType.MISSING_LABEL,
            viewClassName = "Button",
            screenTitle = "Home",
            packageName = "com.example",
            details = "test",
            foregroundColorHex = "#000000",
        )
        assertNull(ReportHelper.contrastColorLine(v))
    }
}
