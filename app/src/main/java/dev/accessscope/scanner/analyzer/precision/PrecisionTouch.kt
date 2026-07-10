/**
 * Touch target e spaziatura tra controlli.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionNavigation
import dev.accessscope.scanner.analyzer.precision.PrecisionHome
import dev.accessscope.scanner.analyzer.precision.PrecisionLabels
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionTouch {
    /**
     * Verifica se il nodo rappresenta un target tappabile largo (CTA full-width).
     *
     * Le CTA a tutta larghezza sono considerate conformi anche se l'altezza è inferiore a 48 dp,
     * purché la larghezza sia sufficiente per un'interazione confortevole.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è un target tappabile largo; `false` altrimenti.
     */
    fun isWideTapTarget(snap: NodeSnapshot): Boolean =
        snap.bounds.width() >= snap.minTouchTargetPx * 3 &&
            snap.bounds.height() >= (snap.minTouchTargetPx * 0.55f).toInt()

    /**
     * Euristica: controllo "button-like" (CTA) dove lo sfondo interno è più affidabile del ring esterno.
     */
    fun isButtonLikeTapTarget(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable()) return false
        val h = snap.bounds.height()
        val w = snap.bounds.width()
        val min = snap.minTouchTargetPx
        val tallEnough = h >= (min * 0.85f).toInt()
        val wideEnough = w >= min * 2
        val notTooTall = h <= (min * 2.2f).toInt()
        return tallEnough && wideEnough && notTooTall
    }

    /**
     * Verifica se il nodo è probabilmente un badge di stato (pill o etichetta compatta).
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo assomiglia a un badge di stato; `false` altrimenti.
     */
    fun isLikelyStatusBadge(snap: NodeSnapshot): Boolean =
        snap.hasVisibleText() &&
            (snap.text?.length ?: 0) <= 24 &&
            snap.bounds.height() <= snap.minTouchTargetPx &&
            snap.bounds.width() <= snap.minTouchTargetPx * 3

    /**
     * Determina se il controllo di spacing touch va saltato tra due nodi.
     *
     * Esclude nodi drawer, controlli top bar e coppie di elementi nella fascia top bar Nexi,
     * dove lo spacing affiancato è intenzionale per design.
     *
     * @param a Primo snapshot del nodo da valutare.
     * @param b Secondo snapshot del nodo da valutare.
     * @return `true` se il controllo spacing touch va saltato; `false` altrimenti.
     */
    fun shouldSkipTouchSpacingBetween(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot> = emptyList(),
        screenArea: Int = 0,
    ): Boolean {
        if (PrecisionNavigation.shouldSkipDrawerNode(a) || PrecisionNavigation.shouldSkipDrawerNode(b)) return true
        if (PrecisionNavigation.isTopBarControl(a) || PrecisionNavigation.isTopBarControl(b)) return true
        if (PrecisionRulesPlatform.isInsideWebView(a, all) || PrecisionRulesPlatform.isInsideWebView(b, all)) return true
        if (all.isNotEmpty() && screenArea > 0 &&
            PrecisionRulesPlatform.isInsideDenseScrollGrid(a, all, screenArea) &&
            PrecisionRulesPlatform.isInsideDenseScrollGrid(b, all, screenArea)
        ) {
            return true
        }
        val ids = setOf(PrecisionGeometry.viewIdShort(a), PrecisionGeometry.viewIdShort(b))
        if ("topbar_title" in ids && ids.any { it.startsWith("topbar") || it.startsWith("layout_topbar") }) {
            return true
        }
        val inTopBand = a.bounds.top < 400 && b.bounds.top < 400
        val topBarRelated = ids.any { it.startsWith("topbar") || it.startsWith("layout_topbar") }
        if (inTopBand && topBarRelated) return true
        return false
    }
    /**
     * Determina se il controllo touch target va saltato per il nodo.
     *
     * Aggrega le esclusioni per nodi drawer, widget home, link inline, icone in pulsanti
     * etichettati, CTA larghe e container CTA.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo touch target va saltato; `false` altrimenti.
     */
    fun shouldSkipTouchTargetCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean =
        PrecisionNavigation.shouldSkipDrawerNode(snap) ||
            PrecisionNavigation.isPhantomClickableBounds(snap) ||
            PrecisionGeometry.isAnomalousTouchBounds(snap) ||
            PrecisionHome.shouldSkipHomeWidgetAnalysis(snap, all, packageName) ||
            PrecisionNavigation.isInlineTextLink(snap) ||
            PrecisionLabels.isIconInsideLabeledButton(snap, all) ||
            isWideTapTarget(snap) ||
            PrecisionRulesPlatform.isSkeletonPlaceholder(snap) ||
            PrecisionRulesPlatform.isMapSurface(snap) ||
            (PrecisionRulesPlatform.isInsideMapOrMediaSurface(snap, all) && !snap.isMediaControl()) ||
            PrecisionRulesPlatform.shouldSkipComposeTouch(snap) ||
            PrecisionRulesPlatform.isClickableLayoutShell(snap) ||
            PrecisionRulesPlatform.isEmptyClickableHitArea(snap, all) ||
            (PrecisionRulesPlatform.isInsideWebView(snap, all) && !snap.hasAccessibleName()) ||
            // Le CTA "vere" (es. bottoni 65dp) sono già conformi: evitare rumore inutile.
            // Ma le CTA wrap_content (es. CustomViewButtonCta 27dp) vanno segnalate: NON skippare.
            (PrecisionHome.isCtaContainer(snap, packageName) && snap.bounds.height() >= snap.minTouchTargetPx)

    /**
     * Determina se il controllo dimensione testo piccolo va saltato per il nodo.
     *
     * @param snap Snapshot del nodo testo da valutare.
     * @param viewport Rettangolo del viewport visibile stimato.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo testo piccolo va saltato; `false` altrimenti.
     */
    fun shouldSkipSmallTextCheck(snap: NodeSnapshot, viewport: Rect = android.graphics.Rect(), packageName: String = ""): Boolean {
        if (PrecisionNavigation.shouldSkipDrawerNode(snap)) return true
        if (PrecisionRulesPlatform.isSkeletonPlaceholder(snap)) return true
        if (!viewport.isEmpty && PrecisionGeometry.isOffScreenOrMarginalNode(snap, viewport, packageName)) return true
        if (snap.className.contains("Toolbar", true)) return true
        if (snap.text?.length == 1) return true
        return snap.bounds.height() >= snap.minTextHeightPx * 0.85
    }
}
