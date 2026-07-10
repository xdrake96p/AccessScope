package dev.accessscope.scanner.analyzer.title

import dev.accessscope.scanner.analyzer.PrecisionRules

internal data class TitleCandidate(val title: String, val weight: Int, val source: String)

internal object TitleCandidateLogic {

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

    private val GENERIC_ACTIVITY_NAMES = setOf(
        "main", "home", "host", "container", "base", "wrapper", "shell",
        "flutter", "navigation", "single", "launcher", "root",
    )

    /**
     * Inferisce il titolo schermata dai viewId del contenuto visibile (generico multi-app).
     */
    fun inferTitleFromContentMarkers(ids: Set<String>): String? {
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
        if (NexiTitleHeuristics.hasHomeMarkers(ids)) return findTopBarTitleFromIds(ids) ?: "Home"
        return null
    }

    /** Toolbar coerente col fingerprint del contenuto (evita tab host fuorviante). */
    fun isToolbarConsistentWithContent(toolbarTitle: String, ids: Set<String>): Boolean {
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

    fun pickBestTitle(candidates: List<TitleCandidate>, ids: Set<String>): String? {
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

    fun titleCandidate(title: String, weight: Int, source: String) =
        TitleCandidate(title, weight, source)

    fun isGenericActivityName(name: String): Boolean {
        val simple = name
            .removeSuffix("Activity")
            .removeSuffix("Fragment")
            .removeSuffix("Screen")
            .lowercase()
        return simple in GENERIC_ACTIVITY_NAMES || simple.length <= 4
    }

    private fun findTopBarTitleFromIds(ids: Set<String>): String? = null

    /**
     * Converte un nome di activity o fragment in un titolo leggibile.
     *
     * Rimuove suffissi comuni (`Activity`, `Fragment`, …), separa camelCase e applica [humanizeTitle].
     *
     * @param name Nome semplice della classe (es. `HomeActivity`).
     * @return Titolo umanizzato derivato dal nome classe.
     */
    fun humanizeActivityName(name: String): String {
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
    fun humanizeTitle(title: String): String =
        title.trim().replace(Regex("\\s+"), " ")

    /**
     * Determina se una stringa assomiglia a un importo o valuta piuttosto che a un titolo.
     *
     * @param text Testo da valutare.
     * @return `true` se [PrecisionRules.isCurrencyOrAmountText] classifica il testo come importo.
     */
    fun looksLikeAmount(text: String): Boolean = PrecisionRules.isCurrencyOrAmountText(text)
}
