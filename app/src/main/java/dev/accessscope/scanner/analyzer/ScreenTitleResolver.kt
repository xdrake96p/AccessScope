package dev.accessscope.scanner.analyzer

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object ScreenTitleResolver {

    private val KNOWN_NEXI_SECTION_TITLES = setOf(
        "DISTINTE",
        "BONIFICI",
        "EFFETTI IN SCADENZA",
        "EFFETTI",
        "NUOVO PAGAMENTO",
        "DISPOSIZIONI ONLINE",
        "DISPOSIZIONI ISTANTANEE",
        "DISPOSIZIONI",
        "RUBRICA",
        "AUTORIZZA DISTINTE",
        "PAGA EFFETTI",
        "INSOLUTI",
        "ARCHIVIO DISTINTE",
        "ARCHIVIO EFFETTI",
        "COMUNICAZIONI AZIENDALI",
        "NOTIFICHE",
        "IMPOSTAZIONI",
        "AIUTO E CONTATTI",
    )

    fun resolve(root: AccessibilityNodeInfo, event: AccessibilityEvent): String {
        event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (!looksLikeAmount(it)) return humanizeTitle(it)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root.paneTitle?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
                return humanizeTitle(it)
            }
        }

        findPinScreen(root)?.let { return it }
        findModalTitle(root)?.let { return it }
        findSectionTitle(root)?.let { return it }
        findKnownNexiTitles(root)?.let { return it }
        findTopBarTitle(root)?.let { return it }
        findProminentHeading(root)?.let { return it }

        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (!looksLikeAmount(it)) return humanizeTitle(it)
        }

        val activityName = event.className?.toString()?.substringAfterLast('.').orEmpty()
        if (activityName.isNotBlank()) return humanizeActivityName(activityName)

        return "Schermata"
    }

    /** Public helper for multi-window prioritization. */
    fun isPinScreen(root: AccessibilityNodeInfo): Boolean = findPinScreen(root) != null

    private fun findPinScreen(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var hasPinPad = false
        var hasNumericKey = false
        var hasDeleteKey = false
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString()?.trim().orEmpty()
            if (id.contains("pin_pad") || id.contains("pinpad") || id.contains("pin_pad_view") ||
                id.endsWith("/background_pin")
            ) {
                hasPinPad = true
            }
            if (id.endsWith("/uno") || id.endsWith("/due") || id.endsWith("/tre")) {
                hasNumericKey = true
            }
            if (id.endsWith("/cancell") || id.endsWith("/zero")) {
                hasDeleteKey = true
            }
            if (text.contains("inserisci", true) && text.contains("pin", true) ||
                text.contains("inserisci pin", true) ||
                id.contains("caption_pin") || id.contains("caption_otp")
            ) {
                return "Inserisci PIN"
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        if (hasPinPad || (hasNumericKey && hasDeleteKey)) return "Inserisci PIN"
        return null
    }

    private fun findTopBarTitle(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val className = node.className?.toString().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val isBar = className.contains("Toolbar", true) ||
                className.contains("ActionBar", true) ||
                className.contains("AppBar", true) ||
                className.contains("CollapsingToolbar", true) ||
                viewId.contains("toolbar", true) ||
                viewId.contains("action_bar", true) ||
                viewId.contains("topbar", true)

            if (isBar || viewId.endsWith("/topbar_title") || viewId.endsWith("/toolbar_title")) {
                val titleText = findTitleTextInBar(node)
                if (!titleText.isNullOrBlank() && !looksLikeAmount(titleText) &&
                    !isKnownSectionTitle(titleText)
                ) {
                    return humanizeTitle(titleText)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun findTitleTextInBar(barNode: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(barNode)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString()?.trim().orEmpty()
            if ((viewId.endsWith("/topbar_title") || viewId.endsWith("/toolbar_title")) &&
                text.isNotBlank()
            ) {
                return text
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isHeading && text.isNotBlank()) {
                return text
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        barNode.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun findSectionTitle(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val sectionBandBottom = screenBounds.top + (screenBounds.height() * 0.22f).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString()?.trim().orEmpty()
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)

            if (viewId.endsWith("/title") && text.isNotBlank() && text.length <= 80 &&
                !looksLikeAmount(text) && bounds.top <= sectionBandBottom
            ) {
                return humanizeTitle(text)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun findKnownNexiTitles(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val sectionBandBottom = screenBounds.top + (screenBounds.height() * 0.25f).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var contentPagamentoTitle: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString()?.trim().orEmpty()
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val normalized = text.uppercase()

            if (viewId.endsWith("/content_pagamento") && text.isNotBlank() && bounds.top <= sectionBandBottom) {
                contentPagamentoTitle = humanizeTitle(text)
            }

            if (text.isNotBlank() && bounds.top <= sectionBandBottom && text.length <= 80 &&
                !looksLikeAmount(text)
            ) {
                if (isKnownSectionTitle(text)) {
                    return humanizeTitle(text)
                }
                KNOWN_NEXI_SECTION_TITLES.firstOrNull { known ->
                    normalized.contains(known) || known.contains(normalized)
                }?.let { return humanizeTitle(it) }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        if (contentPagamentoTitle != null) return contentPagamentoTitle

        return findTitleNearContentPagamento(root)
    }

    private fun findTitleNearContentPagamento(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var foundPagamento = false
        val candidates = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            if (viewId.endsWith("/content_pagamento")) {
                foundPagamento = true
            }
            val text = node.text?.toString()?.trim().orEmpty()
            if (foundPagamento && text.isNotBlank() && text.length <= 80 && !looksLikeAmount(text)) {
                val normalized = text.uppercase()
                if (normalized.contains("PAGAMENTO") || normalized.contains("NUOVO")) {
                    candidates.add(humanizeTitle(text))
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return candidates.firstOrNull()
    }

    private fun isKnownSectionTitle(text: String): Boolean {
        val normalized = text.trim().uppercase()
        return KNOWN_NEXI_SECTION_TITLES.any { known ->
            normalized == known || normalized.contains(known)
        }
    }

    private fun findModalTitle(root: AccessibilityNodeInfo): String? {
        val className = root.className?.toString().orEmpty()
        val isModal = listOf("Dialog", "BottomSheet", "Popup", "AlertDialog", "Modal")
            .any { className.contains(it, true) }
        if (!isModal) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val isHeading = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.collectionItemInfo?.isHeading == true || node.isHeading
            } else {
                false
            }
            val text = node.text?.toString()?.trim().orEmpty()
            if ((isHeading || node.className?.toString().orEmpty().contains("Title", true)) &&
                text.isNotBlank() && text.length <= 80 && !looksLikeAmount(text)
            ) {
                return humanizeTitle(text)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun findProminentHeading(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val topThreshold = screenBounds.top + (screenBounds.height() * 0.28f).toInt()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: Pair<String, Int>? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            if (viewId.endsWith("/topbar_title")) {
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                continue
            }
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isBlank() || looksLikeAmount(text)) {
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                continue
            }
            val isHeading = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.isHeading || node.collectionItemInfo?.isHeading == true
            } else {
                false
            }
            val looksLikeTitle = isHeading ||
                (node.className?.toString().orEmpty().contains("TextView", true) &&
                    !node.isClickable && text.length <= 60)

            if (looksLikeTitle && bounds.top <= topThreshold) {
                val score = bounds.height()
                if (best == null || score > best!!.second) {
                    best = humanizeTitle(text) to score
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return best?.first
    }

    private fun looksLikeAmount(text: String): Boolean = PrecisionRules.isCurrencyOrAmountText(text)

    private fun humanizeActivityName(name: String): String {
        val cleaned = name
            .removeSuffix("Activity")
            .removeSuffix("Fragment")
            .removeSuffix("Screen")
            .removeSuffix("Page")
        return humanizeTitle(
            cleaned.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").trim().ifBlank { name },
        )
    }

    private fun humanizeTitle(title: String): String =
        title.trim().replace(Regex("\\s+"), " ")
}
