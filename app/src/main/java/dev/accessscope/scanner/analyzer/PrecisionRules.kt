/**
 * Regole di precisione per l'analisi dell'accessibilità dell'interfaccia utente.
 *
 * Questo modulo raccoglie euristiche e filtri per ridurre falsi positivi e falsi negativi
 * durante la scansione dei nodi dell'albero di accessibilità Android. Le regole tengono conto
 * di pattern specifici dell'applicazione (topbar Nexi, drawer, carousel distinte/effetti,
 * widget home, tastierino PIN, ecc.) e di comportamenti strutturali comuni come overlap
 * intenzionale, aggiornamenti dinamici senza live region e target touch ridotti.
 *
 * @see NodeSnapshot
 * @see AppPrecisionProfiles
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Rect

/**
 * Oggetto singleton che espone le funzioni di valutazione e filtro per l'analisi precisa dei nodi UI.
 *
 * Ogni funzione risponde a una domanda specifica (ad esempio: « questo nodo va escluso dal report? »,
 * « è decorativo? », « ha un'etichetta accessibile sufficiente? ») ed è invocata dagli analyzer
 * di AccessScope durante la classificazione degli issue di accessibilità.
 */
object PrecisionRules {

    /**
     * Estrae l'identificatore breve del view ID di un nodo, normalizzato in minuscolo.
     *
     * Rimuove il prefisso del package Android (tutto ciò che precede l'ultimo `/`) e restituisce
     * la parte finale dell'ID risorsa, utile per confronti euristici indipendenti dal namespace.
     *
     * @param snap Snapshot del nodo da cui estrarre il view ID.
     * @return Identificatore breve in minuscolo, oppure stringa vuota se il view ID è assente.
     */
    fun viewIdShort(snap: NodeSnapshot): String =
        snap.viewId?.substringAfterLast('/')?.lowercase().orEmpty()

    /**
     * Stima il rettangolo del viewport visibile a partire dall'insieme degli snapshot analizzati.
     *
     * Calcola il bounding box minimo che contiene i bounds di tutti i nodi forniti, approssimando
     * l'area effettivamente occupata dal contenuto sullo schermo.
     *
     * @param snapshots Elenco degli snapshot dei nodi presenti nella schermata corrente.
     * @return Rettangolo che delimita il viewport stimato, oppure [Rect] vuoto se la lista è vuota.
     */
    fun estimateViewport(snapshots: List<NodeSnapshot>): Rect {
        if (snapshots.isEmpty()) return Rect()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = 0
        var bottom = 0
        snapshots.forEach { snap ->
            left = minOf(left, snap.bounds.left)
            top = minOf(top, snap.bounds.top)
            right = maxOf(right, snap.bounds.right)
            bottom = maxOf(bottom, snap.bounds.bottom)
        }
        return Rect(left, top, right, bottom)
    }

    /**
     * Verifica se un nodo è fuori dal viewport o marginalmente visibile (rumore di layout).
     *
     * Un nodo viene considerato fuori schermo o marginale se non interseca il viewport,
     * se è micro-testo posizionato sotto il fold (oltre il 90% dell'altezza del viewport),
     * se eccede i margini laterali oltre la soglia touch target, oppure se presenta bounds
     * anomali. Le etichette di campo note ([isKnownContrastFieldLabel]) nel viewport
     * non vengono escluse per preservare il recall mirato.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param viewport Rettangolo del viewport visibile stimato.
     * @param packageName Nome del package dell'app, usato per profili di precisione specifici.
     * @return `true` se il nodo va trattato come fuori schermo o marginale; `false` altrimenti.
     */
    fun isOffScreenOrMarginalNode(snap: NodeSnapshot, viewport: Rect, packageName: String = ""): Boolean {
        if (viewport.isEmpty) return false
        if (!Rect.intersects(snap.bounds, viewport)) return true
        val belowFold = snap.bounds.top > viewport.top + (viewport.height() * 0.90f).toInt()
        val tiny = snap.bounds.height() < snap.minTextHeightPx * 0.75f
        if (belowFold && tiny) return true
        if (isKnownContrastFieldLabel(snap, packageName) && !belowFold) return false
        if (snap.bounds.left < viewport.left - snap.minTouchTargetPx) return true
        if (snap.bounds.right > viewport.right + snap.minTouchTargetPx) return true
        return false
    }

    /**
     * Rileva bounds anomali che non rappresentano un'area tappabile reale.
     *
     * Identifica strisce verticali o orizzontali troppo sottili rispetto alla dimensione minima
     * del touch target (ad esempio 79×698 px), tipiche di artefatti di layout non interattivi.
     *
     * @param snap Snapshot del nodo i cui bounds devono essere verificati.
     * @return `true` se i bounds sono anomali e non tappabili; `false` altrimenti.
     */
    fun isAnomalousTouchBounds(snap: NodeSnapshot): Boolean {
        val w = snap.bounds.width()
        val h = snap.bounds.height()
        val min = snap.minTouchTargetPx
        val thinVertical = w < (min * 0.75f).toInt() && h > min * 4
        val thinHorizontal = h < min / 3 && w > min * 4
        val hairlineText = snap.hasVisibleText() && (h <= 2 || w <= 2)
        return thinVertical || thinHorizontal || hairlineText
    }

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
        val id = viewIdShort(snap)
        return snap.isCheckable ||
            id.contains("select") ||
            id.contains("slection") ||
            id.contains("check") ||
            id.contains("selection") ||
            id.contains("checkbox")
    }

    /**
     * Verifica se il nodo è una checkbox o riga di selezione del carousel distinte/effetti.
     *
     * Nel carousel Nexi l'overlap con il FrameLayout padre è intenzionale e non costituisce
     * un problema di accessibilità da segnalare.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il nodo è una riga di selezione del carousel; `false` altrimenti.
     */
    fun isCarouselSelectionRow(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        return id in setOf("multiple_slection", "check_multiple_selection", "checkbox_all") ||
            (id.contains("slection") && snap.bounds.width() >= snap.minTouchTargetPx * 5)
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
        if (isKnownContrastFieldLabel(snap, packageName)) return false
        if (isSkeletonPlaceholder(snap)) return true
        if (isOffScreenOrMarginalNode(snap, viewport, packageName)) return true
        if (isAnomalousTouchBounds(snap)) return true
        if (isFullWidthListRow(snap, screenWidth)) return true
        return false
    }

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
        val id = viewIdShort(snap)
        if (id.contains("topbar") || id.contains("toolbar") || id.contains("action")) return true
        return hasLabeledClickableAncestor(snap, all) || !snap.contentDescription.isNullOrBlank()
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
        val id = viewIdShort(snap)
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
        val id = viewIdShort(snap)
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
        return viewIdShort(snap) == "scroll" && snap.bounds.width() < snap.minTouchTargetPx
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
        val id = viewIdShort(snap)
        if (id != "content" && id != "layout_content") return false
        if (all.count { viewIdShort(it) == id } >= 2) return true
        return isRecyclerListItem(snap, all)
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
        val id = viewIdShort(snap)
        if (id == "layout_topbar_icon_left" || id == "layout_topbar_icon_right") {
            if (!snap.isInteractiveClickable()) return false
            if (snap.hasAccessibleName()) return false
            val iconId = if (id.contains("left")) "topbar_icon_left" else "topbar_icon_right"
            val icon = all.firstOrNull { viewIdShort(it) == iconId && snap.bounds.contains(it.bounds) }
            if (icon?.hasAccessibleName() == true) return false
            return !hasLabeledDescendant(snap, all)
        }
        if (id == "topbar_icon_left" || id == "topbar_icon_right") {
            if (!snap.contentDescription.isNullOrBlank()) return false
            val parentClickable = all.any { other ->
                other.traversalIndex < snap.traversalIndex &&
                    other.isInteractiveClickable() &&
                    other.bounds.contains(snap.bounds) &&
                    viewIdShort(other).startsWith("layout_topbar_icon")
            }
            if (!snap.isInteractiveClickable() && !parentClickable) return false
            return true
        }
        return false
    }

    /**
     * Verifica se un'icona è contenuta all'interno di un pulsante già etichettato testualmente.
     *
     * In questi casi segnalare l'icona come priva di etichetta produce spesso un falso positivo,
     * poiché il nome accessibile è già fornito dal contenitore cliccabile padre.
     *
     * @param snap Snapshot del nodo icona da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se l'icona è dentro un pulsante etichettato; `false` altrimenti.
     */
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        if (hasLabeledClickableAncestor(snap, all)) return true
        if (isIconWithLabeledSibling(snap, all)) return true
        return all.any { other ->
            other != snap &&
                other.bounds.contains(snap.bounds) &&
                other.isInteractiveClickable() &&
                other.hasAccessibleName() &&
                !other.isImageClass()
        }
    }

    /**
     * Verifica se un'icona ha un fratello (sibling) con etichetta accessibile nello stesso contenitore.
     *
     * Pattern comune in swipe action (tick/cestino) e stati empty state, dove l'icona decorativa
     * convive con un testo etichettato nello stesso gruppo visivo.
     *
     * @param snap Snapshot del nodo icona da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un fratello etichettato nello stesso contenitore; `false` altrimenti.
     */
    fun isIconWithLabeledSibling(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        val container = findSmallestContainer(snap, all) ?: return false
        return all.any { sibling ->
            sibling != snap &&
                sibling != container &&
                container.bounds.contains(sibling.bounds) &&
                !sibling.isImageClass() &&
                sibling.hasAccessibleName()
        }
    }

    /**
     * Trova il contenitore più piccolo che include completamente i bounds del nodo dato.
     *
     * @param snap Snapshot del nodo di riferimento.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return Lo snapshot del contenitore con area minima che contiene il nodo, oppure `null` se assente.
     */
    private fun findSmallestContainer(snap: NodeSnapshot, all: List<NodeSnapshot>): NodeSnapshot? =
        all.filter { it != snap && it.bounds.contains(snap.bounds) }
            .minByOrNull { it.bounds.width() * it.bounds.height() }

    /**
     * Verifica se il nodo ha un antenato cliccabile con nome accessibile.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un antenato cliccabile etichettato; `false` altrimenti.
     */
    fun hasLabeledClickableAncestor(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        return all.any { candidate ->
            candidate.traversalIndex < snap.traversalIndex &&
                candidate.isInteractiveClickable() &&
                candidate.hasAccessibleName() &&
                candidate.bounds.contains(snap.bounds)
        }
    }

    /**
     * Verifica se il nodo ha almeno un discendente con nome accessibile.
     *
     * @param snap Snapshot del nodo contenitore da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un discendente etichettato; `false` altrimenti.
     */
    fun hasLabeledDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                snap.bounds.contains(other.bounds) &&
                other.hasAccessibleName()
        }

    /**
     * Verifica se il nodo ha discendenti etichettati che intersecano i suoi bounds.
     *
     * Variante di [hasLabeledDescendant] utile per ScrollView, dove i bounds stretti dei figli
     * possono non essere completamente contenuti nel rettangolo del contenitore scrollabile.
     *
     * @param snap Snapshot del nodo contenitore scrollabile da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un discendente etichettato in intersezione; `false` altrimenti.
     */
    fun hasLabeledDescendantInScroll(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                Rect.intersects(snap.bounds, other.bounds) &&
                other.hasAccessibleName()
        }

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
        return all.any { viewIdShort(it) in markers }
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
        val id = viewIdShort(snap)
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
        val id = viewIdShort(snap)
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
        val id = viewIdShort(snap)
        if (id == "tv_tab") return true
        if (id !in setOf("numero", "amount_effetti", "scadenza", "beneficiario", "desc_breve")) return false
        return all.any { viewIdShort(it) in setOf("card_home", "tab_home", "card_effetti") }
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
        val ids = snapshots.map { viewIdShort(it) }.toSet()
        if (ids.contains("recycler_distinte") || ids.contains("recycler_effetti")) return true
        if (ids.contains("vop_info") && (ids.contains("multiple_slection") || ids.contains("amount_dist"))) {
            return true
        }
        if (isScrollableListScreen(snapshots)) return true
        if (snapshots.any { isMediaPlayerSurface(it) }) return true
        if (snapshots.any { isMapSurface(it) && it.bounds.width() * it.bounds.height() > estimateViewport(snapshots).let { v -> v.width() * v.height() } * 0.25f }) {
            return true
        }
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
                viewIdShort(snap).contains("search") ||
                    viewIdShort(snap).contains("edt_") ||
                    viewIdShort(snap).contains("input") ||
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
        if (shouldSkipTouchSpacingBetween(a, b)) return true
        if (screenWidth > 0 && (isFullWidthListRow(a, screenWidth) || isFullWidthListRow(b, screenWidth))) {
            return true
        }
        val screenArea = if (screenWidth > 0) screenWidth * estimateViewport(all).height() else 0
        if (isStructuralScrollOverlap(a, b, all, packageName, screenArea)) return true
        if (isTabStripNode(a, packageName) && isTabStripNode(b, packageName)) return true
        if (isTabStripNode(a, packageName) || isTabStripNode(b, packageName)) {
            val tabParent = all.any { viewIdShort(it) in setOf("card_effetti", "tab_home", "card_home") }
            if (tabParent) return true
        }
        if (isObscuredByModalOverlay(a, all) || isObscuredByModalOverlay(b, all)) return true
        if (isInsideMapOrMediaSurface(a, all) && isInsideMapOrMediaSurface(b, all)) return true
        if (isInsideWebView(a, all) || isInsideWebView(b, all)) return true
        if (isClickableLayoutShell(a) && isClickableLayoutShell(b)) return true
        if (isLayoutShellOverlap(a, b, all)) return true
        if (a.isWebView() || b.isWebView()) return true
        if (!isHomeScreenContext(all, packageName)) return false
        return shouldSkipHomeWidgetAnalysis(a, all, packageName) ||
            shouldSkipHomeWidgetAnalysis(b, all, packageName)
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
        if (isCarouselContentContainer(snap, all, packageName)) return true
        if (isCarouselSelectionRow(snap)) return true
        val id = viewIdShort(snap)
        if ((id.contains("select") || id.contains("selection")) &&
            all.count { viewIdShort(it) == id } >= 1 &&
            snap.bounds.width() > estimateViewport(all).width() * 0.75f
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
        if (viewIdShort(snap) !in AppPrecisionProfiles.mainContentScrollIds(packageName)) return false
        if (screenArea <= 0) return true
        val snapArea = snap.bounds.width() * snap.bounds.height()
        return snapArea > screenArea * 0.35f
    }

    /**
     * Verifica se il nodo è un container CTA (Call To Action) dell'app.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un container CTA; `false` altrimenti.
     */
    fun isCtaContainer(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
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
                viewIdShort(other) == "tv_custom" &&
                snap.bounds.contains(other.bounds) &&
                other.hasVisibleText()
        }

    /**
     * Determina se il controllo etichetta del container cliccabile va saltato.
     *
     * Esclude container il cui figlio espone già il nome accessibile (ad esempio CustomViewButtonCta
     * con `tv_custom`), container home, carousel e layout con discendenti etichettati.
     *
     * @param snap Snapshot del nodo contenitore da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo etichetta va saltato; `false` altrimenti.
     */
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (shouldSkipHomeWidgetAnalysis(snap, all, packageName)) return true
        if (isCarouselContentContainer(snap, all, packageName)) return true
        if (isCtaContainer(snap, packageName) && hasTvCustomDescendant(snap, all)) return true
        if (!snap.hasAccessibleName()) {
            if (hasLabeledDescendant(snap, all) || hasLabeledDescendantInScroll(snap, all)) return true
            if (isLayoutContainer(snap.className) || snap.isCustomView()) {
                return all.any { other ->
                    other != snap &&
                        snap.bounds.contains(other.bounds) &&
                        other.hasAccessibleName() &&
                        other.hasVisibleText()
                }
            }
        }
        return false
    }

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
     * Determina se il controllo heading strutturale va saltato per il nodo.
     *
     * Badge di stato, pill, etichette di campo e testi decorativi in maiuscolo non costituiscono
     * heading strutturali di pagina e non devono essere valutati come tali.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il controllo heading va saltato; `false` altrimenti.
     */
    fun shouldSkipHeadingCheck(snap: NodeSnapshot): Boolean {
        if (isLikelyStatusBadge(snap)) return true
        val viewIdShort = viewIdShort(snap)
        if (viewIdShort == "state" || viewIdShort.contains("badge") || viewIdShort.contains("status")) {
            return true
        }
        if (viewIdShort in setOf(
                "last_access", "name_account", "labelcontacts", "enroll_user",
                "tv_custom", "topbar_title", "no_result", "filtri_attivi",
                "totale_distinte", "total_amount_ins", "user_type", "currency",
                "multiple_slection", "checkbox_all", "rotate_display", "logo",
                "tv_title_second_section", "show_more",
            )
        ) {
            return true
        }
        if (isListFieldLabel(snap)) return true
        val text = snap.text?.trim().orEmpty()
        if (text.isNotEmpty() && text == text.uppercase() && text.length <= 24 &&
            snap.bounds.height() <= snap.minTouchTargetPx
        ) {
            return true
        }
        return false
    }

    /**
     * Verifica se il nodo è un'etichetta di campo in card o lista.
     *
     * Combina gli ID definiti nel profilo app con pattern generici di label (`label`, `iban`,
     * `amount`, `hint`, ecc.).
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un'etichetta di campo lista/card; `false` altrimenti.
     */
    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
        if (id.isEmpty()) return false
        if (id in AppPrecisionProfiles.fieldLabelIds(packageName)) return true
        return isGenericFieldLabelPattern(id)
    }

    /**
     * Verifica se un view ID corrisponde a un pattern generico di etichetta di campo.
     *
     * @param id Identificatore breve del view ID già normalizzato in minuscolo.
     * @return `true` se l'ID corrisponde a un pattern di field label; `false` altrimenti.
     */
    private fun isGenericFieldLabelPattern(id: String): Boolean =
        id.startsWith("txt_data_") ||
            id.startsWith("data_") ||
            id.contains("label") ||
            id.contains("iban") ||
            id.contains("amount") ||
            id.contains("email") ||
            id.contains("phone") ||
            id.contains("causale") ||
            id.contains("description") ||
            id.contains("subtitle") ||
            id.contains("hint")

    /**
     * Verifica se il nodo è un'etichetta di campo nota per i controlli di contrasto.
     *
     * Alias di [isListFieldLabel] usato nel contesto dei filtri di contrasto colore.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un'etichetta di campo nota; `false` altrimenti.
     */
    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean =
        isListFieldLabel(snap, packageName)

    /**
     * Verifica se una stringa di testo rappresenta un importo o valuta.
     *
     * @param text Testo da analizzare.
     * @return `true` se il testo corrisponde a un pattern di importo/valuta; `false` altrimenti.
     */
    fun isCurrencyOrAmountText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.matches(Regex("""^[\d\s.,€$+-]+$"""))) return true
        return t.matches(Regex("""^\d{1,3}(\.\d{3})*,\d{2}\s*€?$"""))
    }

    /**
     * Verifica se un view ID appartiene ai template di item lista configurati nel profilo app.
     *
     * @param viewId View ID completo del nodo (con eventuale prefisso package).
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se l'ID è un template lista noto; `false` altrimenti.
     */
    fun isKnownListTemplateId(viewId: String?, packageName: String = ""): Boolean {
        if (viewId.isNullOrBlank()) return false
        return viewId.substringAfterLast('/').lowercase() in AppPrecisionProfiles.listTemplateIds(packageName)
    }

    /**
     * Verifica se il nodo è un widget grafico o CTA presente nella home screen.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un widget grafico o CTA home; `false` altrimenti.
     */
    fun isHomeChartOrCtaWidget(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
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
        val id = viewIdShort(snap)
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
                viewIdShort(other) in containers &&
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
        if (viewIdShort(snap) != "tv_custom") return false
        if (!snap.hasVisibleText()) return false
        return all.any { other ->
            other != snap &&
                (isCtaContainer(other, packageName) || viewIdShort(other) == "ll_custom") &&
                other.bounds.contains(snap.bounds)
        }
    }

    /**
     * Verifica se il nodo è testo CTA primario brandizzato (es. `new_payment`, `tv_custom`).
     */
    fun isBrandedOrPrimaryCtaText(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (!snap.hasVisibleText()) return false
        val id = viewIdShort(snap)
        if (id in AppPrecisionProfiles.primaryCtaTextIds(packageName)) {
            return isCtaContainer(snap, packageName) ||
                all.any { other ->
                    other != snap &&
                        (isCtaContainer(other, packageName) || viewIdShort(other) == "ll_custom") &&
                        other.bounds.contains(snap.bounds)
                }
        }
        return isBrandedCtaText(snap, all, packageName)
    }

    /**
     * TextView/AppCompatTextView con solo sfondo/drawable e nessun contenuto testuale accessibile.
     *
     * Non va misurato per contrasto: non c'è testo reale da valutare.
     */
    fun isEmptyTextSurfaceWithoutContent(snap: NodeSnapshot): Boolean {
        if (!snap.className.contains("TextView", ignoreCase = true)) return false
        if (snap.isHeading) return false
        if (snap.hasVisibleText()) return false
        if (!snap.hintText.isNullOrBlank()) return false
        if (!snap.contentDescription.isNullOrBlank()) return false
        if (snap.isEditable && !snap.hintText.isNullOrBlank()) return false
        return true
    }

    /**
     * Determina se il controllo contrasto testo va saltato per questo nodo.
     */
    fun shouldSkipContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ): Boolean {
        if (isBrandedOrPrimaryCtaText(snap, all, packageName)) return true
        if (isEmptyTextSurfaceWithoutContent(snap)) return true
        val area = screenAreaPx.takeIf { it > 0 } ?: estimateViewport(all).let { it.width() * it.height() }
        if (isTextOverIllustratedBackground(snap, all, area)) return true
        val id = viewIdShort(snap)
        if (id in AppPrecisionProfiles.carouselDecorativeContrastIds(packageName) &&
            isInsideCarouselOrListItem(snap, all, packageName)
        ) {
            return true
        }
        if (id == "causale" && isInsideCarouselOrListItem(snap, all, packageName)) return true
        if (id == "tv_title_second_section" && isHomeScreenContext(all, packageName)) return true
        if (snap.isEditable && snap.text.isNullOrBlank() && !snap.hintText.isNullOrBlank()) {
            // Hint contrast handled separately; skip empty-value sampling
            return false
        }
        if (isSkeletonPlaceholder(snap) || isLottieAnimation(snap)) return true
        if (isInsideMapOrMediaSurface(snap, all) && !snap.isMediaControl()) return true
        if (shouldSkipComposeContrast(snap)) return true
        return false
    }

    /**
     * Determina se il controllo contrasto UI (icona) va saltato.
     */
    fun shouldSkipUiContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ): Boolean {
        // vop_info: l'icona è in realtà ad altissimo contrasto (drawable stroke scuro su bianco),
        // ma il campionamento screenshot può generare falsi positivi. Manteniamo solo il check
        // "manca contentDescription" tramite label/azioni, non il contrasto icona.
        if (viewIdShort(snap) == "vop_info") return true
        if (snap.isLikelyDecorative) return true
        if (isLottieAnimation(snap)) return true
        if (snap.isImageClass()) {
            if (!snap.isInteractiveClickable() && !snap.isFocusable) return true
            if (isIconInsideLabeledButton(snap, all)) return true
            val maxDim = maxOf(snap.bounds.width(), snap.bounds.height())
            val maxIconPx = (snap.minTouchTargetPx * 1.5f).toInt()
            if (maxDim > maxIconPx) return true
            if (screenAreaPx > 0) {
                val nodeArea = snap.bounds.width().toLong() * snap.bounds.height()
                if (nodeArea >= screenAreaPx * 0.03) return true
            }
        }
        return false
    }

    /**
     * Testo caption/metadati sovrapposto a illustrazioni o foto di card (non controlli UI).
     *
     * Es. "POLIZZA N. …" su card assicurativa con grafica decorativa: il campionamento
     * screenshot non riflette il contrasto percepito e genera falsi positivi.
     */
    fun isTextOverIllustratedBackground(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        screenAreaPx: Int,
    ): Boolean {
        if (!snap.hasVisibleText()) return false
        if (snap.isInteractiveClickable() || snap.isFocusable) return false
        if (snap.isHeading) return false
        val id = viewIdShort(snap)
        if (id.contains("policy", ignoreCase = true) ||
            id.contains("card_", ignoreCase = true) ||
            id.endsWith("_number", ignoreCase = true)
        ) {
            if (overlapsLargeIllustration(snap, all, screenAreaPx)) return true
        }
        if (snap.bounds.height() > snap.minTextHeightPx) return false
        return overlapsLargeIllustration(snap, all, screenAreaPx)
    }

    private fun overlapsLargeIllustration(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        screenAreaPx: Int,
    ): Boolean {
        if (screenAreaPx <= 0) return false
        val minImageArea = screenAreaPx * 0.035
        return all.any { other ->
            other != snap &&
                other.isImageClass() &&
                !other.isInteractiveClickable() &&
                other.bounds.width().toLong() * other.bounds.height() >= minImageArea.toLong() &&
                (
                    Rect.intersects(snap.bounds, other.bounds) ||
                        other.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
                    )
        }
    }

    /**
     * Riconosce un contesto di calendario Material (DatePicker) che genera rumore massivo.
     *
     * In Nexi/BFF la schermata COMUNICAZIONI usa un calendario con celle ripetute (`material_calendar_day`)
     * e griglia tappabile; applicare i controlli touch/spacing/focus a ciascuna cella produce falsi positivi.
     */
    fun isMaterialCalendarContext(screenTitle: String, snapshots: List<NodeSnapshot>): Boolean {
        if (!screenTitle.contains("COMUNICAZIONI", ignoreCase = true)) return false
        val dayCells = snapshots.count { viewIdShort(it) == "material_calendar_day" }
        // Se ci sono molte celle giorno, è quasi certamente il DatePicker Material.
        return dayCells >= 12
    }

    /**
     * Identifica una singola cella giorno del calendario Material.
     *
     * Oltre all'ID, usa le coordinate di collection (grid) come fallback quando l'ID non è esposto.
     */
    fun isMaterialCalendarDayCell(
        snap: NodeSnapshot,
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
    ): Boolean {
        if (!isMaterialCalendarContext(screenTitle, snapshots)) return false
        if (viewIdShort(snap) == "material_calendar_day") return true
        // Fallback: celle in griglia piccole e ripetute.
        val isGridCell = snap.collectionRow >= 0 && snap.collectionColumn >= 0
        val smallish = snap.bounds.width() <= snap.minTouchTargetPx * 2 &&
            snap.bounds.height() <= snap.minTouchTargetPx * 2
        return isGridCell && smallish && snap.isInteractiveClickable()
    }

    /**
     * Determina se un nodo appartiene al cluster del calendario Material (griglia + header + controlli mese).
     *
     * Serve per escludere i controlli che generano falsi positivi sistematici (label/role/custom action/touch)
     * su componenti Material complessi.
     */
    fun isMaterialCalendarRelatedNode(
        snap: NodeSnapshot,
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
    ): Boolean {
        if (!isMaterialCalendarContext(screenTitle, snapshots)) return false
        val id = viewIdShort(snap)
        if (id in setOf(
                "material_calendar_day",
                "gv_calendario",
                "ll_mese_precedente",
                "ll_mese_successivo",
                "iv_previous_month",
                "iv_next_month",
                "periodo_temp",
            )
        ) {
            return true
        }
        if (isMaterialCalendarDayCell(snap, screenTitle, snapshots)) return true

        // Fallback per nodi senza viewId (—) ma dentro la griglia calendario.
        val calendar = snapshots.firstOrNull { viewIdShort(it) == "gv_calendario" } ?: return false
        if (!Rect.intersects(calendar.bounds, snap.bounds)) return false
        val centerInside = calendar.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
        if (!centerInside) return false
        val smallish = snap.bounds.width() <= snap.minTouchTargetPx * 2 &&
            snap.bounds.height() <= snap.minTouchTargetPx * 2
        return smallish
    }

    /**
     * Evita FP su "Non raggiungibile con TalkBack" quando esiste un discendente/overlay focusabile.
     */
    fun hasFocusableOrEditableDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        return all.any { other ->
            other != snap &&
                snap.bounds.contains(other.bounds) &&
                (other.isFocusable || other.isEditable || other.isInteractiveClickable()) &&
                other.hasAccessibleName()
        }
    }

    /**
     * Verifica se il nodo appartiene a una tab strip (TabLayout custom Nexi).
     */
    fun isTabStripNode(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
        if (id in AppPrecisionProfiles.tabStripViewIds(packageName)) return true
        return snap.className.contains("TabView", true)
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
        val idsA = viewIdShort(a)
        val idsB = viewIdShort(b)
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
            viewIdShort(larger) in setOf("content", "container", "scrollview_port") ||
            larger.className.contains("RelativeLayout", true) ||
            larger.className.contains("FrameLayout", true)
        val smallerIsStructural = isScrollContainer(smaller) ||
            viewIdShort(smaller) in setOf("content", "scrollview_port")
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
                        viewIdShort(other) in setOf(
                            "recycler_distinte", "recycler_effetti", "recycler",
                            "content", "layout_content",
                        ) ||
                        (isKnownListTemplateId(other.viewId, packageName) && other.bounds.area() > snap.bounds.area() * 2)
                    )
        }
    }

    /**
     * Calcola l'area in pixel quadrati di un rettangolo Android.
     *
     * @receiver Rettangolo di cui calcolare l'area.
     * @return Area in pixel quadrati (larghezza × altezza).
     */
    private fun Rect.area(): Int = width() * height()

    private fun NodeSnapshot.area(): Int = bounds.width() * bounds.height()

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
        if (shouldSkipDrawerNode(a) || shouldSkipDrawerNode(b)) return true
        if (isTopBarControl(a) || isTopBarControl(b)) return true
        if (isInsideWebView(a, all) || isInsideWebView(b, all)) return true
        if (all.isNotEmpty() && screenArea > 0 &&
            isInsideDenseScrollGrid(a, all, screenArea) &&
            isInsideDenseScrollGrid(b, all, screenArea)
        ) {
            return true
        }
        val ids = setOf(viewIdShort(a), viewIdShort(b))
        if ("topbar_title" in ids && ids.any { it.startsWith("topbar") || it.startsWith("layout_topbar") }) {
            return true
        }
        val inTopBand = a.bounds.top < 400 && b.bounds.top < 400
        val topBarRelated = ids.any { it.startsWith("topbar") || it.startsWith("layout_topbar") }
        if (inTopBand && topBarRelated) return true
        return false
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
        val id = viewIdShort(snap)
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
        if (isDrawerScroll(snap)) return true
        if (!snap.isScrollable) return false
        if (isMainContentScroll(snap, screenArea, packageName)) return true
        val cls = snap.className.lowercase()
        val isKnownContainer = isScrollContainer(snap) ||
            cls.contains("recyclerview") ||
            cls.contains("viewpager")
        if (!isKnownContainer) return false
        if (hasLabeledDescendant(snap, all) || hasLabeledDescendantInScroll(snap, all)) return true
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

    /**
     * Determina se va segnalata un'azione personalizzata non etichettata sul nodo.
     *
     * Filtra nodi che hanno azioni custom senza nome accessibile, escludendo item lista, carousel,
     * widget home, container CTA etichettati e container scroll strutturali.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se l'azione custom non etichettata va segnalata; `false` altrimenti.
     */
    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (snap.unlabeledActionCount <= 0) return false
        if (snap.hasAccessibleName()) return false
        if (!snap.isInteractiveClickable() && !snap.isFocusable) return false
        if (isRecyclerListItem(snap, all)) return false
        if (isCarouselContentContainer(snap, all, packageName)) return false
        if (isHomeChartOrCtaWidget(snap, packageName)) return false
        val id = viewIdShort(snap)
        if (id.contains("select") || id.contains("selection")) return false
        if (id in setOf("multiple_slection", "checkbox_all") && snap.hasAccessibleName()) return false
        if (isCtaContainer(snap, packageName) && (hasTvCustomDescendant(snap, all) || hasLabeledDescendant(snap, all))) {
            return false
        }
        val cls = snap.className.lowercase()
        if (isScrollContainer(snap)) return false
        if (cls.contains("recyclerview") || cls.contains("scrollview") || cls.contains("viewpager")) {
            return false
        }
        if (viewIdShort(snap) in setOf("scrollview_port", "scroll", "card_home")) return false
        if (viewIdShort(snap) == "tv_custom") return false
        if (isBrandedCtaText(snap, all, packageName)) return false
        if (snap.isScrollable && hasLabeledDescendant(snap, all)) return false
        if (isLayoutContainer(snap.className) && hasLabeledDescendant(snap, all)) return false
        return true
    }

    /**
     * Verifica se il nodo è un'immagine probabilmente decorativa (non interattiva).
     *
     * @param snap Snapshot del nodo immagine da valutare.
     * @return `true` se l'immagine è considerata decorativa; `false` altrimenti.
     */
    fun isDecorative(snap: NodeSnapshot): Boolean {
        if (isTopBarControl(snap)) return false
        val id = viewIdShort(snap)
        if (id in setOf("vop_info", "dot_filter")) return false
        if (isLottieAnimation(snap) && !snap.isInteractiveClickable()) return true
        return snap.isLikelyDecorative
    }

    /**
     * Determina se il controllo etichetta per immagine decorativa va saltato.
     *
     * @param snap Snapshot del nodo immagine da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se il controllo etichetta decorativa va saltato; `false` altrimenti.
     */
    fun shouldSkipDecorativeLabeledCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (isLottieAnimation(snap) && !snap.isInteractiveClickable()) return true
        if (!snap.isImageClass()) return false
        if (isIconWithLabeledSibling(snap, all)) return true
        if (isLikelyNavigationImage(snap, all)) return true
        if (isTopBarControl(snap)) {
            val cd = snap.contentDescription?.trim().orEmpty()
            return cd.isNotBlank() && !isPoorAltText(cd)
        }
        return false
    }

    /**
     * Icone freccia/chiusura/info in header o controlli di navigazione: non sono decorative.
     *
     * Evita FP tipo "mese precedente/successivo" nei calendari Material o custom.
     */
    fun isLikelyNavigationImage(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        val cd = snap.contentDescription?.trim().orEmpty()
        if (cd.isBlank() || isPoorAltText(cd)) return false
        if (snap.isInteractiveClickable() || snap.isFocusable) return true
        // Se l'icona è dentro un contenitore cliccabile (tipico: LinearLayout wrapper), non è decorativa.
        return all.any { parent ->
            parent != snap &&
                parent.isInteractiveClickable() &&
                parent.bounds.contains(snap.bounds) &&
                parent.bounds.width() <= snap.minTouchTargetPx * 3 &&
                parent.bounds.height() <= snap.minTouchTargetPx * 3
        }
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
        shouldSkipDrawerNode(snap) ||
            shouldSkipHomeWidgetAnalysis(snap, all, packageName) ||
            isInlineTextLink(snap) ||
            isIconInsideLabeledButton(snap, all) ||
            isWideTapTarget(snap) ||
            isSkeletonPlaceholder(snap) ||
            isMapSurface(snap) ||
            (isInsideMapOrMediaSurface(snap, all) && !snap.isMediaControl()) ||
            shouldSkipComposeTouch(snap) ||
            // Le CTA "vere" (es. bottoni 65dp) sono già conformi: evitare rumore inutile.
            // Ma le CTA wrap_content (es. CustomViewButtonCta 27dp) vanno segnalate: NON skippare.
            (isCtaContainer(snap, packageName) && snap.bounds.height() >= snap.minTouchTargetPx)

    /**
     * Determina se il controllo dimensione testo piccolo va saltato per il nodo.
     *
     * @param snap Snapshot del nodo testo da valutare.
     * @param viewport Rettangolo del viewport visibile stimato.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo testo piccolo va saltato; `false` altrimenti.
     */
    fun shouldSkipSmallTextCheck(snap: NodeSnapshot, viewport: Rect = android.graphics.Rect(), packageName: String = ""): Boolean {
        if (shouldSkipDrawerNode(snap)) return true
        if (isSkeletonPlaceholder(snap)) return true
        if (!viewport.isEmpty && isOffScreenOrMarginalNode(snap, viewport, packageName)) return true
        if (snap.className.contains("Toolbar", true)) return true
        if (snap.text?.length == 1) return true
        return snap.bounds.height() >= snap.minTextHeightPx * 0.85
    }

    /**
     * Verifica se un testo alternativo (alt text) è di qualità insufficiente.
     *
     * Identifica descrizioni generiche o placeholder (`image`, `icon`, `logo`, nomi file, ecc.)
     * che non forniscono informazione utile all'utente.
     *
     * @param text Testo alternativo da valutare.
     * @return `true` se l'alt text è considerato scadente; `false` altrimenti.
     */
    fun isPoorAltText(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.length < 2) return true
        val bad = setOf(
            "image", "img", "icon", "logo", "photo", "picture", "bitmap",
            "immagine", "foto", "icona", "logo",
        )
        if (bad.contains(t)) return true
        if (t.matches(Regex("""^(img|image|photo|icon)[-_]?\d*\.?(png|jpg|jpeg|webp|gif)?$"""))) return true
        if (t.matches(Regex("""^[a-z0-9_-]+\.(png|jpg|jpeg|webp)$"""))) return true
        return false
    }

    /**
     * Verifica se hint, testo o content description indicano un campo obbligatorio.
     *
     * @param hint Testo hint del campo, se presente.
     * @param text Testo visibile del nodo, se presente.
     * @param contentDescription Content description del nodo, se presente.
     * @return `true` se almeno uno dei testi indica un campo obbligatorio; `false` altrimenti.
     */
    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?): Boolean {
        val combined = listOfNotNull(hint, text, contentDescription).joinToString(" ").lowercase()
        return combined.contains("obbligatorio") || combined.contains("required") || combined.contains("*")
    }

    /**
     * Verifica se una className Android corrisponde a un container di layout strutturale.
     *
     * @param className Nome completo della classe del componente UI.
     * @return `true` se la classe rappresenta un container layout; `false` altrimenti.
     */
    fun isLayoutContainer(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("layout") ||
            lower.contains("viewgroup") ||
            lower.contains("constraint") ||
            lower.contains("coordinator") ||
            lower.contains("drawer")
    }

    // ── Pattern piattaforma Android (generici, non legati a un'app) ──────────

    private val MAP_SURFACE_MARKERS = listOf("mapview", "googlemap", "maps.", "maplibre", "heremap")
    private val MEDIA_PLAYER_MARKERS = listOf("playerview", "styledplayerview", "videoview", "exoplayer")
    private val MODAL_OVERLAY_MARKERS = listOf("dialog", "bottomsheet", "popupwindow", "alertdialog", "modalbottomsheet")
    private val SKELETON_ID_MARKERS = listOf("skeleton", "shimmer", "placeholder", "loading", "stub", "skel")
    private val LOTTIE_MARKERS = listOf("lottie", "lottieanimationview")

    /** Superficie mappa (MapView, GoogleMap embed): i marker interni non sono controlli UI standard. */
    fun isMapSurface(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        val id = viewIdShort(snap).lowercase()
        return MAP_SURFACE_MARKERS.any { cls.contains(it) } ||
            (id.contains("map") && snap.bounds.width() > 200 && snap.bounds.height() > 200)
    }

    /** Area di riproduzione video (ExoPlayer, VideoView): contenuto dinamico senza live region atteso. */
    fun isMediaPlayerSurface(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        if (cls.contains("mediacontroller")) return false
        return MEDIA_PLAYER_MARKERS.any { cls.contains(it) }
    }

    /** Animazione Lottie non interattiva: decorativa, contrasto non significativo. */
    fun isLottieAnimation(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        val id = viewIdShort(snap).lowercase()
        return LOTTIE_MARKERS.any { cls.contains(it) || id.contains(it) }
    }

    /** Placeholder skeleton/shimmer durante il caricamento. */
    fun isSkeletonPlaceholder(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap).lowercase()
        if (SKELETON_ID_MARKERS.any { id.contains(it) }) return true
        val cls = snap.className.lowercase()
        return cls.contains("shimmer") || cls.contains("skeleton") || cls.contains("placeholder")
    }

    /** Host Jetpack Compose (ComposeView o nodo semantics). */
    fun isComposeHost(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        return cls.contains("composeview") || cls.contains("abstractcomposeview") || cls.contains("androidx.compose")
    }

    /** Nodo semantics Compose senza viewId: contrasto screenshot inaffidabile. */
    fun shouldSkipComposeContrast(snap: NodeSnapshot): Boolean {
        if (!snap.viewId.isNullOrBlank()) return false
        return isComposeHost(snap) || snap.className.contains("Semantics", true)
    }

    /**
     * Touch target su semantics Compose senza viewId e senza ruolo interattivo esplicito:
     * spesso spacing/layout interno, non un controllo autonomo.
     */
    fun shouldSkipComposeTouch(snap: NodeSnapshot): Boolean {
        if (!snap.viewId.isNullOrBlank()) return false
        if (!isComposeHost(snap) && !snap.className.contains("Semantics", true)) return false
        if (snap.isEditable || (snap.isInteractiveClickable() && snap.hasAccessibleName())) return false
        return snap.bounds.width() < snap.minTouchTargetPx || snap.bounds.height() < snap.minTouchTargetPx
    }

    /** Bounds del modal/bottom sheet dominante, se presente. */
    fun findModalOverlayBounds(all: List<NodeSnapshot>): Rect? {
        val viewport = estimateViewport(all)
        if (viewport.isEmpty) return null
        val screenArea = viewport.width() * viewport.height().toFloat()
        return all
            .filter { snap ->
                val cls = snap.className.lowercase()
                val id = viewIdShort(snap).lowercase()
                val isModal = MODAL_OVERLAY_MARKERS.any { cls.contains(it) } ||
                    id.contains("bottom_sheet") || id.contains("dialog") || id.contains("modal")
                isModal && snap.bounds.width() * snap.bounds.height() >= screenArea * 0.35f
            }
            .maxByOrNull { it.bounds.width() * it.bounds.height() }
            ?.bounds
    }

    /** Nodo dietro un overlay modale a schermo intero: non analizzare (evita FP su sfondo oscurato). */
    fun isObscuredByModalOverlay(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        val modal = findModalOverlayBounds(all) ?: return false
        val viewport = estimateViewport(all)
        val screenArea = viewport.width() * viewport.height().toFloat()
        if (modal.width() * modal.height() < screenArea * 0.45f) return false
        return !modal.contains(snap.bounds.centerX(), snap.bounds.centerY())
    }

    fun isInsideMapOrMediaSurface(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { host ->
            host != snap &&
                (isMapSurface(host) || isMediaPlayerSurface(host)) &&
                host.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
        }

    fun isInsideWebView(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { host ->
            host != snap &&
                host.isWebView() &&
                host.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
        }

    /**
     * Target cliccabile semantico (pulsante, switch, checkbox) — esclude layout container clickable.
     * Usato per overlap/spacing: evita FP su FrameLayout/ScrollView clickable senza perdere Button reali.
     */
    fun isSemanticClickTarget(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable()) return false
        return !isClickableLayoutShell(snap)
    }

    /**
     * Contenitore layout reso clickable (card intera, riga lista) — non un controllo UI autonomo.
     */
    fun isClickableLayoutShell(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable()) return false
        val cls = snap.className
        if (cls.contains("Button", ignoreCase = true) ||
            cls.contains("Chip", ignoreCase = true) ||
            snap.isCheckable
        ) {
            return false
        }
        val id = viewIdShort(snap)
        if (id in setOf("gridview", "recycler", "scrollview_port", "scroll", "content")) return true
        return cls.contains("Layout", ignoreCase = true) ||
            cls.contains("ViewGroup", ignoreCase = true) ||
            cls.contains("ScrollView", ignoreCase = true) ||
            cls.contains("RecyclerView", ignoreCase = true) ||
            cls.contains("GridView", ignoreCase = true) ||
            (snap.isScrollable && snap.bounds.area() > snap.minTouchTargetPx * snap.minTouchTargetPx * 6)
    }

    /**
     * Overlap tra shell layout e discendente strutturale (non doppio target reale).
     * Mantiene segnalazioni card+Button quando il controllo interno è un vero pulsante.
     */
    fun isLayoutShellOverlap(a: NodeSnapshot, b: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!Rect.intersects(a.bounds, b.bounds)) return false
        val (shell, other) = when {
            isClickableLayoutShell(a) && !isClickableLayoutShell(b) -> a to b
            isClickableLayoutShell(b) && !isClickableLayoutShell(a) -> b to a
            else -> return false
        }
        if (!shell.bounds.contains(other.bounds.centerX(), other.bounds.centerY())) return false
        val otherIsRealControl = other.className.contains("Button", ignoreCase = true) ||
            other.className.contains("ImageButton", ignoreCase = true)
        if (otherIsRealControl) return false
        return isScrollContainer(shell) ||
            shell.className.contains("FrameLayout", ignoreCase = true) ||
            shell.className.contains("ConstraintLayout", ignoreCase = true) ||
            shell.bounds.area() > other.bounds.area() * 2
    }

    /** Nodo dentro RecyclerView/GridView/ScrollView ampio (griglia densa — spacing intenzionale). */
    fun isInsideDenseScrollGrid(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int): Boolean {
        if (screenArea <= 0) return false
        return all.any { host ->
            host != snap &&
                host.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY()) &&
                host.bounds.area() > screenArea * 0.22f &&
                (
                    host.isScrollable ||
                        host.className.contains("RecyclerView", ignoreCase = true) ||
                        host.className.contains("GridView", ignoreCase = true) ||
                        host.className.contains("ScrollView", ignoreCase = true)
                    )
        }
    }

    /**
     * Salta l'analisi per nodi che generano rumore strutturale su pattern UI comuni.
     * I controlli interattivi reali (CTA, campi, media controls) restano analizzati.
     */
    fun shouldSkipPlatformNoiseAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (isSkeletonPlaceholder(snap)) return true
        if (isLottieAnimation(snap) && !snap.isInteractiveClickable()) return true
        if (isObscuredByModalOverlay(snap, all)) return true
        if (isMapSurface(snap) && !snap.isMediaControl()) return true
        if (isMediaPlayerSurface(snap)) return true
        if (isInsideMapOrMediaSurface(snap, all) && !snap.isMediaControl()) return true
        if (isInsideWebView(snap, all) && snap.viewId.isNullOrBlank() && !snap.isInteractiveClickable()) return true
        return false
    }

    /**
     * WebView barrier: segnala solo WebView grandi senza figli a11y e senza nodi accessibili sovrapposti.
     */
    fun shouldReportWebViewBarrier(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isWebView()) return false
        if (snap.bounds.width() <= 100 || snap.bounds.height() <= 100) return false
        if (snap.childCount > 0) return false
        val hasAccessibleOverlap = all.any { other ->
            other != snap &&
                other.hasAccessibleName() &&
                android.graphics.Rect.intersects(snap.bounds, other.bounds) &&
                snap.bounds.contains(other.bounds.centerX(), other.bounds.centerY())
        }
        return !hasAccessibleOverlap
    }
}
