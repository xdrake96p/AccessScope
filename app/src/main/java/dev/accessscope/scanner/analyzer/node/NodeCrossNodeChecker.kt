package dev.accessscope.scanner.analyzer.node

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.analyzer.ViolationConfidencePolicy
import dev.accessscope.scanner.analyzer.precision.area
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType

internal object NodeCrossNodeChecker {

    fun checkCrossNodeIssues(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenWidth: Int,
        minTouchSpacingPx: Int,
        screenFingerprint: String?,
    ) {
        val maxBottom = snapshots.maxOfOrNull { it.bounds.bottom } ?: 0
        val screenArea = if (screenWidth > 0 && maxBottom > 0) screenWidth * maxBottom else 0
        val isMaterialCalendar = PrecisionRules.isMaterialCalendarContext(screenTitle, snapshots)
        val clickables = snapshots
            .filter { PrecisionRules.isSemanticClickTarget(it) }
            .filterNot { PrecisionRules.isObscuredByModalOverlay(it, snapshots) }
            .filterNot { snap -> isMaterialCalendar && PrecisionRules.isMaterialCalendarDayCell(snap, screenTitle, snapshots) }

        snapshots.mapNotNull { snap -> snap.accessibleName()?.lowercase()?.let { it to snap } }
            .groupBy({ it.first }, { it.second })
            .forEach { (name, nodes) ->
                if (name.isBlank() || nodes.size < 2) return@forEach
                if (nodes.all { PrecisionRules.isInsideWebView(it, snapshots) }) return@forEach
                val distinctBounds = nodes.map { it.bounds }.distinctBy { "${it.left},${it.top},${it.right},${it.bottom}" }
                if (distinctBounds.size < nodes.size) {
                    nodes.forEach { snap ->
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.DUPLICATE_ACCESSIBLE_NAME, snap, packageName, screenTitle,
                            "Nome \"$name\" duplicato su elementi distinti.", 0.9f,
                        )
                    }
                }
            }

        for (i in clickables.indices) {
            for (j in i + 1 until clickables.size) {
                val a = clickables[i]
                val b = clickables[j]
                if (PrecisionRules.shouldSkipDrawerNode(a) || PrecisionRules.shouldSkipDrawerNode(b)) continue
                if (PrecisionRules.shouldSkipOverlapBetween(a, b, snapshots, packageName, screenWidth)) continue
                if (PrecisionRules.shouldSkipPinPadWhenNotPinScreen(a, screenTitle, packageName) ||
                    PrecisionRules.shouldSkipPinPadWhenNotPinScreen(b, screenTitle, packageName)
                ) {
                    continue
                }
                if (a.bounds.contains(b.bounds) || b.bounds.contains(a.bounds)) continue
                if (Rect.intersects(a.bounds, b.bounds)) {
                    val overlap = overlapArea(a.bounds, b.bounds)
                    val minArea = minOf(a.area(), b.area())
                    if (overlap > minArea * 0.45) {
                        val confidence = ViolationConfidencePolicy.overlappingTouchConfidence(a, overlap)
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.OVERLAPPING_TOUCH_TARGETS, a, packageName, screenTitle,
                            "Sovrapposizione ${overlap}px² con ${b.className}.", confidence,
                        )
                    }
                } else {
                    val distance = edgeDistance(a.bounds, b.bounds)
                    if (distance in 1 until minTouchSpacingPx &&
                        !PrecisionRules.shouldSkipTouchSpacingBetween(a, b, snapshots, screenArea)
                    ) {
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.INSUFFICIENT_TOUCH_SPACING, a, packageName, screenTitle,
                            "Solo ${distance}px da un altro pulsante.", 0.85f,
                        )
                    }
                }
            }
        }

        snapshots.forEach { parent ->
            if (!parent.hasAccessibleName() || parent.isInteractiveClickable()) return@forEach
            snapshots.forEach { child ->
                if (parent == child || !parent.bounds.contains(child.bounds)) return@forEach
                if (parent.accessibleName() == child.accessibleName() && child.hasAccessibleName() && !child.isInteractiveClickable()) {
                    violations += ViolationBuilder.v(
                        screenFingerprint,
                        ViolationType.REDUNDANT_ACCESSIBLE_NAME, child, packageName, screenTitle,
                        "Nome ripetuto dal contenitore.", 0.75f,
                    )
                }
            }
        }
    }

    private fun overlapArea(a: Rect, b: Rect): Int {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (left < right && top < bottom) (right - left) * (bottom - top) else 0
    }

    private fun edgeDistance(a: Rect, b: Rect): Int {
        val dx = when {
            a.right < b.left -> b.left - a.right
            b.right < a.left -> a.left - b.right
            else -> 0
        }
        val dy = when {
            a.bottom < b.top -> b.top - a.bottom
            b.bottom < a.top -> a.top - b.bottom
            else -> 0
        }
        return maxOf(dx, dy)
    }
}
