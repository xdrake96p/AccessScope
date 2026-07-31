package dev.accessscope.scanner.analyzer.title

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

internal object TitleTopBarWalker {
        fun findTopBarTitle(root: AccessibilityNodeInfo): String? {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val className = node.className?.toString().orEmpty()
                val viewId = node.viewIdResourceName.orEmpty().lowercase()
                if (isBottomNavigationNode(viewId, className)) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                    continue
                }
                val isBar = className.contains("Toolbar", true) ||
                    className.contains("ActionBar", true) ||
                    className.contains("AppBar", true) ||
                    className.contains("CollapsingToolbar", true) ||
                    viewId.contains("toolbar", true) ||
                    viewId.contains("action_bar", true) ||
                    viewId.contains("topbar", true)
    
                if (isBar || viewId.endsWith("/topbar_title") || viewId.endsWith("/toolbar_title")) {
                    val titleText = findTitleTextInBar(node)
                    if (!titleText.isNullOrBlank() && !TitleCandidateLogic.looksLikeAmount(titleText)) {
                        return TitleCandidateLogic.humanizeTitle(titleText)
                    }
                }
    
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
            }
            return null
        }
    
        /** Esclude bottom navigation e tab bar dal candidato toolbar (etichette tab fuorvianti). */
        private fun isBottomNavigationNode(viewId: String, className: String): Boolean =
            viewId.contains("bottom_nav") ||
                viewId.contains("bottomnavigation") ||
                viewId.contains("navigation_bar") ||
                viewId.startsWith("nav_") ||
                className.contains("BottomNavigationView", true) ||
                className.contains("NavigationBarView", true)
    
        /**
         * Cerca il testo del titolo all'interno di un nodo barra (toolbar/topbar).
         *
         * Priorità: viewId `topbar_title` / `toolbar_title`, nodi heading (API 28+), testo del nodo barra.
         *
         * @param barNode Nodo che rappresenta la barra superiore.
         * @return Testo del titolo se trovato, altrimenti `null`.
         */
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
}
