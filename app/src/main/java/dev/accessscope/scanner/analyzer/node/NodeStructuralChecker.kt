package dev.accessscope.scanner.analyzer.node

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.PrecisionRules
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import java.util.ArrayDeque
import kotlin.math.abs

internal object NodeStructuralChecker {

    fun checkWebViews(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenFingerprint: String?,
    ) {
        snapshots.filter { PrecisionRules.shouldReportWebViewBarrier(it, snapshots) }.forEach { snap ->
            violations += ViolationBuilder.v(
                screenFingerprint,
                ViolationType.WEBVIEW_BARRIER, snap, packageName, screenTitle,
                "WebView senza contenuto accessibile esposto (${snap.bounds.width()}×${snap.bounds.height()} px).", 0.9f,
            )
        }
    }

    fun checkTables(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
    ) {
        val gridItems = snapshots.filter { it.collectionRow >= 0 && it.collectionColumn >= 0 }
        if (gridItems.size < 6) return
        val rows = gridItems.map { it.collectionRow }.distinct().size
        val cols = gridItems.map { it.collectionColumn }.distinct().size
        if (rows < 2 || cols < 2) return
        val hasHeader = gridItems.any { it.isHeading || it.collectionRow == 0 || it.collectionColumn == 0 }
        if (!hasHeader) {
            violations += AccessibilityViolation(
                type = ViolationType.TABLE_HEADER_MISSING,
                viewClassName = "Collection/Grid",
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Griglia ${rows}×${cols} senza intestazioni marcate.",
                confidence = 0.8f,
            )
        }
    }

    fun checkDuplicateViewIds(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenFingerprint: String?,
    ) {
        val screenArea = snapshots.maxOfOrNull { it.bounds.right * it.bounds.bottom } ?: 0
        snapshots.mapNotNull { snap -> snap.viewId?.let { it to snap } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .forEach { (id, nodes) ->
                if (isListItemTemplate(id, nodes, packageName)) return@forEach
                if (screenArea > 0 && nodes.all { PrecisionRules.isInsideDenseScrollGrid(it, snapshots, screenArea) }) {
                    return@forEach
                }
                val representative = nodes.minByOrNull { it.traversalIndex } ?: return@forEach
                violations += ViolationBuilder.v(
                    screenFingerprint,
                    ViolationType.DUPLICATE_VIEW_ID,
                    representative,
                    packageName,
                    screenTitle,
                    "ID $id condiviso da ${nodes.size} elementi (anomalo, non template lista).",
                    0.95f,
                )
            }
    }

    fun checkDuplicateLinks(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
        screenFingerprint: String?,
    ) {
        snapshots.filter { it.isLikelyLink() && it.hasAccessibleName() }
            .groupBy { it.accessibleName()!!.lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (text, links) ->
                val distinctIds = links.mapNotNull { it.viewId }.distinct()
                if (distinctIds.size > 1 || links.map { it.bounds }.distinct().size > 1) {
                    links.forEach { snap ->
                        violations += ViolationBuilder.v(
                            screenFingerprint,
                            ViolationType.DUPLICATE_LINK_TEXT, snap, packageName, screenTitle,
                            "Link \"$text\" ripetuto con destinazioni probabilmente diverse.", 0.82f,
                        )
                    }
                }
            }
    }

    fun checkModalTitle(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
    ) {
        val className = root.className?.toString().orEmpty()
        val isModal = listOf("Dialog", "BottomSheet", "Popup", "AlertDialog").any { className.contains(it, true) }
        if (isModal && (screenTitle == "Schermata" || screenTitle.isBlank())) {
            violations += AccessibilityViolation(
                type = ViolationType.MODAL_WITHOUT_TITLE,
                viewClassName = className,
                screenTitle = screenTitle,
                packageName = packageName,
                details = "Modale senza titolo accessibile.",
                confidence = 0.9f,
            )
        }
    }

    fun checkCollectionStructure(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        violations: MutableList<AccessibilityViolation>,
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val collection = node.collectionInfo
            if (collection != null && node.childCount > 4) {
                val hasStructure = collection.rowCount > 0 || collection.columnCount > 0
                if (!hasStructure) {
                    violations += AccessibilityViolation(
                        type = ViolationType.COLLECTION_WITHOUT_STRUCTURE,
                        viewClassName = node.className?.toString() ?: "unknown",
                        screenTitle = screenTitle,
                        packageName = packageName,
                        details = "Lista con ${node.childCount} elementi senza struttura esposta.",
                        viewId = node.viewIdResourceName,
                        confidence = 0.85f,
                    )
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
    }

    private fun isListItemTemplate(viewId: String, nodes: List<NodeSnapshot>, packageName: String): Boolean {
        if (nodes.size < 2) return false
        if (viewId.substringAfterLast('/').equals("material_calendar_day", ignoreCase = true) && nodes.size >= 12) {
            return true
        }
        if (PrecisionRules.isKnownListTemplateId(viewId, packageName)) return true
        val sameClass = nodes.map { it.className }.distinct().size == 1
        if (!sameClass) return false
        val heights = nodes.map { it.bounds.height() }
        val avg = heights.average()
        if (heights.all { abs(it - avg) <= avg * 0.15 + 2 }) return true
        val widths = nodes.map { it.bounds.width() }
        val widthAvg = widths.average()
        val widthSimilar = widths.all { abs(it - widthAvg) <= widthAvg * 0.12 + 4 }
        val distinctTops = nodes.map { it.bounds.top }.distinct().size >= 2
        return widthSimilar && distinctTops
    }
}
