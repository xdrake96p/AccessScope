package dev.accessscope.scanner.analyzer.title

import dev.accessscope.scanner.analyzer.PrecisionRules

internal data class TitleCandidate(val title: String, val weight: Int, val source: String)

internal object TitleCandidateLogic {

    private val SOURCE_PRIORITY = mapOf(
        "pane_title" to 90,
        "pin" to 88,
        "modal" to 85,
        "toolbar" to 70,
        "section_title" to 65,
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

    /** Nomi classe framework Android (widget/layout) mai validi come titolo schermata. */
    private val ANDROID_FRAMEWORK_VIEW_CLASS_NAMES = setOf(
        "view", "viewgroup", "framelayout", "linearlayout", "relativelayout",
        "constraintlayout", "coordinatorlayout", "recyclerview", "scrollview",
        "nestedscrollview", "horizontalscrollview", "viewpager", "viewpager2",
        "gridview", "listview", "cardview", "textview", "imageview", "button",
        "webview", "surfaceview", "composeview", "androidcomposeview",
    )

    /**
     * `true` se il nome è quello di una classe widget/layout Android generica, mai un titolo
     * di schermata valido (può capitare se un evento riporta la classe del nodo sorgente
     * invece dell'Activity/Dialog).
     *
     * @param name Nome semplice della classe (senza package), es. `ViewGroup`.
     */
    fun isAndroidFrameworkViewClassName(name: String): Boolean =
        name.lowercase() in ANDROID_FRAMEWORK_VIEW_CLASS_NAMES

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
