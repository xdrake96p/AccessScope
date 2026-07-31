/**
 * Calcolo dell'impronta digitale di una schermata per il raggruppamento delle visite.
 *
 * Usa titolo schermata e chrome strutturale (toolbar, tab bar) — non i viewId del
 * contenuto scrollabile, che cambiano a ogni scroll.
 */
package dev.accessscope.scanner.analyzer

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Utility per generare un'impronta sintetica stabile durante lo scroll.
 */
object ScreenFingerprint {

    /**
     * Calcola l'impronta di una schermata logica (non del viewport scrollato).
     *
     * @param root Nodo radice dell'albero di accessibilità della finestra corrente.
     * @param packageName Nome del package dell'applicazione in analisi.
     * @param screenTitle Titolo umano della schermata, già risolto da [ScreenTitleResolver].
     * @return Stringa `package::titolo` con eventuali id chrome strutturali ordinati.
     */
    fun compute(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
    ): String {
        val title = fingerprintTitle(root, screenTitle)
        val chromeIds = mutableListOf<String>()
        collectStructuralChromeIds(root, chromeIds, limit = 12)
        val chrome = chromeIds.sorted().distinct().joinToString("|")
        return if (chrome.isNotEmpty()) "$packageName::$title::$chrome" else "$packageName::$title"
    }

    /**
     * Titolo stabile per fingerprint.
     *
     * @param root Non usato direttamente: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @param displayTitle Titolo già risolto da [ScreenTitleResolver].
     */
    fun fingerprintTitle(root: AccessibilityNodeInfo, displayTitle: String): String =
        displayTitle.trim().ifBlank { "unknown" }

    /** Formato fingerprint per test (chrome già raccolto). */
    internal fun formatForTest(packageName: String, title: String, chromeIds: List<String>): String {
        val chrome = chromeIds.sorted().distinct().joinToString("|")
        return if (chrome.isNotEmpty()) "$packageName::$title::$chrome" else "$packageName::$title"
    }

    /**
     * Riconduce un fingerprint "quasi duplicato" a uno già visto in sessione, quando la
     * differenza è solo un piccolo sottoinsieme di chrome transitorio (es. una collapsing
     * toolbar comparsa/scomparsa per via dello scroll) — senza questo, la stessa schermata
     * logica produce N fingerprint diversi e frammenta il report in N "schermate" fasulle
     * (osservato: 3 fingerprint diversi tutti col titolo "Home" nella stessa sessione).
     *
     * Non tocca fingerprint con un tab esplicito diverso (`tab:...`): quello è un cambio di
     * contenuto reale, non chrome transitorio, e deve restare una schermata distinta.
     *
     * @param candidate Fingerprint appena calcolato da [compute].
     * @param knownFingerprints Fingerprint già registrati in questa sessione di scansione.
     * @return Un fingerprint di [knownFingerprints] se [candidate] ne è una variante transitoria,
     * altrimenti [candidate] invariato.
     */
    fun canonicalize(candidate: String, knownFingerprints: Set<String>): String {
        if (candidate in knownFingerprints) return candidate
        val prefix = titlePrefixOf(candidate) ?: return candidate
        val candidateChrome = chromeSetOf(candidate)
        return knownFingerprints.firstOrNull { existing ->
            existing.startsWith(prefix) && isTransientChromeVariant(candidateChrome, chromeSetOf(existing))
        } ?: candidate
    }

    private fun titlePrefixOf(fingerprint: String): String? {
        val parts = fingerprint.split("::")
        if (parts.size < 2) return null
        return "${parts[0]}::${parts[1]}"
    }

    private fun chromeSetOf(fingerprint: String): Set<String> {
        val parts = fingerprint.split("::")
        if (parts.size < 3) return emptySet()
        return parts[2].split("|").filter { it.isNotBlank() }.toSet()
    }

    private fun isTransientChromeVariant(candidate: Set<String>, existing: Set<String>): Boolean {
        if (candidate == existing) return true
        val candidateTabs = candidate.filterTo(mutableSetOf()) { it.startsWith("tab:") }
        val existingTabs = existing.filterTo(mutableSetOf()) { it.startsWith("tab:") }
        if (candidateTabs != existingTabs) return false
        val symmetricDiff = (candidate - existing).size + (existing - candidate).size
        return symmetricDiff <= MAX_TRANSIENT_CHROME_DIFF
    }

    private const val MAX_TRANSIENT_CHROME_DIFF = 1

    /**
     * Raccoglie viewId di chrome UI stabile (toolbar, tab, bottom nav) — generico multi-app.
     */
    private fun collectStructuralChromeIds(
        node: AccessibilityNodeInfo,
        output: MutableList<String>,
        limit: Int,
    ) {
        if (output.size >= limit) return
        if (!node.isVisibleToUser) return

        val id = node.viewIdResourceName
        if (!id.isNullOrBlank()) {
            val short = id.substringAfterLast('/').lowercase()
            val className = node.className?.toString().orEmpty().lowercase()
            val isChrome = STRUCTURAL_ID_PATTERN.containsMatchIn(short) ||
                className.contains("toolbar", true) ||
                className.contains("tablayout", true) ||
                className.contains("bottomnavigation", true) ||
                className.contains("navigationbar", true)
            if (isChrome) {
                output.add(short)
            }
            if (short == "tv_tab" || short.startsWith("tab_")) {
                node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { tabLabel ->
                    output.add("tab:${tabLabel.lowercase()}")
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectStructuralChromeIds(child, output, limit)
            child.recycle()
        }
    }

    /** Pattern generico per id risorsa del chrome (non contenuto lista/scroll). */
    private val STRUCTURAL_ID_PATTERN = Regex(
        """(topbar|toolbar|action_bar|appbar|tab_|bottom_nav|navigation|nav_host|coordinator)""",
        RegexOption.IGNORE_CASE,
    )
}
