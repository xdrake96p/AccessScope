/**
 * Home screen, PIN pad e widget grafici.
 */
package dev.accessscope.scanner.analyzer.precision

import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionStructural
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionHome {
    /** Marker della home screen; i valori effettivi sono forniti da [AppPrecisionProfiles]. */
    private val HOME_SCREEN_MARKER_IDS = emptySet<String>()

    /**
     * Verifica se il contesto corrente corrisponde alla schermata home dell'app.
     *
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per risolvere i marker home specifici.
     * @return `true` se almeno un nodo corrisponde a un marker della home screen; `false` altrimenti.
     */
    fun isHomeScreenContext(all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        val markers = AppPrecisionProfiles.homeScreenMarkers(packageName)
        if (markers.isEmpty()) return false
        return all.any { PrecisionGeometry.viewIdShort(it) in markers }
    }

    /**
     * Verifica se il nodo corrisponde a un tasto del tastierino PIN.
     *
     * Riconosce i view ID configurati nel profilo app oppure controlli cliccabili con testo
     * di una singola cifra numerica.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un tasto del PIN pad; `false` altrimenti.
     */
    fun isPinPadKey(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.pinPadKeyIds(packageName)) return true
        val digit = snap.text?.trim()
        return snap.isInteractiveClickable() &&
            digit?.length == 1 &&
            digit[0].isDigit()
    }

    /**
     * Determina se l'analisi di un tasto PIN va saltata perché non si è in schermata PIN.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param screenTitle Titolo della schermata corrente.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un tasto PIN ma la schermata non è PIN e va saltato; `false` altrimenti.
     */
    fun shouldSkipPinPadWhenNotPinScreen(snap: NodeSnapshot, screenTitle: String, packageName: String = ""): Boolean {
        if (screenTitle.contains("PIN", ignoreCase = true)) return false
        return isPinPadKey(snap, packageName)
    }

    /**
     * Determina se l'analisi di un widget home va saltata.
     *
     * Esclude grafici, CTA e carousel effetti presenti nella home, che hanno regole di precisione
     * dedicate e non devono essere analizzati con gli stessi criteri delle schermate funzionali.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il widget home va escluso dall'analisi; `false` altrimenti.
     */
    fun shouldSkipHomeWidgetAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        if (!isHomeScreenContext(all, packageName)) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        val chartContainers = AppPrecisionProfiles.homeChartContainerIds(packageName)
        val chartText = AppPrecisionProfiles.homeChartTextIds(packageName)
        val carouselWidgets = AppPrecisionProfiles.homeCarouselWidgetIds(packageName)
        if (id in chartContainers || isHomeChartOrCtaWidget(snap, packageName) || isCtaContainer(snap, packageName)) {
            return true
        }
        if (id in chartText || id in carouselWidgets) return true
        if (isHomeEffettiCarouselNode(snap, all, packageName)) return true
        return false
    }

    /**
     * Verifica se il nodo appartiene al tab o item carousel effetti in home.
     *
     * Distingue i nodi del carousel effetti in home dalla schermata dedicata «Paga effetti»,
     * applicando regole di precisione diverse.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è parte del carousel effetti in home; `false` altrimenti.
     */
    fun isHomeEffettiCarouselNode(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        if (!isHomeScreenContext(all, packageName)) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id == "tv_tab") return true
        if (id !in setOf("numero", "amount_effetti", "scadenza", "beneficiario", "desc_breve")) return false
        return all.any { PrecisionGeometry.viewIdShort(it) in setOf("card_home", "tab_home", "card_effetti") }
    }

    /**
     * Determina se va saltato il controllo su contenuto dinamico silenzioso (senza live region).
     *
     * Nel carousel distinte/effetti lo swipe aggiorna il contenuto senza annuncio alle tecnologie
     * assistive: su schermate lista questo comportamento è atteso e non va segnalato come issue.
     *
     * @param screenTitle Titolo della schermata corrente.
     * @param snapshots Elenco degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo contenuto dinamico va saltato; `false` altrimenti.
     */
    fun shouldSkipSilentDynamicContent(
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
        packageName: String,
    ): Boolean {
        if (isHomeScreenContext(snapshots, packageName)) return true
        val title = screenTitle.uppercase()
        if (title.contains("DISTINTE") || title.contains("AUTORIZZA") ||
            title.contains("EFFETTI") || title.contains("PAGA")
        ) {
            return true
        }
        val ids = snapshots.map { PrecisionGeometry.viewIdShort(it) }.toSet()
        if (ids.contains("recycler_distinte") || ids.contains("recycler_effetti")) return true
        if (ids.contains("vop_info") && (ids.contains("multiple_slection") || ids.contains("amount_dist"))) {
            return true
        }
        if (PrecisionStructural.isScrollableListScreen(snapshots)) return true
        if (snapshots.any { PrecisionRulesPlatform.isMediaPlayerSurface(it) }) return true
        if (snapshots.any { PrecisionRulesPlatform.isMapSurface(it) && it.bounds.width() * it.bounds.height() > PrecisionGeometry.estimateViewport(snapshots).let { v -> v.width() * v.height() } * 0.25f }) {
            return true
        }
        return false
    }
    /**
     * Verifica se il nodo è un container CTA (Call To Action) dell'app.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un container CTA; `false` altrimenti.
     */
    fun isCtaContainer(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.ctaContainerIds(packageName)) return true
        return snap.className.contains("CustomViewButtonCta", true) ||
            snap.className.contains("ButtonCta", true)
    }

    /**
     * Verifica se il nodo contiene un discendente `tv_custom` con testo visibile.
     *
     * @param snap Snapshot del nodo contenitore da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un figlio `tv_custom` con testo visibile; `false` altrimenti.
     */
    fun hasTvCustomDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                PrecisionGeometry.viewIdShort(other) == "tv_custom" &&
                snap.bounds.contains(other.bounds) &&
                other.hasVisibleText()
        }
    /**
     * Verifica se il nodo è un widget grafico o CTA presente nella home screen.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un widget grafico o CTA home; `false` altrimenti.
     */
    fun isHomeChartOrCtaWidget(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        return id in AppPrecisionProfiles.homeChartTextIds(packageName) ||
            id in AppPrecisionProfiles.homeChartContainerIds(packageName) ||
            id in AppPrecisionProfiles.ctaContainerIds(packageName)
    }

    /** ID testo grafico home deprecati; i valori effettivi sono in [AppPrecisionProfiles]. */
    private val HOME_CHART_TEXT_IDS = emptySet<String>()
    /** ID container grafico home deprecati; i valori effettivi sono in [AppPrecisionProfiles]. */
    private val HOME_CHART_CONTAINER_IDS = emptySet<String>()

    /**
     * Verifica se il testo del nodo è decorativo nel contesto del grafico home.
     *
     * @param snap Snapshot del nodo testo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il testo è decorativo nel grafico home; `false` altrimenti.
     */
    fun isHomeChartDecorativeText(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (!snap.hasVisibleText()) return false
        if (snap.isFocusable || snap.isInteractiveClickable()) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        // last_30 / last_30_negative: contrasto reale da verificare (14sp su sfondo verde) — non saltare
        val chartText = AppPrecisionProfiles.homeChartTextIds(packageName)
        if (id !in chartText) return false
        if (isInsideHomeChartContainer(snap, all, packageName)) return true
        return isHomeScreenContext(all, packageName)
    }

    /**
     * Verifica se il nodo è contenuto all'interno di un container grafico della home.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è dentro un container grafico home; `false` altrimenti.
     */
    fun isInsideHomeChartContainer(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        val containers = AppPrecisionProfiles.homeChartContainerIds(packageName)
        return all.any { other ->
            other != snap &&
                PrecisionGeometry.viewIdShort(other) in containers &&
                other.bounds.contains(snap.bounds)
        }
    }

    /**
     * Verifica se il nodo è il testo brandizzato di una CTA (`tv_custom` dentro container CTA).
     *
     * @param snap Snapshot del nodo testo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è testo CTA brandizzato; `false` altrimenti.
     */
    fun isBrandedCtaText(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (PrecisionGeometry.viewIdShort(snap) != "tv_custom") return false
        if (!snap.hasVisibleText()) return false
        return all.any { other ->
            other != snap &&
                (isCtaContainer(other, packageName) || PrecisionGeometry.viewIdShort(other) == "ll_custom") &&
                other.bounds.contains(snap.bounds)
        }
    }

    /**
     * Verifica se il nodo è testo CTA primario brandizzato (es. `new_payment`, `tv_custom`).
     */
    fun isBrandedOrPrimaryCtaText(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (!snap.hasVisibleText()) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.primaryCtaTextIds(packageName)) {
            return isCtaContainer(snap, packageName) ||
                all.any { other ->
                    other != snap &&
                        (isCtaContainer(other, packageName) || PrecisionGeometry.viewIdShort(other) == "ll_custom") &&
                        other.bounds.contains(snap.bounds)
                }
        }
        return isBrandedCtaText(snap, all, packageName)
    }
}
