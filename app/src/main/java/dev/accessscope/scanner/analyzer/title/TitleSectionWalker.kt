package dev.accessscope.scanner.analyzer.title

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

internal object TitleSectionWalker {
        fun findSectionTitle(root: AccessibilityNodeInfo): String? {
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
                    !TitleCandidateLogic.looksLikeAmount(text) && bounds.top <= sectionBandBottom
                ) {
                    return TitleCandidateLogic.humanizeTitle(text)
                }
    
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
            }
            return null
        }
    
        /**
         * Estrae il titolo da dialog, bottom sheet o altri contenitori modali.
         *
         * @param root Nodo radice; deve avere className indicativa di modale per essere considerato.
         * @return Titolo umanizzato del modale, oppure `null` se non modale o senza titolo.
         */
        fun findModalTitle(root: AccessibilityNodeInfo): String? {
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
                    text.isNotBlank() && text.length <= 80 && !TitleCandidateLogic.looksLikeAmount(text)
                ) {
                    return TitleCandidateLogic.humanizeTitle(text)
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
            }
            return null
        }
    
        /**
         * Seleziona l'heading più prominente nella porzione superiore dello schermo (~28%).
         *
         * Esclude `topbar_title` (già gestito altrove), importi e titoli del widget insoluti in home.
         * Preferisce l'heading con maggiore altezza visiva come punteggio.
         *
         * @param root Nodo radice del sottoalbero.
         * @return Titolo umanizzato dell'heading migliore, oppure `null`.
         */
        fun findProminentHeading(root: AccessibilityNodeInfo): String? {
            val screenBounds = android.graphics.Rect()
            root.getBoundsInScreen(screenBounds)
            val topThreshold = screenBounds.top + (screenBounds.height() * 0.28f).toInt()
            val onHome = NexiTitleHeuristics.hasHomeMarkers(TitleTreeWalker.collectViewIdShorts(root))
    
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
                if (text.isBlank() || TitleCandidateLogic.looksLikeAmount(text)) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                    continue
                }
                if (NexiTitleHeuristics.isHomeInsolutiWidgetTitle(text, viewId, onHome)) {
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
                        best = TitleCandidateLogic.humanizeTitle(text) to score
                    }
                }
    
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
            }
            return best?.first
        }
}
