/**
 * Top bar, drawer e tab strip.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionLabels

internal object PrecisionNavigation {
    /**
     * Verifica se il controllo di contrasto va saltato per un'icona nella fascia alta (top bar).
     *
     * Le icone toolbar nella top bar spesso generano falsi positivi di contrasto quando esiste
     * già una content description o un antenato/parent etichettato.
     *
     * @param snap Snapshot del nodo icona da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param viewport Rettangolo del viewport visibile stimato.
     * @return `true` se il controllo contrasto va saltato; `false` altrimenti.
     */
    fun shouldSkipTopBarIconContrast(snap: NodeSnapshot, all: List<NodeSnapshot>, viewport: Rect): Boolean {
        if (!snap.isImageClass()) return false
        if (viewport.isEmpty) return isTopBarControl(snap)
        val topBand = viewport.top + (viewport.height() * 0.20f).toInt()
        if (snap.bounds.bottom > topBand) return false
        if (isTopBarControl(snap)) return true
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id.contains("topbar") || id.contains("toolbar") || id.contains("action")) return true
        return PrecisionLabels.hasLabeledClickableAncestor(snap, all) || !snap.contentDescription.isNullOrBlank()
    }

    /**
     * Verifica se il nodo è un link inline all'interno di un blocco di testo.
     *
     * I link inline in un TextView leggibile sono esentati dal requisito del touch target 48dp,
     * poiché fanno parte del flusso testuale e non di un controllo autonomo.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è un link inline testuale; `false` altrimenti.
     */
    fun isInlineTextLink(snap: NodeSnapshot): Boolean {
        if (!snap.isClickable && !snap.isLongClickable) return false
        val text = snap.text?.trim().orEmpty()
        if (text.isEmpty() || text.length > 40) return false
        return snap.className.contains("TextView", true) &&
            !snap.className.contains("Button", true) &&
            snap.bounds.height() >= snap.minTextHeightPx
    }

    /**
     * Verifica se il nodo è un controllo della top bar Nexi.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo corrisponde a un controllo top bar noto; `false` altrimenti.
     */
    fun isTopBarControl(snap: NodeSnapshot): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        return id in TOPBAR_CONTROL_IDS || id.startsWith("layout_topbar_icon")
    }

    /**
     * Verifica se il nodo è una voce di navigazione del menu laterale (drawer).
     *
     * Riconosce i prefissi tipici delle voci drawer (`nav_*`, `menu_*`, `drawer_*`) definiti
     * nei profili di precisione dell'app.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è una voce di navigazione del drawer; `false` altrimenti.
     */
    fun isDrawerNavItem(snap: NodeSnapshot): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        return AppPrecisionProfiles.drawerNavPrefixes.any { id.startsWith(it) }
    }

    /**
     * Verifica se il nodo è l'area scroll stretta del drawer laterale.
     *
     * Lo scroll del drawer (tipicamente ~13 px di larghezza con id `scroll`) non rappresenta
     * l'area di contenuto principale e va escluso dall'analisi strutturale.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è lo scroll del drawer; `false` altrimenti.
     */
    fun isDrawerScroll(snap: NodeSnapshot): Boolean {
        if (!snap.isScrollable) return false
        return PrecisionGeometry.viewIdShort(snap) == "scroll" && snap.bounds.width() < snap.minTouchTargetPx
    }

    /**
     * Rileva bounds fantasma di nodi cliccabili con dimensioni impossibili.
     *
     * Identifica rettangoli estremamente bassi e larghi (ad esempio `nav_insoluti` 1080×12 px)
     * generati da layout del drawer parzialmente esposto, non corrispondenti a target reali.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se i bounds sono fantasma e non rappresentano un controllo reale; `false` altrimenti.
     */
    fun isPhantomClickableBounds(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable() && !snap.hasVisibleText()) return false
        return snap.bounds.height() < snap.minTouchTargetPx / 3 &&
            snap.bounds.width() > snap.minTouchTargetPx * 4
    }

    /**
     * Determina se un nodo del drawer va escluso dall'analisi.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo appartiene al drawer e va saltato; `false` altrimenti.
     */
    fun shouldSkipDrawerNode(snap: NodeSnapshot): Boolean =
        isDrawerNavItem(snap) || isDrawerScroll(snap) || isPhantomClickableBounds(snap)

    /**
     * Verifica se il nodo è un container `content` ripetuto nel carousel distinte/effetti.
     *
     * I container `content` o `layout_content` duplicati nel carousel generano overlap intenzionali
     * con gli item di lista e non vanno analizzati come nodi distinti.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un container carousel duplicato; `false` altrimenti.
     */
    fun isCarouselContentContainer(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id != "content" && id != "layout_content") return false
        if (all.count { PrecisionGeometry.viewIdShort(it) == id } >= 2) return true
        return PrecisionStructural.isRecyclerListItem(snap, all)
    }

    /** Identificatori dei controlli top bar Nexi riconosciuti dalle regole di precisione. */
    private val TOPBAR_CONTROL_IDS = setOf(
        "topbar_icon_left",
        "topbar_icon_right",
        "layout_topbar_icon_left",
        "layout_topbar_icon_right",
        "topbar",
    )

    /**
     * Verifica se va segnalata l'assenza di etichetta accessibile su un controllo top bar Nexi.
     *
     * Controlla i layout cliccabili `layout_topbar_icon_*` senza nome accessibile e le icone
     * `topbar_icon_*` con content description nulla, tenendo conto di icone figlie o discendenti
     * già etichettati.
     *
     * @param snap Snapshot del nodo top bar da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se manca un'etichetta accessibile e va segnalato; `false` altrimenti.
     */
    fun shouldReportMissingTopBarLabel(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id == "layout_topbar_icon_left" || id == "layout_topbar_icon_right") {
            if (!snap.isInteractiveClickable()) return false
            if (snap.hasAccessibleName()) return false
            val iconId = if (id.contains("left")) "topbar_icon_left" else "topbar_icon_right"
            val icon = all.firstOrNull { PrecisionGeometry.viewIdShort(it) == iconId && snap.bounds.contains(it.bounds) }
            if (icon?.hasAccessibleName() == true) return false
            return !PrecisionLabels.hasLabeledDescendant(snap, all)
        }
        if (id == "topbar_icon_left" || id == "topbar_icon_right") {
            if (!snap.contentDescription.isNullOrBlank()) return false
            val parentClickable = all.any { other ->
                other.traversalIndex < snap.traversalIndex &&
                    other.isInteractiveClickable() &&
                    other.bounds.contains(snap.bounds) &&
                    PrecisionGeometry.viewIdShort(other).startsWith("layout_topbar_icon")
            }
            if (!snap.isInteractiveClickable() && !parentClickable) return false
            return true
        }
        return false
    }
    /**
     * Verifica se il nodo appartiene a una tab strip (TabLayout custom Nexi).
     */
    fun isTabStripNode(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.tabStripViewIds(packageName)) return true
        return snap.className.contains("TabView", true)
    }
}
