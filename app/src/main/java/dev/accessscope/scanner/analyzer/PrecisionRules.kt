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
        return thinVertical || thinHorizontal
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
        val chartText = AppPrecisionProfiles.homeChartTextIds(packageName)
        if (id == "last_30" || id == "last_30_negative") {
            if (!snap.contentDescription.isNullOrBlank()) return true
            if (isHomeScreenContext(all, packageName)) return true
        }
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
    fun shouldSkipTouchSpacingBetween(a: NodeSnapshot, b: NodeSnapshot): Boolean {
        if (shouldSkipDrawerNode(a) || shouldSkipDrawerNode(b)) return true
        if (isTopBarControl(a) || isTopBarControl(b)) return true
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
        if (!snap.isImageClass()) return false
        if (isIconWithLabeledSibling(snap, all)) return true
        if (isTopBarControl(snap)) {
            val cd = snap.contentDescription?.trim().orEmpty()
            return cd.isNotBlank() && !isPoorAltText(cd)
        }
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
        shouldSkipDrawerNode(snap) ||
            shouldSkipHomeWidgetAnalysis(snap, all, packageName) ||
            isInlineTextLink(snap) ||
            isIconInsideLabeledButton(snap, all) ||
            isWideTapTarget(snap) ||
            isCtaContainer(snap, packageName)

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
}
