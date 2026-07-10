package dev.accessscope.scanner.analyzer.title

import android.view.accessibility.AccessibilityNodeInfo

internal object TitleScreenDetection {

    /**
     * Verifica se la radice rappresenta solo il menu laterale (drawer), senza contenuto principale.
     *
     * Considera drawer-only una finestra con almeno due viewId `nav_*` e nessun marker
     * del contenuto principale dell'app.
     *
     * @param root Nodo radice della finestra o del sottoalbero da valutare.
     * @return `true` se l'albero contiene prevalentemente navigazione laterale senza fragment principale.
     */
    fun isDrawerOnlyRoot(root: AccessibilityNodeInfo): Boolean {
        val ids = TitleTreeWalker.collectViewIdShorts(root)
        val navCount = ids.count { it.startsWith("nav_") }
        if (navCount < 2) return false
        return !ids.any { it in NexiTitleHeuristics.MAIN_CONTENT_MARKER_IDS }
    }

    /**
     * Rileva schermate splash o overlay di brand senza navigazione utilizzabile.
     *
     * Una schermata transitoria mostra il logo ma non elementi di navigazione né titoli
     * di sezione riconosciuti; in tal caso non va creata una sezione «Schermata» nel report.
     *
     * @param root Nodo radice dell'albero da analizzare.
     * @return `true` se la radice corrisponde a un overlay transitorio (es. splash con logo).
     */
    fun isTransientOverlay(root: AccessibilityNodeInfo): Boolean {
        val ids = TitleTreeWalker.collectViewIdShorts(root)
        val hasLogo = "logo" in ids
        val hasNav = ids.any { it in NexiTitleHeuristics.NAV_HINT_IDS }
        val hasKnownTitle = TitleTreeWalker.findSectionTitle(root) != null ||
            NexiTitleHeuristics.findKnownNexiTitles(root) != null ||
            NexiTitleHeuristics.findByDistinctiveIds(root) != null
        return hasLogo && !hasNav && !hasKnownTitle
    }

    /**
     * Indica se la radice corrisponde a una schermata di inserimento PIN.
     *
     * Helper pubblico usato per la prioritizzazione tra finestre multiple quando più
     * overlay o activity sono visibili contemporaneamente.
     *
     * @param root Nodo radice dell'albero da ispezionare.
     * @return `true` se [TitleTreeWalker.findPinScreen] individua una schermata PIN.
     */
    fun isPinScreen(root: AccessibilityNodeInfo): Boolean = TitleTreeWalker.findPinScreen(root) != null
}
