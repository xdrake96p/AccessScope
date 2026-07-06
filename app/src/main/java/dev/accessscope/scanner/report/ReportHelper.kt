package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.PassedCheck
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.data.ViolationType
import kotlin.math.roundToInt

data class ReportSectionGroup(
    val screenTitle: String,
    val sectionTitle: String,
) {
    val hasSubsection: Boolean get() = sectionTitle != screenTitle
}

object ReportHelper {
    const val MIN_CONFIDENCE = 0.65f

    val SEVERITY_ORDER = listOf(
        ViolationSeverity.CRITICAL,
        ViolationSeverity.SERIOUS,
        ViolationSeverity.MODERATE,
        ViolationSeverity.MINOR,
    )

    fun confidenceThreshold(type: ViolationType): Float = when (type) {
        ViolationType.LOW_COLOR_CONTRAST,
        ViolationType.LOW_NON_TEXT_CONTRAST,
        -> 0.72f
        ViolationType.HEADING_HIERARCHY,
        ViolationType.TEXT_TRUNCATED,
        ViolationType.DUPLICATE_VIEW_ID,
        -> 0.80f
        ViolationType.REQUIRED_FIELD_UNMARKED,
        ViolationType.INPUT_ERROR_MISSING,
        -> 0.75f
        else -> MIN_CONFIDENCE
    }

    fun filterViolations(violations: List<AccessibilityViolation>): List<AccessibilityViolation> =
        violations.filter { it.confidence >= confidenceThreshold(it.type) }

    fun computeScore(violations: List<AccessibilityViolation>, screens: Int): Int {
        if (screens == 0) return 100
        val filtered = filterViolations(violations)
        val weighted = filtered.sumOf { severityWeight(it.type.severity) }
        return (100 - weighted / screens * 6).roundToInt().coerceIn(0, 100)
    }

    /** @deprecated use computeScore(violations, screens) */
    fun computeScore(issues: Int, screens: Int): Int {
        if (screens == 0) return 100
        return (100 - issues.toFloat() / screens * 8).roundToInt().coerceIn(0, 100)
    }

    private fun severityWeight(severity: ViolationSeverity): Double = when (severity) {
        ViolationSeverity.CRITICAL -> 4.0
        ViolationSeverity.SERIOUS -> 2.0
        ViolationSeverity.MODERATE -> 1.0
        ViolationSeverity.MINOR -> 0.5
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

    data class ScreenOverviewEntry(
        val screenTitle: String,
        val violationCount: Int,
        val passedCount: Int,
    )

    fun screenOverview(
        violations: List<AccessibilityViolation>,
        summaries: List<CheckAreaSummary>,
    ): List<ScreenOverviewEntry> {
        val violationTotals = screenTotals(violations)
        val passedByScreen = summaries.groupBy { it.screenTitle }
            .mapValues { (_, items) -> items.sumOf { it.passedCount } }
        return (violationTotals.keys + passedByScreen.keys)
            .sorted()
            .map { screen ->
                ScreenOverviewEntry(
                    screenTitle = screen,
                    violationCount = violationTotals[screen] ?: 0,
                    passedCount = passedByScreen[screen] ?: 0,
                )
            }
    }

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

    fun totalPassedChecks(summaries: List<CheckAreaSummary>): Int =
        summaries.sumOf { it.passedCount }

    fun groupChecksByScreen(summaries: List<CheckAreaSummary>): Map<String, List<CheckAreaSummary>> =
        summaries.groupBy { it.screenTitle }.mapValues { (_, items) ->
            items.sortedBy { it.area.ordinal }
        }

    fun checksForScreen(summaries: List<CheckAreaSummary>, screenTitle: String): List<CheckAreaSummary> =
        summaries.filter { it.screenTitle == screenTitle }

    fun globalCheckCoverage(
        summaries: List<CheckAreaSummary>,
        violations: List<AccessibilityViolation>,
    ): List<Pair<ViolationArea, Pair<Int, Int>>> {
        val passedByArea = summaries.groupBy { it.area }.mapValues { (_, items) -> items.sumOf { it.passedCount } }
        val failedByArea = violations.groupingBy { it.area }.eachCount()
        return ViolationArea.entries.mapNotNull { area ->
            val passed = passedByArea[area] ?: 0
            val failed = failedByArea[area] ?: 0
            if (passed == 0 && failed == 0) null else area to (passed to failed)
        }
    }

    fun violationDetailLines(v: AccessibilityViolation): List<String> = buildList {
        add("Dettaglio: ${v.details}")
        v.elementLabel?.takeIf { it.isNotBlank() }?.let { add("Elemento: \"$it\"") }
        if (!v.measuredValue.isNullOrBlank() || !v.requiredValue.isNullOrBlank()) {
            add("Misura: ${v.measuredValue ?: "—"} · Richiesto: ${v.requiredValue ?: "—"}")
        }
        add("WCAG: ${v.wcagReference}")
        v.remediation?.let { add("Suggerimento: $it") }
        val meta = buildList {
            add(v.viewClassName.substringAfterLast('.'))
            v.viewId?.substringAfterLast('/')?.let { add("@id/$it") }
            v.bounds?.let { add(it) }
        }.joinToString(" · ")
        if (meta.isNotBlank()) add("Posizione: $meta")
    }

    fun passedCheckLine(check: PassedCheck): String = buildString {
        append("✓ ${check.checkLabel}")
        if (check.elementSummary.isNotBlank()) append(": ${check.elementSummary}")
        check.viewId?.substringAfterLast('/')?.let { append(" (@id/$it)") }
    }
}
