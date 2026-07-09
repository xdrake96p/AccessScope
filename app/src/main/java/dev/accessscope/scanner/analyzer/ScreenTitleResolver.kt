/**
 * Risoluzione del titolo di schermata a partire dall'albero di accessibilità Android.
 *
 * Questo modulo analizza [AccessibilityNodeInfo] e [AccessibilityEvent] per produrre
 * un'etichetta leggibile della schermata corrente, con euristiche specifiche per l'app Nexi
 * (sezioni bancarie, PIN, drawer, overlay transitori). I titoli risolti possono essere
 * memorizzati in cache per pacchetto e riutilizzati quando l'albero non espone un titolo
 * esplicito.
 */
package dev.accessscope.scanner.analyzer

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.util.AppFileLogger
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Risolve titoli di schermata umanamente leggibili dall'albero di accessibilità.
 *
 * Applica una catena di strategie in ordine di priorità: testo dell'evento, pane title,
 * schermata PIN, modali, top bar (stabile in scroll), titoli di sezione, profilo Nexi opzionale,
 * heading prominenti, descrizione contenuto, nome activity e infine cache per pacchetto.
 * Espone anche helper per distinguere drawer, overlay transitori e schermate PIN.
 */
object ScreenTitleResolver {

    private val lastTitleByPackage = ConcurrentHashMap<String, String>()

    private val KNOWN_NEXI_SECTION_TITLES = setOf(
        "DISTINTE",
        "BONIFICI",
        "EFFETTI IN SCADENZA",
        "EFFETTI",
        "NUOVO PAGAMENTO",
        "DISPOSIZIONI ONLINE",
        "DISPOSIZIONI ISTANTANEE",
        "DISPOSIZIONI",
        "RUBRICA",
        "AUTORIZZA DISTINTE",
        "PAGA EFFETTI",
        "INSOLUTI",
        "ARCHIVIO DISTINTE",
        "ARCHIVIO EFFETTI",
        "COMUNICAZIONI AZIENDALI",
        "NOTIFICHE",
        "IMPOSTAZIONI",
        "AIUTO E CONTATTI",
    )

    /**
     * Determina il titolo della schermata corrente analizzando radice e evento di accessibilità.
     *
     * Prova le varie euristiche in sequenza; quando trova un candidato valido lo normalizza,
     * lo memorizza in cache (se appropriato) e lo restituisce. Se nessuna strategia ha successo
     * e la cache non è riutilizzabile, restituisce il fallback `"Schermata"`.
     *
     * @param root Nodo radice dell'albero di accessibilità da analizzare.
     * @param event Evento di accessibilità associato alla finestra o al contenuto corrente.
     * @return Titolo umanamente leggibile della schermata, oppure `"Schermata"` come fallback.
     */
    fun resolve(root: AccessibilityNodeInfo, event: AccessibilityEvent): String {
        val packageKey = root.packageName?.toString()
            ?: event.packageName?.toString()
            ?: ""
        val rootIds = collectViewIdShorts(root)

        fun storeAndReturn(title: String): String {
            if (title != "Schermata" && title != "Menu" && packageKey.isNotBlank() &&
                shouldCacheTitle(root, title, rootIds)
            ) {
                lastTitleByPackage[packageKey] = title
            }
            return title
        }

        val candidates = mutableListOf<TitleCandidate>()

        event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (!looksLikeAmount(it)) candidates += TitleCandidate(humanizeTitle(it), 85, "event_text")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root.paneTitle?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
                candidates += TitleCandidate(humanizeTitle(it), 95, "pane_title")
            }
        }

        findPinScreen(root)?.let { candidates += TitleCandidate(it, 96, "pin") }
        findModalTitle(root)?.let { candidates += TitleCandidate(it, 88, "modal") }

        inferTitleFromContentMarkers(rootIds)?.let {
            candidates += TitleCandidate(it, 100, "content_markers")
        }

        findByDistinctiveIds(root)?.let {
            candidates += TitleCandidate(it, 82, "distinctive_ids")
        }

        findTopBarTitle(root)?.let { toolbar ->
            val weight = when {
                !isToolbarConsistentWithContent(toolbar, rootIds) -> 20
                rootIds.count { it.startsWith("nav_") } >= 3 -> 45
                else -> 72
            }
            candidates += TitleCandidate(toolbar, weight, "toolbar")
        }

        findSectionTitle(root)?.let { candidates += TitleCandidate(it, 74, "section_title") }
        findKnownNexiTitles(root)?.let { candidates += TitleCandidate(it, 76, "nexi_text") }
        findProminentHeading(root)?.let { candidates += TitleCandidate(it, 58, "heading") }

        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (!looksLikeAmount(it)) candidates += TitleCandidate(humanizeTitle(it), 52, "content_desc")
        }

        val activityName = event.className?.toString()?.substringAfterLast('.').orEmpty()
        if (activityName.isNotBlank() && !activityName.equals("View", ignoreCase = true)) {
            val activityWeight = if (isGenericActivityName(activityName)) 28 else 64
            candidates += TitleCandidate(humanizeActivityName(activityName), activityWeight, "activity")
        }

        if (packageKey.isNotBlank()) {
            lastTitleByPackage[packageKey]?.let { cached ->
                if (canReuseCachedTitle(root, cached, rootIds)) {
                    candidates += TitleCandidate(cached, 38, "cache")
                }
            }
        }

        val chosen = pickBestTitle(candidates, rootIds)
        if (chosen != null) {
            val toolbar = candidates.firstOrNull { it.source == "toolbar" }?.title
            val content = candidates.firstOrNull { it.source == "content_markers" }?.title
            if (toolbar != null && content != null && !toolbar.equals(content, ignoreCase = true)) {
                AppFileLogger.info(
                    "ScreenTitle",
                    "resolved toolbar='$toolbar' content='$content' chosen='$chosen' ids=${rootIds.take(8)}",
                )
            }
            return storeAndReturn(chosen)
        }

        return "Schermata"
    }

    internal data class TitleCandidate(val title: String, val weight: Int, val source: String)

    /**
     * Inferisce il titolo schermata dai viewId del contenuto visibile (generico multi-app).
     */
    internal fun inferTitleFromContentMarkers(ids: Set<String>): String? {
        if (ids.isEmpty()) return null

        val landingMarkers = setOf("titlehello", "titlehelp", "buttonaltro", "productslist", "policyname", "policynumber")
        val documentMarkers = setOf(
            "label_documents_element_policy_nr", "recycler_documents", "documents_list",
            "documentcard", "label_documents",
        )
        val rubricaMarkers = setOf("labelcontacts", "edt_ragione_sociale", "text_input_ragione_sociale", "iban_account")

        val landingHits = ids.count { it in landingMarkers }
        val documentHits = ids.count { it in documentMarkers || (it == "name" && ids.contains("numero")) }
        val rubricaHits = ids.count { it in rubricaMarkers }

        if (landingHits >= 2 && documentHits == 0) return "Home"
        if (documentHits >= 2 && landingHits == 0) return "I miei documenti"
        if (rubricaHits >= 2) return "RUBRICA"
        if (hasHomeMarkers(ids)) return findTopBarTitleFromIds(ids) ?: "Home"
        return null
    }

    /** Toolbar coerente col fingerprint del contenuto (evita tab host fuorviante). */
    internal fun isToolbarConsistentWithContent(toolbarTitle: String, ids: Set<String>): Boolean {
        val toolbar = toolbarTitle.lowercase()
        val contentTitle = inferTitleFromContentMarkers(ids) ?: return true

        if (contentTitle.equals("Home", ignoreCase = true)) {
            val docToolbar = toolbar.contains("document") || toolbar.contains("documenti")
            val landingPresent = ids.any { it in setOf("titlehello", "buttonaltro", "productslist", "policyname") }
            if (docToolbar && landingPresent) return false
        }
        if (contentTitle.equals("I miei documenti", ignoreCase = true)) {
            val landingPresent = ids.any { it in setOf("titlehello", "buttonaltro", "productslist", "policyname") }
            if (landingPresent) return false
        }
        if (contentTitle.equals("RUBRICA", ignoreCase = true) &&
            !toolbar.contains("rubric")
        ) {
            return false
        }
        return true
    }

    private val SOURCE_PRIORITY = mapOf(
        "content_markers" to 100,
        "pane_title" to 90,
        "pin" to 88,
        "modal" to 85,
        "distinctive_ids" to 80,
        "toolbar" to 70,
        "section_title" to 65,
        "nexi_text" to 60,
        "event_text" to 55,
        "activity" to 40,
        "heading" to 35,
        "content_desc" to 30,
        "cache" to 10,
    )

    internal fun pickBestTitle(candidates: List<TitleCandidate>, ids: Set<String>): String? {
        if (candidates.isEmpty()) return null
        return candidates
            .filter { it.title.isNotBlank() && it.title != "Schermata" && !isGenericScreenTitle(it.title) }
            .maxWithOrNull(
                compareBy<TitleCandidate> { it.weight }
                    .thenBy { SOURCE_PRIORITY[it.source] ?: 0 }
                    .thenBy { it.title.length },
            )
            ?.title
            ?.let { humanizeTitle(it) }
    }

    /** Titoli generici che non aiutano a distinguere sezioni nel report. */
    private fun isGenericScreenTitle(title: String): Boolean {
        val normalized = title.trim().lowercase()
        return normalized in setOf("menu", "indietro", "back", "close", "chiudi", "annulla", "ok")
    }

    internal fun titleCandidateForTest(title: String, weight: Int, source: String) =
        TitleCandidate(title, weight, source)

    private fun isGenericActivityName(name: String): Boolean {
        val simple = name
            .removeSuffix("Activity")
            .removeSuffix("Fragment")
            .removeSuffix("Screen")
            .lowercase()
        return simple in GENERIC_ACTIVITY_NAMES || simple.length <= 4
    }

    private val GENERIC_ACTIVITY_NAMES = setOf(
        "main", "home", "host", "container", "base", "wrapper", "shell",
        "flutter", "navigation", "single", "launcher", "root",
    )

    private fun findTopBarTitleFromIds(ids: Set<String>): String? = null

    private fun shouldCacheTitle(root: AccessibilityNodeInfo, title: String, ids: Set<String>): Boolean {
        if (title.equals("Ultimi insoluti", ignoreCase = true) && hasHomeMarkers(ids)) return false
        if (ids.any { it.startsWith("nav_") }) return false
        val contentTitle = inferTitleFromContentMarkers(ids)
        if (contentTitle != null && !title.equals(contentTitle, ignoreCase = true) &&
            !isToolbarConsistentWithContent(title, ids)
        ) {
            return false
        }
        return true
    }

    /**
     * Raccoglie i suffissi corti dei viewId presenti nel sottoalbero radice.
     *
     * Utile per filtrare drawer, home e altre schermate in base ai marker di layout,
     * senza esporre l'intero nome qualificato della risorsa.
     *
     * @param root Nodo radice del sottoalbero da ispezionare.
     * @return Insieme dei suffissi viewId (parte dopo l'ultimo `/`), in minuscolo.
     */
    fun rootViewIds(root: AccessibilityNodeInfo): Set<String> = collectViewIdShorts(root)

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
        val ids = collectViewIdShorts(root)
        val navCount = ids.count { it.startsWith("nav_") }
        if (navCount < 2) return false
        return !ids.any { it in MAIN_CONTENT_MARKER_IDS }
    }

    private val MAIN_CONTENT_MARKER_IDS = setOf(
        "scrollview_port", "card_home", "recycler_distinte", "content_pagamento",
        "labelcontacts", "iban_account", "vop_info", "amount_effetti", "pin_pad_view",
    )

    /**
     * Verifica se un titolo in cache è ancora valido per il layout corrente.
     */
    private fun canReuseCachedTitle(root: AccessibilityNodeInfo, cached: String, ids: Set<String>): Boolean {
        if (ids.any { it.startsWith("nav_") }) return false
        if (cached.equals("Ultimi insoluti", ignoreCase = true) && hasHomeMarkers(ids)) return false
        if (cached.equals("Ultimi insoluti", ignoreCase = true) &&
            (ids.contains("recycler_distinte") || ids.contains("vop_info"))
        ) {
            return false
        }
        inferTitleFromContentMarkers(ids)?.let { contentTitle ->
            if (!cached.equals(contentTitle, ignoreCase = true) &&
                !isToolbarConsistentWithContent(cached, ids)
            ) {
                return false
            }
        }
        if (hasScrollableContent(root) && hasActivityChrome(ids)) {
            findTopBarTitle(root)?.let { top ->
                return top.equals(cached, ignoreCase = true)
            }
            return true
        }
        val fresh = findSectionTitle(root) ?: findKnownNexiTitles(root) ?: findByDistinctiveIds(root)
        if (fresh != null && !fresh.equals(cached, ignoreCase = true)) return false
        return true
    }

    /** True se l'albero contiene contenuto scrollabile (lista, scroll view). */
    private fun hasScrollableContent(root: AccessibilityNodeInfo): Boolean {
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

    /** Chrome strutturale dell'activity (toolbar, tab, bottom nav) — pattern generici Android. */
    private fun hasActivityChrome(ids: Set<String>): Boolean =
        ids.any { id ->
            id.contains("topbar") || id.contains("toolbar") || id.contains("tab_") ||
                id.contains("bottom_nav") || id.contains("navigation") || id.contains("nav_host") ||
                id.contains("action_bar") || id.contains("appbar") || id == "content"
        }

    /**
     * Controlla se l'insieme di viewId include marker tipici della schermata Home Nexi.
     *
     * @param ids Suffissi viewId raccolti dal sottoalbero corrente.
     * @return `true` se almeno un viewId corrisponde a [HOME_MARKER_IDS].
     */
    private fun hasHomeMarkers(ids: Set<String>): Boolean =
        ids.any { it in HOME_MARKER_IDS }

    private val HOME_MARKER_IDS = setOf(
        "entrate_home", "uscite_home", "card_home", "scrollview_port", "rotate_display",
    )

    /**
     * Svuota la cache dei titoli per pacchetto.
     *
     * @param packageName Nome del pacchetto da rimuovere dalla cache; se `null` o blank,
     *   viene svuotata l'intera mappa [lastTitleByPackage].
     */
    fun clearTitleCache(packageName: String? = null) {
        if (packageName.isNullOrBlank()) {
            lastTitleByPackage.clear()
        } else {
            lastTitleByPackage.remove(packageName)
        }
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
        val ids = collectViewIdShorts(root)
        val hasLogo = "logo" in ids
        val hasNav = ids.any { it in NAV_HINT_IDS }
        val hasKnownTitle = findSectionTitle(root) != null ||
            findKnownNexiTitles(root) != null ||
            findByDistinctiveIds(root) != null
        return hasLogo && !hasNav && !hasKnownTitle
    }

    /**
     * Indica se la radice corrisponde a una schermata di inserimento PIN.
     *
     * Helper pubblico usato per la prioritizzazione tra finestre multiple quando più
     * overlay o activity sono visibili contemporaneamente.
     *
     * @param root Nodo radice dell'albero da ispezionare.
     * @return `true` se [findPinScreen] individua una schermata PIN.
     */
    fun isPinScreen(root: AccessibilityNodeInfo): Boolean = findPinScreen(root) != null

    /**
     * Cerca nel sottoalbero indicatori di schermata PIN (tastierino numerico, testi, viewId).
     *
     * @param root Nodo radice da attraversare in ampiezza.
     * @return `"Inserisci PIN"` se rilevata una schermata PIN, altrimenti `null`.
     */
    private fun findPinScreen(root: AccessibilityNodeInfo): String? {
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
    private fun findTopBarTitle(root: AccessibilityNodeInfo): String? {
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
                if (!titleText.isNullOrBlank() && !looksLikeAmount(titleText) &&
                    !isKnownSectionTitle(titleText)
                ) {
                    return humanizeTitle(titleText)
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
    private fun findSectionTitle(root: AccessibilityNodeInfo): String? {
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
                !looksLikeAmount(text) && bounds.top <= sectionBandBottom
            ) {
                return humanizeTitle(text)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    /**
     * Risolve titoli di sezione Nexi noti tramite testo in fascia alta o viewId `content_pagamento`.
     *
     * Esclude il widget «Ultimi insoluti» quando si è in home e gestisce il fallback
     * [findTitleNearContentPagamento] per schermate di pagamento.
     *
     * @param root Nodo radice del sottoalbero.
     * @return Titolo di sezione Nexi umanizzato, oppure `null`.
     */
    private fun findKnownNexiTitles(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val sectionBandBottom = screenBounds.top + (screenBounds.height() * 0.25f).toInt()
        val rootIds = collectViewIdShorts(root)
        val onHome = hasHomeMarkers(rootIds)

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var contentPagamentoTitle: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString()?.trim().orEmpty()
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val normalized = text.uppercase()

            if (viewId.endsWith("/content_pagamento") && text.isNotBlank() && bounds.top <= sectionBandBottom) {
                contentPagamentoTitle = humanizeTitle(text)
            }

            if (text.isNotBlank() && bounds.top <= sectionBandBottom && text.length <= 80 &&
                !looksLikeAmount(text)
            ) {
                if (isHomeInsolutiWidgetTitle(text, viewId, onHome)) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                    continue
                }
                if (isKnownSectionTitle(text)) {
                    return humanizeTitle(text)
                }
                KNOWN_NEXI_SECTION_TITLES.firstOrNull { known ->
                    normalized.contains(known) || known.contains(normalized)
                }?.let { known ->
                    if (!(known == "INSOLUTI" && onHome)) {
                        return humanizeTitle(known)
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        if (contentPagamentoTitle != null) return contentPagamentoTitle

        return findTitleNearContentPagamento(root)
    }

    /**
     * Cerca un titolo di pagamento vicino al viewId `content_pagamento` nel sottoalbero.
     *
     * Dopo aver trovato `content_pagamento`, raccoglie candidati il cui testo contiene
     * «PAGAMENTO» o «NUOVO» e restituisce il primo valido.
     *
     * @param root Nodo radice del sottoalbero.
     * @return Primo titolo candidato umanizzato, oppure `null`.
     */
    private fun findTitleNearContentPagamento(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var foundPagamento = false
        val candidates = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            if (viewId.endsWith("/content_pagamento")) {
                foundPagamento = true
            }
            val text = node.text?.toString()?.trim().orEmpty()
            if (foundPagamento && text.isNotBlank() && text.length <= 80 && !looksLikeAmount(text)) {
                val normalized = text.uppercase()
                if (normalized.contains("PAGAMENTO") || normalized.contains("NUOVO")) {
                    candidates.add(humanizeTitle(text))
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return candidates.firstOrNull()
    }

    /**
     * Verifica se il testo corrisponde a un titolo di sezione Nexi nella lista nota.
     *
     * @param text Testo da confrontare (case-insensitive dopo normalizzazione).
     * @return `true` se il testo coincide o contiene una voce di [KNOWN_NEXI_SECTION_TITLES].
     */
    private fun isKnownSectionTitle(text: String): Boolean {
        val normalized = text.trim().uppercase()
        return KNOWN_NEXI_SECTION_TITLES.any { known ->
            normalized == known || normalized.contains(known)
        }
    }

    /**
     * Estrae il titolo da dialog, bottom sheet o altri contenitori modali.
     *
     * @param root Nodo radice; deve avere className indicativa di modale per essere considerato.
     * @return Titolo umanizzato del modale, oppure `null` se non modale o senza titolo.
     */
    private fun findModalTitle(root: AccessibilityNodeInfo): String? {
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
                text.isNotBlank() && text.length <= 80 && !looksLikeAmount(text)
            ) {
                return humanizeTitle(text)
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
    private fun findProminentHeading(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val topThreshold = screenBounds.top + (screenBounds.height() * 0.28f).toInt()
        val onHome = hasHomeMarkers(collectViewIdShorts(root))

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
            if (text.isBlank() || looksLikeAmount(text)) {
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                continue
            }
            if (isHomeInsolutiWidgetTitle(text, viewId, onHome)) {
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
                    best = humanizeTitle(text) to score
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return best?.first
    }

    /**
     * Determina se una stringa assomiglia a un importo o valuta piuttosto che a un titolo.
     *
     * @param text Testo da valutare.
     * @return `true` se [PrecisionRules.isCurrencyOrAmountText] classifica il testo come importo.
     */
    private fun looksLikeAmount(text: String): Boolean = PrecisionRules.isCurrencyOrAmountText(text)

    /**
     * Converte un nome di activity o fragment in un titolo leggibile.
     *
     * Rimuove suffissi comuni (`Activity`, `Fragment`, …), separa camelCase e applica [humanizeTitle].
     *
     * @param name Nome semplice della classe (es. `HomeActivity`).
     * @return Titolo umanizzato derivato dal nome classe.
     */
    private fun humanizeActivityName(name: String): String {
        val cleaned = name
            .removeSuffix("Activity")
            .removeSuffix("Fragment")
            .removeSuffix("Screen")
            .removeSuffix("Page")
        return humanizeTitle(
            cleaned.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").trim().ifBlank { name },
        )
    }

    /**
     * Normalizza spazi e trim di un titolo grezzo.
     *
     * @param title Stringa titolo eventualmente con spazi multipli.
     * @return Titolo con spazi collassati e bordi trimmati.
     */
    private fun humanizeTitle(title: String): String =
        title.trim().replace(Regex("\\s+"), " ")

    private val NAV_HINT_IDS = setOf(
        "topbar_title", "layout_topbar_icon_left", "layout_topbar_icon_right",
        "content_pagamento", "entrate_home", "uscite_home", "recycler_distinte",
        "labelcontacts", "rotate_display", "scrollview_port",
    )

    /**
     * Risolve il titolo da viewId distintivi Nexi quando heading e topbar non sono disponibili.
     *
     * Mappa combinazioni di viewId a sezioni note (Home, Rubrica, pagamenti, distinte, effetti,
     * insoluti). Non opera quando il drawer (`nav_*`) è visibile.
     *
     * @param root Nodo radice del sottoalbero.
     * @return Titolo di sezione inferito dai viewId, oppure `null`.
     */
    private fun findByDistinctiveIds(root: AccessibilityNodeInfo): String? {
        val ids = collectViewIdShorts(root)
        if (ids.isEmpty()) return null
        if (ids.any { it.startsWith("nav_") }) return null

        return when {
            hasHomeMarkers(ids) -> "Home"
            ids.contains("labelcontacts") || ids.contains("iban_account") -> "RUBRICA"
            ids.contains("content_pagamento") -> findTitleNearContentPagamento(root) ?: "NUOVO PAGAMENTO"
            ids.contains("recycler_distinte") && ids.contains("vop_info") -> "AUTORIZZA DISTINTE"
            ids.contains("amount_effetti") && ids.contains("causale") -> "PAGA EFFETTI"
            (ids.contains("txt_situazione") || ids.contains("amount_effetti")) &&
                ids.contains("see_all_insolved") && !hasHomeMarkers(ids) -> "Ultimi insoluti"
            ids.contains("tv_custom") && ids.contains("ll_custom") -> "NUOVO PAGAMENTO"
            else -> null
        }
    }

    /**
     * Identifica il titolo del widget insoluti in home, non una sezione dedicata.
     *
     * In home, «Ultimi insoluti» (viewId `insoluti_title` o testo equivalente) è un widget
     * e non deve essere trattato come titolo di navigazione verso la sezione Insoluti.
     *
     * @param text Testo del nodo candidato.
     * @param viewId ViewId completo o parziale del nodo.
     * @param onHome `true` se il layout corrente contiene marker della home.
     * @return `true` se il testo/viewId corrisponde al widget insoluti in home.
     */
    private fun isHomeInsolutiWidgetTitle(text: String, viewId: String, onHome: Boolean): Boolean {
        if (!onHome) return false
        if (viewId.endsWith("/insoluti_title")) return true
        return text.equals("Ultimi insoluti", ignoreCase = true)
    }

    /**
     * Attraversa il sottoalbero e raccoglie i suffissi corti di tutti i viewId presenti.
     *
     * @param root Nodo radice da visitare in ampiezza.
     * @return Insieme immutabile dei suffissi viewId (parte dopo `/`), in minuscolo.
     */
    private fun collectViewIdShorts(root: AccessibilityNodeInfo): Set<String> {
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
}
