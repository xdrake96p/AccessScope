package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import kotlin.math.abs

object FocusOrderAnalyzer {

    fun analyze(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
    ): List<AccessibilityViolation> {
        val focusable = snapshots.filter {
            it.isFocusable || it.isInteractiveClickable() || it.isEditable || it.isCheckable
        }
        if (focusable.size < 3) return emptyList()

        val visualOrder = focusable.sortedBy { it.visualSortKey() }
        val traversalOrder = focusable.sortedBy { it.traversalIndex }

        val inversions = countInversions(
            traversalOrder.map { visualOrder.indexOf(it) },
        )
        val maxInversions = focusable.size * (focusable.size - 1) / 2
        val inversionRate = if (maxInversions > 0) inversions.toFloat() / maxInversions else 0f

        if (inversionRate < 0.35f) return emptyList()

        val confidence = (0.6f + inversionRate * 0.35f).coerceAtMost(0.95f)
        return listOf(
            AccessibilityViolation(
                type = ViolationType.ILLOGICAL_FOCUS_ORDER,
                viewClassName = "Schermata",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Ordine TalkBack (${traversalOrder.take(5).joinToString { it.accessibleName() ?: "?" }}) " +
                    "non segue l'ordine visivo (${visualOrder.take(5).joinToString { it.accessibleName() ?: "?" }}). " +
                    "Inversioni: $inversions/$maxInversions.",
                confidence = confidence,
            ),
        )
    }

    fun analyzeHeadingLevels(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
    ): List<AccessibilityViolation> {
        val headings = snapshots
            .filter {
                (it.isHeading || it.looksLikeStructuralHeading()) &&
                    !PrecisionRules.shouldSkipHeadingCheck(it)
            }
            .map { snap ->
                val level = if (snap.headingLevel > 0) snap.headingLevel
                else estimateFromBounds(snap.bounds.height())
                snap to level
            }
            .sortedBy { it.first.visualSortKey() }

        val violations = mutableListOf<AccessibilityViolation>()
        for (i in 1 until headings.size) {
            val prev = headings[i - 1].second
            val curr = headings[i].second
            if (curr - prev > 1) {
                val snap = headings[i].first
                violations += AccessibilityViolation(
                    type = ViolationType.HEADING_LEVEL_SKIP,
                    viewClassName = snap.className,
                    screenTitle = screenTitle,
                    packageName = packageName,
                    details = "Salto da livello ~H$prev a ~H$curr su \"${snap.accessibleName() ?: snap.text}\".",
                    viewId = snap.viewId,
                    bounds = snap.boundsLabel(),
                    sectionTitle = snap.sectionTitle,
                    confidence = 0.8f,
                )
            }
        }
        return violations
    }

    private fun estimateFromBounds(height: Int): Int = when {
        height >= 72 -> 1
        height >= 56 -> 2
        height >= 44 -> 3
        height >= 36 -> 4
        else -> 5
    }

    private fun countInversions(sequence: List<Int>): Int {
        var count = 0
        for (i in sequence.indices) {
            for (j in i + 1 until sequence.size) {
                if (sequence[i] > sequence[j]) count++
            }
        }
        return count
    }
}
