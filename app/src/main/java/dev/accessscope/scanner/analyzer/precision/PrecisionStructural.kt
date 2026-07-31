/**
 * Rumore strutturale scroll, liste e carousel.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.ViolationConfidencePolicy
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionLabels
import dev.accessscope.scanner.analyzer.precision.PrecisionNavigation
import dev.accessscope.scanner.analyzer.precision.PrecisionTouch
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform
import dev.accessscope.scanner.analyzer.precision.area

internal object PrecisionStructural {
    /**
     * Verifica se il nodo corrisponde a una riga di selezione lista a tutta larghezza.
     *
     * Le righe di selezione occupano intenzionalmente l'intera larghezza dello schermo e possono
     * sovrapporsi al container padre: l'overlap in questi casi non va segnalato come issue.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param screenWidth Larghezza dello schermo in pixel.
     * @return `true` se il nodo è una riga di selezione a tutta larghezza; `false` altrimenti.
     */
    fun isFullWidthListRow(snap: NodeSnapshot, screenWidth: Int): Boolean {
        if (isCarouselSelectionRow(snap)) return true
        if (screenWidth <= 0) return false
        if (snap.bounds.width() < screenWidth * 0.80f) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        return snap.isCheckable ||
            id.contains("select") ||
            id.contains("slection") ||
            id.contains("check") ||
            id.contains("selection") ||
            id.contains("checkbox")
    }

    /**
     * Verifica se il nodo è una checkbox o riga di selezione a tutta larghezza in un carousel.
     *
     * L'overlap con il FrameLayout padre è intenzionale in questi casi e non costituisce
     * un problema di accessibilità da segnalare.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è una riga di selezione del carousel; `false` altrimenti.
     */
    fun isCarouselSelectionRow(snap: NodeSnapshot): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        return (id.contains("select") || id.contains("selection")) &&
            snap.bounds.width() >= snap.minTouchTargetPx * 5
    }

    /**
     * Determina se un nodo va escluso come rumore strutturale durante l'analisi.
     *
     * Aggrega i controlli su nodi fuori schermo, bounds anomali e righe lista a tutta larghezza,
     * preservando le etichette di campo note per il contrasto.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param viewport Rettangolo del viewport visibile stimato.
     * @param screenWidth Larghezza dello schermo in pixel.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo va saltato come rumore strutturale; `false` altrimenti.
     */
    fun shouldSkipStructuralNoise(
        snap: NodeSnapshot,
        viewport: Rect,
        screenWidth: Int,
        packageName: String = "",
    ): Boolean {
        if (PrecisionLabels.isKnownContrastFieldLabel(snap, packageName)) return false
        if (PrecisionRulesPlatform.isSkeletonPlaceholder(snap)) return true
        if (PrecisionGeometry.isOffScreenOrMarginalNode(snap, viewport, packageName)) return true
        if (PrecisionGeometry.isAnomalousTouchBounds(snap)) return true
        if (isFullWidthListRow(snap, screenWidth)) return true
        return false
    }
    /**
     * Verifica se la schermata corrente è una lista scrollabile con ricerca.
     *
     * Le liste con RecyclerView e campo di ricerca producono aggiornamenti durante lo scroll
     * senza live region: comportamento atteso che non richiede segnalazione.
     *
     * @param snapshots Elenco degli snapshot dei nodi nella schermata.
     * @return `true` se la schermata è una lista scrollabile con ricerca; `false` altrimenti.
     */
    fun isScrollableListScreen(snapshots: List<NodeSnapshot>): Boolean {
        val hasRecycler = snapshots.any {
            it.className.contains("RecyclerView", true) || it.className.contains("ListView", true)
        }
        if (!hasRecycler) return false
        val hasSearch = snapshots.any { snap ->
            snap.isEditable && (
                PrecisionGeometry.viewIdShort(snap).contains("search") ||
                    PrecisionGeometry.viewIdShort(snap).contains("edt_") ||
                    PrecisionGeometry.viewIdShort(snap).contains("input") ||
                    snap.hintText?.contains("cerca", ignoreCase = true) == true ||
                    snap.hintText?.contains("search", ignoreCase = true) == true
                )
        }
        val scrollables = snapshots.count { it.isScrollable }
        return hasSearch || scrollables >= 2
    }

    /**
     * Determina se il controllo di overlap va saltato tra due nodi.
     *
     * Esclude overlap intenzionali tra righe carousel, spacing touch consentito e widget home
     * quando il contesto non è la schermata home.
     *
     * @param a Primo snapshot del nodo coinvolto nell'overlap.
     * @param b Secondo snapshot del nodo coinvolto nell'overlap.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @param screenWidth Larghezza dello schermo in pixel.
     * @return `true` se l'overlap tra i due nodi va ignorato; `false` altrimenti.
     */
    fun shouldSkipOverlapBetween(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenWidth: Int = 0,
    ): Boolean {
        if (isCarouselSelectionRow(a) || isCarouselSelectionRow(b)) return true
        if (PrecisionTouch.shouldSkipTouchSpacingBetween(a, b)) return true
        if (screenWidth > 0 && (isFullWidthListRow(a, screenWidth) || isFullWidthListRow(b, screenWidth))) {
            return true
        }
        val screenArea = if (screenWidth > 0) screenWidth * PrecisionGeometry.estimateViewport(all).height() else 0
        if (isStructuralScrollOverlap(a, b, all, packageName, screenArea)) return true
        if (PrecisionNavigation.isTabStripNode(a, packageName) && PrecisionNavigation.isTabStripNode(b, packageName)) return true
        if (PrecisionRulesPlatform.isObscuredByModalOverlay(a, all) || PrecisionRulesPlatform.isObscuredByModalOverlay(b, all)) return true
        if (PrecisionRulesPlatform.isInsideMapOrMediaSurface(a, all) && PrecisionRulesPlatform.isInsideMapOrMediaSurface(b, all)) return true
        if (PrecisionRulesPlatform.isInsideWebView(a, all) || PrecisionRulesPlatform.isInsideWebView(b, all)) return true
        if (PrecisionRulesPlatform.isClickableLayoutShell(a) && PrecisionRulesPlatform.isClickableLayoutShell(b)) return true
        if (PrecisionRulesPlatform.isLayoutShellOverlap(a, b, all)) return true
        if (ViolationConfidencePolicy.isStructuralNoiseId(a.viewId) ||
            ViolationConfidencePolicy.isStructuralNoiseId(b.viewId)
        ) {
            return true
        }
        if (a.isWebView() || b.isWebView()) return true
        return false
    }

    /**
     * Determina se l'analisi di un item del carousel lista va saltata.
     *
     * @param snap Snapshot del nodo item carousel da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se l'item carousel va escluso dall'analisi; `false` altrimenti.
     */
    fun shouldSkipCarouselListItemAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        if (PrecisionNavigation.isCarouselContentContainer(snap, all, packageName)) return true
        if (isCarouselSelectionRow(snap)) return true
        val id = PrecisionGeometry.viewIdShort(snap)
        if ((id.contains("select") || id.contains("selection")) &&
            all.count { PrecisionGeometry.viewIdShort(it) == id } >= 1 &&
            snap.bounds.width() > PrecisionGeometry.estimateViewport(all).width() * 0.75f
        ) {
            return true
        }
        return false
    }

    /**
     * Verifica se il nodo è l'area scroll del contenuto principale della schermata.
     *
     * @param snap Snapshot del nodo scrollabile da valutare.
     * @param screenArea Area totale dello schermo in pixel quadrati.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è lo scroll del contenuto principale; `false` altrimenti.
     */
    fun isMainContentScroll(snap: NodeSnapshot, screenArea: Int, packageName: String = ""): Boolean {
        if (!snap.isScrollable) return false
        if (PrecisionGeometry.viewIdShort(snap) !in AppPrecisionProfiles.mainContentScrollIds(packageName)) return false
        if (screenArea <= 0) return true
        val snapArea = snap.bounds.width() * snap.bounds.height()
        return snapArea > screenArea * 0.35f
    }
    /**
     * Overlap strutturale tra root scroll e contenitore layout (non doppio target reale).
     */
    fun isStructuralScrollOverlap(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenArea: Int,
    ): Boolean {
        val idsA = PrecisionGeometry.viewIdShort(a)
        val idsB = PrecisionGeometry.viewIdShort(b)
        val scrollIds = AppPrecisionProfiles.mainContentScrollIds(packageName) + setOf("scrollview_port", "content")
        val aScroll = a.isScrollable || idsA in scrollIds || a.className.contains("ScrollView", true)
        val bScroll = b.isScrollable || idsB in scrollIds || b.className.contains("ScrollView", true)
        val aRoot = idsA == "content" || idsA == "container"
        val bRoot = idsB == "content" || idsB == "container"
        if ((aScroll && bRoot) || (bScroll && aRoot)) return true
        if (aScroll && bScroll) return true
        if (isMainContentScroll(a, screenArea, packageName) || isMainContentScroll(b, screenArea, packageName)) {
            if (aRoot || bRoot || idsA == "container" || idsB == "container") return true
        }
        if (a.bounds.contains(b.bounds) || b.bounds.contains(a.bounds)) return true
        if (isAncestorDescendantOverlap(a, b, all)) return true
        return false
    }

    private fun isAncestorDescendantOverlap(a: NodeSnapshot, b: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!Rect.intersects(a.bounds, b.bounds)) return false
        val smaller = if (a.area() <= b.area()) a else b
        val larger = if (smaller === a) b else a
        if (!larger.bounds.contains(smaller.bounds.centerX(), smaller.bounds.centerY())) return false
        val largerIsStructural = isScrollContainer(larger) ||
            PrecisionGeometry.viewIdShort(larger) in setOf("content", "container", "scrollview_port") ||
            larger.className.contains("RelativeLayout", true) ||
            larger.className.contains("FrameLayout", true)
        val smallerIsStructural = isScrollContainer(smaller) ||
            PrecisionGeometry.viewIdShort(smaller) in setOf("content", "scrollview_port")
        return largerIsStructural && smallerIsStructural
    }

    /**
     * Verifica se il nodo è un TextView all'interno di un item RecyclerView o carousel.
     *
     * I testi negli item di lista o carousel non costituiscono heading strutturali di pagina.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è dentro un item carousel o lista; `false` altrimenti.
     */
    fun isInsideCarouselOrListItem(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (snap.className.contains("RecyclerView", true)) return false
        return all.any { other ->
            other != snap &&
                other.bounds.contains(snap.bounds) &&
                other.bounds.height() > snap.bounds.height() * 1.5 &&
                (
                    other.className.contains("RecyclerView", true) ||
                        other.className.contains("ViewPager", true) ||
                        PrecisionGeometry.viewIdShort(other) in setOf("recycler", "content", "layout_content") ||
                        (PrecisionLabels.isKnownListTemplateId(other.viewId, packageName) && other.bounds.area() > snap.bounds.area() * 2)
                    )
        }
    }
    /**
     * Verifica se il nodo è un item ripetuto di RecyclerView (stesso viewId in più istanze).
     *
     * Riconosce template di lista con stesso viewId, stessa classe e altezze simili tra i sibling.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se il nodo è un item ripetuto di RecyclerView; `false` altrimenti.
     */
    fun isRecyclerListItem(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        val id = snap.viewId ?: return false
        val siblings = all.filter { it.viewId == id }
        if (siblings.size < 2) return false
        val sameClass = siblings.map { it.className }.distinct().size == 1
        if (!sameClass) return false
        val heights = siblings.map { it.bounds.height() }
        val avg = heights.average()
        val heightsSimilar = heights.all { kotlin.math.abs(it - avg) <= avg * 0.15 + 2 }
        return heightsSimilar
    }

    /**
     * Verifica se il nodo è un contenitore scrollabile riconosciuto (ScrollView, RecyclerView, ecc.).
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è un container scroll noto; `false` altrimenti.
     */
    fun isScrollContainer(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        val id = PrecisionGeometry.viewIdShort(snap)
        return snap.isScrollable && (
            cls.contains("scrollview") ||
                cls.contains("recyclerview") ||
                cls.contains("viewpager") ||
                cls.contains("horizontalscroll") ||
                id in setOf("scrollview_port", "scroll", "card_home", "content")
            )
    }

    /**
     * Determina se il controllo etichetta per area scrollabile va saltato.
     *
     * Esclude scroll del drawer, contenuto principale, container scroll noti con discendenti
     * etichettati e aree scroll ampie con almeno tre figli accessibili.
     *
     * @param snap Snapshot del nodo scrollabile da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param screenArea Area totale dello schermo in pixel quadrati.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo etichetta scroll va saltato; `false` altrimenti.
     */
    fun shouldSkipScrollWithoutLabel(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int, packageName: String = ""): Boolean {
        if (PrecisionNavigation.isDrawerScroll(snap)) return true
        if (!snap.isScrollable) return false
        if (isMainContentScroll(snap, screenArea, packageName)) return true
        val cls = snap.className.lowercase()
        val isKnownContainer = isScrollContainer(snap) ||
            cls.contains("recyclerview") ||
            cls.contains("viewpager")
        if (!isKnownContainer) return false
        if (PrecisionLabels.hasLabeledDescendant(snap, all) || PrecisionLabels.hasLabeledDescendantInScroll(snap, all)) return true
        val snapArea = snap.bounds.width() * snap.bounds.height()
        if (screenArea > 0 && snapArea > screenArea * 0.5f) {
            val labeledChildren = all.count { other ->
                other != snap &&
                    Rect.intersects(snap.bounds, other.bounds) &&
                    other.hasAccessibleName()
            }
            if (labeledChildren >= 3) return true
        }
        return false
    }
}
