package dev.accessscope.scanner.analyzer

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.data.AccessibilityViolation

/**
 * Simula il percorso di navigazione di TalkBack attraversando i nodi focalizzabili
 * e verificando cosa verrebbe annunciato. Android non consente alle app terze di
 * attivare TalkBack programmaticamente per motivi di sicurezza.
 */
class TalkBackSimulator {

    fun simulate(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
    ): List<ScreenReaderFinding> {
        val findings = mutableListOf<ScreenReaderFinding>()
        val focusableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, focusableNodes)

        var silentCount = 0
        focusableNodes.forEach { node ->
            val announced = buildAnnouncement(node)
            val className = node.className?.toString() ?: "unknown"
            val viewId = node.viewIdResourceName

            if (announced.isNullOrBlank()) {
                silentCount++
                findings += ScreenReaderFinding(
                    packageName = packageName,
                    screenTitle = screenTitle,
                    nodeClassName = className,
                    announcedText = null,
                    issue = "TalkBack non avrebbe testo da annunciare su questo elemento.",
                    viewId = viewId,
                )
            } else if (announced.length < 2) {
                findings += ScreenReaderFinding(
                    packageName = packageName,
                    screenTitle = screenTitle,
                    nodeClassName = className,
                    announcedText = announced,
                    issue = "Annuncio screen reader troppo breve o poco descrittivo.",
                    viewId = viewId,
                )
            }
        }

        if (focusableNodes.isEmpty()) {
            findings += ScreenReaderFinding(
                packageName = packageName,
                screenTitle = screenTitle,
                nodeClassName = "—",
                announcedText = null,
                issue = "Nessun elemento focalizzabile: la schermata sarebbe quasi innavigabile con TalkBack.",
            )
        } else if (silentCount > focusableNodes.size / 2) {
            findings += ScreenReaderFinding(
                packageName = packageName,
                screenTitle = screenTitle,
                nodeClassName = "—",
                announcedText = "$silentCount / ${focusableNodes.size} elementi",
                issue = "Oltre il 50% degli elementi focalizzabili non ha un annuncio utile.",
            )
        }

        focusableNodes.forEach { it.recycle() }
        return findings
    }

    private fun collectFocusableNodes(
        node: AccessibilityNodeInfo,
        output: MutableList<AccessibilityNodeInfo>,
    ) {
        if (!node.isVisibleToUser) {
            recycleChildren(node)
            return
        }

        val isFocusCandidate = node.isFocusable ||
            node.isClickable ||
            node.isCheckable ||
            node.isEditable ||
            node.isScrollable

        if (isFocusCandidate) {
            output += AccessibilityNodeInfo.obtain(node)
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectFocusableNodes(child, output)
            child.recycle()
        }
    }

    private fun recycleChildren(node: AccessibilityNodeInfo) {
        for (i in 0 until node.childCount) {
            node.getChild(i)?.recycle()
        }
    }

    private fun buildAnnouncement(node: AccessibilityNodeInfo): String? {
        val parts = mutableListOf<String>()

        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
        node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)

        if (node.isCheckable) {
            parts += if (node.isChecked) "selezionato" else "non selezionato"
        }

        val className = node.className?.toString().orEmpty()
        if (parts.isEmpty()) {
            val role = when {
                className.contains("Button", ignoreCase = true) -> "pulsante"
                className.contains("EditText", ignoreCase = true) -> "campo di testo"
                className.contains("Image", ignoreCase = true) -> "immagine"
                else -> null
            }
            role?.let(parts::add)
        }

        return parts.distinct().joinToString(", ").ifBlank { null }
    }
}

fun TalkBackSimulator.toViolations(
    findings: List<ScreenReaderFinding>,
): List<AccessibilityViolation> =
    findings.map { finding ->
        AccessibilityViolation(
            type = ViolationType.SCREEN_READER_ANNOUNCEMENT,
            viewClassName = finding.nodeClassName,
            screenTitle = finding.screenTitle,
            packageName = finding.packageName,
            details = buildString {
                append(finding.issue)
                finding.announcedText?.let { append(" Annuncio simulato: \"$it\".") }
            },
            viewId = finding.viewId,
        )
    }
