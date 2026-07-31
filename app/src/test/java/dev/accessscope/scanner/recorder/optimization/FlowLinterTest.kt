package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.optimization.lint.FlowLinter
import dev.accessscope.scanner.recorder.optimization.lint.LintRule
import dev.accessscope.scanner.recorder.optimization.lint.LintSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test di regressione per [FlowLinter] (piano M1-A1).
 */
class FlowLinterTest {

    private fun tap(
        viewId: String? = null,
        text: String? = null,
        point: Boolean = false,
    ) = RecordedAction.Tap(
        packageName = "com.example.app",
        viewId = viewId,
        text = text,
        pointPercentX = if (point) 0.5f else null,
        pointPercentY = if (point) 0.5f else null,
    )

    @Test
    fun weakSelector_pointOnly_isError() {
        val report = FlowLinter.lint(listOf(tap(point = true)))
        assertEquals(1, report.issues.size)
        assertEquals(LintRule.POINT_ONLY_SELECTOR, report.issues[0].rule)
        assertEquals(LintSeverity.ERROR, report.issues[0].severity)
    }

    @Test
    fun textOnlySelector_isInfo() {
        val report = FlowLinter.lint(listOf(tap(text = "Saldo")))
        assertEquals(LintRule.TEXT_ONLY_SELECTOR, report.issues.single().rule)
        assertEquals(LintSeverity.INFO, report.issues.single().severity)
    }

    @Test
    fun structuralAndNoiseIds_areWarnings() {
        val report = FlowLinter.lint(
            listOf(
                tap(viewId = "com.example.app:id/drawer_layout"),
                tap(viewId = "com.example.app:id/progress_bar"),
            ),
        )
        val rules = report.issues.map { it.rule }
        assertTrue(LintRule.STRUCTURAL_SELECTOR in rules)
        assertTrue(LintRule.NOISE_SELECTOR in rules)
    }

    @Test
    fun volatileId_isWarning() {
        val report = FlowLinter.lint(listOf(tap(viewId = "com.example.app:id/btn_839201")))
        assertTrue(report.issues.any { it.rule == LintRule.VOLATILE_ID })
    }

    @Test
    fun submitTapWithoutWait_isWarning() {
        val report = FlowLinter.lint(
            listOf(
                tap(viewId = "com.example.app:id/btn_continue", text = "Continua"),
                tap(viewId = "com.example.app:id/home_title"),
            ),
        )
        assertTrue(report.issues.any { it.rule == LintRule.MISSING_WAIT_AFTER_SUBMIT })
    }

    @Test
    fun submitTapWithFollowingWait_hasNoSubmitIssue() {
        val report = FlowLinter.lint(
            listOf(
                tap(viewId = "com.example.app:id/btn_continue", text = "Continua"),
                RecordedAction.WaitForAnimation(packageName = "com.example.app"),
            ),
        )
        assertTrue(report.issues.none { it.rule == LintRule.MISSING_WAIT_AFTER_SUBMIT })
    }

    @Test
    fun blindLongWait_isInfo() {
        val report = FlowLinter.lint(
            listOf(RecordedAction.Wait(packageName = "com.example.app", timeoutMs = 8_000L)),
        )
        assertEquals(LintRule.BLIND_WAIT_LONG, report.issues.single().rule)
        assertEquals(LintSeverity.INFO, report.issues.single().severity)
    }

    @Test
    fun waitWithTarget_isNotBlind() {
        val report = FlowLinter.lint(
            listOf(
                RecordedAction.Wait(
                    packageName = "com.example.app",
                    timeoutMs = 8_000L,
                    visibleId = "com.example.app:id/title",
                ),
            ),
        )
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun strongSelectorTap_producesNoIssues() {
        val report = FlowLinter.lint(
            listOf(tap(viewId = "com.example.app:id/btn_pay", text = "Paga")),
        )
        // Solo MISSING_WAIT_AFTER_SUBMIT potrebbe scattare: qui verifichiamo che i selettori sono ok.
        assertTrue(report.issues.none { it.rule == LintRule.WEAK_SELECTOR || it.rule == LintRule.STRUCTURAL_SELECTOR })
    }

    @Test
    fun byStepGroupsIssues() {
        val report = FlowLinter.lint(
            listOf(
                tap(point = true),
                tap(viewId = "com.example.app:id/drawer_layout"),
            ),
        )
        assertEquals(setOf(0, 1), report.byStep().keys)
    }
}
