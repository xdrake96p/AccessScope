package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportHelperSeverityTest {

    @Test
    fun groupViolationsBySeverity_ordersCriticalFirst() {
        val violations = listOf(
            violation(ViolationType.DECORATIVE_IMAGE_LABELED),
            violation(ViolationType.MISSING_LABEL),
            violation(ViolationType.TEXT_TOO_SMALL),
        )
        val grouped = ReportHelper.groupViolationsBySeverity(violations)
        assertEquals(ViolationSeverity.CRITICAL, grouped[0].first)
        assertEquals(ViolationSeverity.SERIOUS, grouped[1].first)
        assertEquals(ViolationSeverity.MINOR, grouped[2].first)
        assertEquals(1, grouped[0].second.sumOf { it.second.size })
    }

    private fun violation(type: ViolationType) = AccessibilityViolation(
        type = type,
        viewClassName = "View",
        screenTitle = "Home",
        packageName = "com.example",
        details = "detail",
    )
}
