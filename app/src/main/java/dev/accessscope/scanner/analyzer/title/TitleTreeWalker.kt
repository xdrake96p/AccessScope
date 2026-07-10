package dev.accessscope.scanner.analyzer.title

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

internal object TitleTreeWalker {

    /**
     * Attraversa il sottoalbero e raccoglie i suffissi corti di tutti i viewId presenti.
     *
     * @param root Nodo radice da visitare in ampiezza.
     * @return Insieme immutabile dei suffissi viewId (parte dopo `/`), in minuscolo.
     */
    fun collectViewIdShorts(root: AccessibilityNodeInfo): Set<String> {
        val ids = mutableSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.viewIdResourceName?.substringAfterLast('/')?.lowercase()?.let { ids.add(it) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return ids
    }

    /** True se l'albero contiene contenuto scrollabile (lista, scroll view). */
    fun hasScrollableContent(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("ScrollView", true) ||
                cls.contains("RecyclerView", true) ||
                cls.contains("NestedScroll", true) ||
                cls.contains("ViewPager", true)
            ) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return false
    }

    /**
     * Cerca nel sottoalbero indicatori di schermata PIN (tastierino numerico, testi, viewId).
     *
     * @param root Nodo radice da attraversare in ampiezza.
     * @return `"Inserisci PIN"` se rilevata una schermata PIN, altrimenti `null`.
     */
    fun findPinScreen(root: AccessibilityNodeInfo): String? {
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

    /**
     * Estrae il titolo da barre superiori (Toolbar, ActionBar, AppBar, topbar).
     *
     * Ignora testi che sembrano importi o titoli di sezione già noti, per evitare duplicati
     * o etichette fuorvianti.
     *
     * @param root Nodo radice del sottoalbero da analizzare.
     * @return Titolo umanizzato trovato nella top bar, oppure `null`.
     */
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
                if (!titleText.isNullOrBlank() && !TitleCandidateLogic.looksLikeAmount(titleText) &&
                    !NexiTitleHeuristics.isKnownSectionTitle(titleText)
                ) {
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

    /**
     * Cerca un titolo di sezione tramite viewId che termina con `/title` nella fascia alta dello schermo.
     *
     * Considera solo testi brevi, non importi, entro circa il 22% superiore dell'area visibile.
     *
     * @param root Nodo radice del sottoalbero.
     * @return Titolo umanizzato se trovato, altrimenti `null`.
     */
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
        val onHome = NexiTitleHeuristics.hasHomeMarkers(collectViewIdShorts(root))

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
