package dev.accessscope.scanner.analyzer.title

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

internal object NexiTitleHeuristics {

    val KNOWN_NEXI_SECTION_TITLES = setOf(
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

    val NAV_HINT_IDS = setOf(
        "topbar_title", "layout_topbar_icon_left", "layout_topbar_icon_right",
        "content_pagamento", "entrate_home", "uscite_home", "recycler_distinte",
        "labelcontacts", "rotate_display", "scrollview_port",
    )

    val MAIN_CONTENT_MARKER_IDS = setOf(
        "scrollview_port", "card_home", "recycler_distinte", "content_pagamento",
        "labelcontacts", "iban_account", "vop_info", "amount_effetti", "pin_pad_view",
    )

    private val HOME_MARKER_IDS = setOf(
        "entrate_home", "uscite_home", "card_home", "scrollview_port", "rotate_display",
    )

    /**
     * Controlla se l'insieme di viewId include marker tipici della schermata Home Nexi.
     *
     * @param ids Suffissi viewId raccolti dal sottoalbero corrente.
     * @return `true` se almeno un viewId corrisponde a [HOME_MARKER_IDS].
     */
    fun hasHomeMarkers(ids: Set<String>): Boolean =
        ids.any { it in HOME_MARKER_IDS }

    /**
     * Risolve titoli di sezione Nexi noti tramite testo in fascia alta o viewId `content_pagamento`.
     *
     * Esclude il widget «Ultimi insoluti» quando si è in home e gestisce il fallback
     * [findTitleNearContentPagamento] per schermate di pagamento.
     *
     * @param root Nodo radice del sottoalbero.
     * @return Titolo di sezione Nexi umanizzato, oppure `null`.
     */
    fun findKnownNexiTitles(root: AccessibilityNodeInfo): String? {
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val sectionBandBottom = screenBounds.top + (screenBounds.height() * 0.25f).toInt()
        val rootIds = TitleTreeWalker.collectViewIdShorts(root)
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
                contentPagamentoTitle = TitleCandidateLogic.humanizeTitle(text)
            }

            if (text.isNotBlank() && bounds.top <= sectionBandBottom && text.length <= 80 &&
                !TitleCandidateLogic.looksLikeAmount(text)
            ) {
                if (isHomeInsolutiWidgetTitle(text, viewId, onHome)) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                    continue
                }
                if (isKnownSectionTitle(text)) {
                    return TitleCandidateLogic.humanizeTitle(text)
                }
                KNOWN_NEXI_SECTION_TITLES.firstOrNull { known ->
                    normalized.contains(known) || known.contains(normalized)
                }?.let { known ->
                    if (!(known == "INSOLUTI" && onHome)) {
                        return TitleCandidateLogic.humanizeTitle(known)
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
    fun findTitleNearContentPagamento(root: AccessibilityNodeInfo): String? {
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
            if (foundPagamento && text.isNotBlank() && text.length <= 80 && !TitleCandidateLogic.looksLikeAmount(text)) {
                val normalized = text.uppercase()
                if (normalized.contains("PAGAMENTO") || normalized.contains("NUOVO")) {
                    candidates.add(TitleCandidateLogic.humanizeTitle(text))
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
    fun isKnownSectionTitle(text: String): Boolean {
        val normalized = text.trim().uppercase()
        return KNOWN_NEXI_SECTION_TITLES.any { known ->
            normalized == known || normalized.contains(known)
        }
    }

    /**
     * Risolve il titolo da viewId distintivi Nexi quando heading e topbar non sono disponibili.
     *
     * Mappa combinazioni di viewId a sezioni note (Home, Rubrica, pagamenti, distinte, effetti,
     * insoluti). Non opera quando il drawer (`nav_*`) è visibile.
     *
     * @param root Nodo radice del sottoalbero.
     * @return Titolo di sezione inferito dai viewId, oppure `null`.
     */
    fun findByDistinctiveIds(root: AccessibilityNodeInfo): String? {
        val ids = TitleTreeWalker.collectViewIdShorts(root)
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
    fun isHomeInsolutiWidgetTitle(text: String, viewId: String, onHome: Boolean): Boolean {
        if (!onHome) return false
        if (viewId.endsWith("/insoluti_title")) return true
        return text.equals("Ultimi insoluti", ignoreCase = true)
    }
}
