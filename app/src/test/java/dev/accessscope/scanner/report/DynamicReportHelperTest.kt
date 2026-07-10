package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.data.VisitedScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicReportHelperTest {

    @Test
    fun buildFrames_groupsViolationsByFingerprintInVisitOrder() {
        val visited = listOf(
            VisitedScreen("fp-home", "Home", 0),
            VisitedScreen("fp-settings", "Impostazioni", 1),
        )
        val violations = listOf(
            violation(screenTitle = "Home", fingerprint = "fp-home"),
            violation(screenTitle = "Impostazioni", fingerprint = "fp-settings", type = ViolationType.SMALL_TOUCH_TARGET),
        )

        val frames = DynamicReportHelper.buildFrames(
            visitedScreens = visited,
            violations = violations,
            talkBackFindings = emptyList(),
            checkSummaries = emptyList(),
        )

        assertEquals(2, frames.size)
        assertEquals("Home", frames[0].title)
        assertEquals("fp-home", frames[0].fingerprint)
        assertEquals(1, frames[0].violations.size)
        assertEquals("Impostazioni", frames[1].title)
        assertEquals(1, frames[1].violations.size)
    }

    @Test
    fun buildFrames_includesTalkBackAndPassedCountsPerScreen() {
        val visited = listOf(VisitedScreen("fp-home", "Home", 0))
        val violations = listOf(violation(screenTitle = "Home", fingerprint = "fp-home"))
        val talkBack = listOf(
            ScreenReaderFinding(
                packageName = "com.example",
                screenTitle = "Home",
                nodeClassName = "Button",
                announcedText = "OK",
                issue = "Annuncio incompleto",
            ),
        )
        val summaries = listOf(
            CheckAreaSummary(
                area = ViolationArea.LABELS,
                screenTitle = "Home",
                packageName = "com.example",
                passedCount = 4,
            ),
        )

        val frames = DynamicReportHelper.buildFrames(
            visitedScreens = visited,
            violations = violations,
            talkBackFindings = talkBack,
            checkSummaries = summaries,
        )

        assertEquals(1, frames.size)
        assertEquals(1, frames[0].talkBackFindings.size)
        assertEquals(4, frames[0].passedCount)
    }

    @Test
    fun buildFrames_fallsBackToScreenTitleWhenFingerprintMissing() {
        val violations = listOf(
            violation(screenTitle = "Bonifici", fingerprint = null),
            violation(screenTitle = "Bonifici", fingerprint = null, type = ViolationType.LOW_COLOR_CONTRAST),
        )

        val frames = DynamicReportHelper.buildFrames(
            visitedScreens = emptyList(),
            violations = violations,
            talkBackFindings = emptyList(),
            checkSummaries = emptyList(),
        )

        assertEquals(1, frames.size)
        assertEquals("Bonifici", frames[0].title)
        assertEquals(2, frames[0].violations.size)
        assertTrue(frames[0].fingerprint.startsWith("title::"))
    }

    @Test
    fun buildFrames_duplicateTitle_assignsTalkBackOnlyToFrameWithViolations() {
        val visited = listOf(
            VisitedScreen("fp-home-a", "Home", 0),
            VisitedScreen("fp-home-b", "Home", 1),
        )
        val violations = listOf(
            violation(screenTitle = "Home", fingerprint = "fp-home-a"),
        )
        val talkBack = listOf(
            ScreenReaderFinding(
                packageName = "com.example",
                screenTitle = "Home",
                nodeClassName = "Button",
                announcedText = "OK",
                issue = "Annuncio incompleto",
            ),
        )
        val frames = DynamicReportHelper.buildFrames(
            visitedScreens = visited,
            violations = violations,
            talkBackFindings = talkBack,
            checkSummaries = emptyList(),
        )
        assertEquals(1, frames[0].talkBackFindings.size)
        assertEquals(0, frames[1].talkBackFindings.size)
    }

    @Test
    fun filterFrameViolations_filtersBySeverity() {
        val frame = DynamicScreenFrame(
            fingerprint = "fp",
            title = "Home",
            visitIndex = 0,
            violations = listOf(
                violation(type = ViolationType.MISSING_LABEL),
                violation(type = ViolationType.SMALL_TOUCH_TARGET),
            ),
            talkBackFindings = emptyList(),
            passedCount = 0,
            screenEvidenceId = "fp",
        )

        val filtered = DynamicReportHelper.filterFrameViolations(frame, ViolationSeverity.SERIOUS)
        assertEquals(1, filtered.size)
        assertEquals(ViolationType.SMALL_TOUCH_TARGET, filtered[0].type)
    }

    private fun violation(
        screenTitle: String = "Home",
        fingerprint: String? = "fp-home",
        type: ViolationType = ViolationType.MISSING_LABEL,
    ) = AccessibilityViolation(
        type = type,
        viewClassName = "android.widget.Button",
        screenTitle = screenTitle,
        packageName = "com.example",
        details = "test",
        screenFingerprint = fingerprint,
    )
}
