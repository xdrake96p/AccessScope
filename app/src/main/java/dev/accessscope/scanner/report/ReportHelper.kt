package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationSeverity
import kotlin.math.roundToInt

data class ReportSectionGroup(
    val screenTitle: String,
    val sectionTitle: String,
) {
    val hasSubsection: Boolean get() = sectionTitle != screenTitle
}

object ReportHelper {
    const val MIN_CONFIDENCE = 0.60f

    val SEVERITY_ORDER = listOf(
        ViolationSeverity.CRITICAL,
        ViolationSeverity.SERIOUS,
        ViolationSeverity.MODERATE,
        ViolationSeverity.MINOR,
    )

    fun filterViolations(violations: List<AccessibilityViolation>): List<AccessibilityViolation> =
        violations.filter { it.confidence >= MIN_CONFIDENCE }

    fun computeScore(issues: Int, screens: Int): Int {
        if (screens == 0) return 100
        val density = issues.toFloat() / screens
        return (100 - density * 8).roundToInt().coerceIn(0, 100)
    }

    fun scoreLabel(score: Int): String = when {
        score >= 85 -> "Ottimo"
        score >= 70 -> "Buono"
        score >= 50 -> "Da migliorare"
        else -> "Critico"
    }

    fun sectionKey(violation: AccessibilityViolation): ReportSectionGroup =
        ReportSectionGroup(
            screenTitle = violation.screenTitle,
            sectionTitle = violation.reportSection,
        )

    fun sectionKey(finding: ScreenReaderFinding): ReportSectionGroup =
        ReportSectionGroup(
            screenTitle = finding.screenTitle,
            sectionTitle = finding.reportSection,
        )

    fun sortBySeverity(violations: List<AccessibilityViolation>): List<AccessibilityViolation> =
        violations.sortedWith(
            compareBy(
                { SEVERITY_ORDER.indexOf(it.type.severity) },
                { it.type.displayName },
            ),
        )

    fun groupViolationsBySection(
        violations: List<AccessibilityViolation>,
    ): List<Pair<ReportSectionGroup, List<AccessibilityViolation>>> =
        violations
            .groupBy(::sectionKey)
            .toList()
            .sortedWith(compareBy({ it.first.screenTitle }, { it.first.sectionTitle }))
            .map { (key, items) -> key to sortBySeverity(items) }

    fun groupTalkBackBySection(
        findings: List<ScreenReaderFinding>,
    ): Map<ReportSectionGroup, List<ScreenReaderFinding>> =
        findings.groupBy(::sectionKey)

    fun severityEmoji(severity: ViolationSeverity): String = when (severity) {
        ViolationSeverity.CRITICAL -> "🔴"
        ViolationSeverity.SERIOUS -> "🟠"
        ViolationSeverity.MODERATE -> "🟡"
        ViolationSeverity.MINOR -> "⚪"
    }

    fun severityGroupTitle(severity: ViolationSeverity): String = when (severity) {
        ViolationSeverity.CRITICAL -> "Critiche"
        ViolationSeverity.SERIOUS -> "Gravi"
        ViolationSeverity.MODERATE -> "Medie"
        ViolationSeverity.MINOR -> "Lievi"
    }

    fun screenTotals(violations: List<AccessibilityViolation>): Map<String, Int> =
        violations.groupingBy { it.screenTitle }.eachCount()

    fun areaTotals(
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
    ): Map<ViolationArea, Int> = ViolationArea.entries.associateWith { area ->
        val count = violations.count { it.area == area }
        val talkBack = if (area == ViolationArea.SCREEN_READER) screenReaderFindings.size else 0
        count + talkBack
    }.filterValues { it > 0 }

    fun areasWithIssues(violations: List<AccessibilityViolation>, talkBackCount: Int): Int {
        val areas = violations.map { it.area }.toMutableSet()
        if (talkBackCount > 0) areas.add(ViolationArea.SCREEN_READER)
        return areas.size
    }

    fun cleanAreaCount(violations: List<AccessibilityViolation>, talkBackCount: Int): Int =
        ViolationArea.entries.size - areasWithIssues(violations, talkBackCount)
}
