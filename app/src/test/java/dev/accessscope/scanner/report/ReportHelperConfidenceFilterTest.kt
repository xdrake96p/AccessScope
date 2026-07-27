package dev.accessscope.scanner.report

import dev.accessscope.scanner.analyzer.ViolationConfidencePolicy
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressione filtro confidenza e demotion rumore strutturale.
 */
class ReportHelperConfidenceFilterTest {

    @Test
    fun filterViolations_dropsStructuralOverlapBelowThreshold() {
        val noisy = AccessibilityViolation(
            type = ViolationType.OVERLAPPING_TOUCH_TARGETS,
            viewClassName = "FrameLayout",
            screenTitle = "Home",
            packageName = "com.example",
            details = "Sovrapposizione 250000px² con View.",
            viewId = "com.example:id/content",
            confidence = 0.88f,
        )
        val filtered = ReportHelper.filterViolations(listOf(noisy))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun filterViolations_keepsRealOverlap() {
        val real = AccessibilityViolation(
            type = ViolationType.OVERLAPPING_TOUCH_TARGETS,
            viewClassName = "Button",
            screenTitle = "Home",
            packageName = "com.example",
            details = "Sovrapposizione 400px² con ImageButton.",
            viewId = "com.example:id/btn_ok",
            confidence = 0.88f,
        )
        val filtered = ReportHelper.filterViolations(listOf(real))
        assertEquals(1, filtered.size)
    }

    @Test
    fun filterViolations_includeLowConfidenceKeepsNoisy() {
        val noisy = AccessibilityViolation(
            type = ViolationType.CUSTOM_ACTION_UNLABELED,
            viewClassName = "View",
            screenTitle = "Distinte",
            packageName = "com.example",
            details = "1 azione senza etichetta.",
            viewId = "com.example:id/content",
            confidence = 0.88f,
        )
        val filtered = ReportHelper.filterViolations(listOf(noisy), includeLowConfidence = true)
        assertEquals(1, filtered.size)
        assertTrue(filtered[0].confidence < 0.78f)
    }

    @Test
    fun demoteIfNoisy_structuralCustomAction() {
        val v = AccessibilityViolation(
            type = ViolationType.CUSTOM_ACTION_UNLABELED,
            viewClassName = "View",
            screenTitle = "X",
            packageName = "p",
            details = "azioni",
            viewId = "p:id/container",
            confidence = 0.88f,
        )
        val demoted = ViolationConfidencePolicy.demoteIfNoisy(v)
        assertEquals(0.55f, demoted.confidence, 0.001f)
        assertFalse(demoted.confidence >= ReportHelper.confidenceThreshold(v.type))
    }

    @Test
    fun confidenceGateStats_countsExcludedByType() {
        val noisy = AccessibilityViolation(
            type = ViolationType.OVERLAPPING_TOUCH_TARGETS,
            viewClassName = "FrameLayout",
            screenTitle = "Home",
            packageName = "com.example",
            details = "Sovrapposizione 250000px² con View.",
            viewId = "com.example:id/content",
            confidence = 0.88f,
        )
        val real = AccessibilityViolation(
            type = ViolationType.MISSING_LABEL,
            viewClassName = "ImageView",
            screenTitle = "Home",
            packageName = "com.example",
            details = "No contentDescription",
            viewId = "com.example:id/icon",
            confidence = 0.90f,
        )
        val stats = ReportHelper.confidenceGateStats(listOf(noisy, real))
        assertEquals(2, stats.rawCount)
        assertEquals(1, stats.excludedCount)
        assertEquals(1, stats.byType[ViolationType.OVERLAPPING_TOUCH_TARGETS])
    }

    @Test
    fun isStructuralNoiseId_recognizesContentAndScroll() {
        assertTrue(ViolationConfidencePolicy.isStructuralNoiseId("app:id/content"))
        assertTrue(ViolationConfidencePolicy.isStructuralNoiseId("app:id/scrollview_port"))
        assertFalse(ViolationConfidencePolicy.isStructuralNoiseId("app:id/vop_info"))
    }
}
