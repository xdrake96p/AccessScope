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
import dev.accessscope.scanner.analyzer.title.TitleCache
import dev.accessscope.scanner.analyzer.title.TitleCandidate
import dev.accessscope.scanner.analyzer.title.TitleCandidateLogic
import dev.accessscope.scanner.analyzer.title.TitleScreenDetection
import dev.accessscope.scanner.analyzer.title.TitleTreeWalker
import dev.accessscope.scanner.analyzer.title.NexiTitleHeuristics
import dev.accessscope.scanner.util.AppFileLogger

/**
 * Risolve titoli di schermata umanamente leggibili dall'albero di accessibilità.
 *
 * Applica una catena di strategie in ordine di priorità: testo dell'evento, pane title,
 * schermata PIN, modali, top bar (stabile in scroll), titoli di sezione, profilo Nexi opzionale,
 * heading prominenti, descrizione contenuto, nome activity e infine cache per pacchetto.
 * Espone anche helper per distinguere drawer, overlay transitori e schermate PIN.
 */
object ScreenTitleResolver {

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
        val rootIds = TitleTreeWalker.collectViewIdShorts(root)

        fun storeAndReturn(title: String): String {
            if (title != "Schermata" && title != "Menu" && packageKey.isNotBlank() &&
                TitleCache.shouldCacheTitle(root, title, rootIds)
            ) {
                TitleCache.put(packageKey, title)
            }
            return title
        }

        val candidates = mutableListOf<TitleCandidate>()

        event.text?.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (!TitleCandidateLogic.looksLikeAmount(it)) {
                candidates += TitleCandidate(TitleCandidateLogic.humanizeTitle(it), 85, "event_text")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root.paneTitle?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
                candidates += TitleCandidate(TitleCandidateLogic.humanizeTitle(it), 95, "pane_title")
            }
        }

        TitleTreeWalker.findPinScreen(root)?.let { candidates += TitleCandidate(it, 96, "pin") }
        TitleTreeWalker.findModalTitle(root)?.let { candidates += TitleCandidate(it, 88, "modal") }

        TitleCandidateLogic.inferTitleFromContentMarkers(rootIds)?.let {
            candidates += TitleCandidate(it, 100, "content_markers")
        }

        NexiTitleHeuristics.findByDistinctiveIds(root)?.let {
            candidates += TitleCandidate(it, 82, "distinctive_ids")
        }

        TitleTreeWalker.findTopBarTitle(root)?.let { toolbar ->
            val weight = when {
                !TitleCandidateLogic.isToolbarConsistentWithContent(toolbar, rootIds) -> 20
                rootIds.count { it.startsWith("nav_") } >= 3 -> 45
                else -> 72
            }
            candidates += TitleCandidate(toolbar, weight, "toolbar")
        }

        TitleTreeWalker.findSectionTitle(root)?.let { candidates += TitleCandidate(it, 74, "section_title") }
        NexiTitleHeuristics.findKnownNexiTitles(root)?.let { candidates += TitleCandidate(it, 76, "nexi_text") }
        TitleTreeWalker.findProminentHeading(root)?.let { candidates += TitleCandidate(it, 58, "heading") }

        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (!TitleCandidateLogic.looksLikeAmount(it)) {
                candidates += TitleCandidate(TitleCandidateLogic.humanizeTitle(it), 52, "content_desc")
            }
        }

        val activityName = event.className?.toString()?.substringAfterLast('.').orEmpty()
        if (activityName.isNotBlank() && !activityName.equals("View", ignoreCase = true)) {
            val activityWeight = if (TitleCandidateLogic.isGenericActivityName(activityName)) 28 else 64
            candidates += TitleCandidate(TitleCandidateLogic.humanizeActivityName(activityName), activityWeight, "activity")
        }

        if (packageKey.isNotBlank()) {
            TitleCache.get(packageKey)?.let { cached ->
                if (TitleCache.canReuseCachedTitle(root, cached, rootIds)) {
                    candidates += TitleCandidate(cached, 38, "cache")
                }
            }
        }

        val chosen = TitleCandidateLogic.pickBestTitle(candidates, rootIds)
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

    /**
     * Inferisce il titolo schermata dai viewId del contenuto visibile (generico multi-app).
     */
    internal fun inferTitleFromContentMarkers(ids: Set<String>): String? =
        TitleCandidateLogic.inferTitleFromContentMarkers(ids)

    /** Toolbar coerente col fingerprint del contenuto (evita tab host fuorviante). */
    internal fun isToolbarConsistentWithContent(toolbarTitle: String, ids: Set<String>): Boolean =
        TitleCandidateLogic.isToolbarConsistentWithContent(toolbarTitle, ids)

    internal fun pickBestTitle(candidates: List<TitleCandidate>, ids: Set<String>): String? =
        TitleCandidateLogic.pickBestTitle(candidates, ids)

    internal fun titleCandidateForTest(title: String, weight: Int, source: String): TitleCandidate =
        TitleCandidateLogic.titleCandidate(title, weight, source)

    /**
     * Raccoglie i suffissi corti dei viewId presenti nel sottoalbero radice.
     *
     * Utile per filtrare drawer, home e altre schermate in base ai marker di layout,
     * senza esporre l'intero nome qualificato della risorsa.
     *
     * @param root Nodo radice del sottoalbero da ispezionare.
     * @return Insieme dei suffissi viewId (parte dopo l'ultimo `/`), in minuscolo.
     */
    fun rootViewIds(root: AccessibilityNodeInfo): Set<String> = TitleTreeWalker.collectViewIdShorts(root)

    /**
     * Verifica se la radice rappresenta solo il menu laterale (drawer), senza contenuto principale.
     *
     * Considera drawer-only una finestra con almeno due viewId `nav_*` e nessun marker
     * del contenuto principale dell'app.
     *
     * @param root Nodo radice della finestra o del sottoalbero da valutare.
     * @return `true` se l'albero contiene prevalentemente navigazione laterale senza fragment principale.
     */
    fun isDrawerOnlyRoot(root: AccessibilityNodeInfo): Boolean =
        TitleScreenDetection.isDrawerOnlyRoot(root)

    /**
     * Svuota la cache dei titoli per pacchetto.
     *
     * @param packageName Nome del pacchetto da rimuovere dalla cache; se `null` o blank,
     *   viene svuotata l'intera mappa.
     */
    fun clearTitleCache(packageName: String? = null) {
        TitleCache.clear(packageName)
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
    fun isTransientOverlay(root: AccessibilityNodeInfo): Boolean =
        TitleScreenDetection.isTransientOverlay(root)

    /**
     * Indica se la radice corrisponde a una schermata di inserimento PIN.
     *
     * Helper pubblico usato per la prioritizzazione tra finestre multiple quando più
     * overlay o activity sono visibili contemporaneamente.
     *
     * @param root Nodo radice dell'albero da ispezionare.
     * @return `true` se viene individuata una schermata PIN.
     */
    fun isPinScreen(root: AccessibilityNodeInfo): Boolean =
        TitleScreenDetection.isPinScreen(root)
}
