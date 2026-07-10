/**
 * Calcolo dell'impronta digitale di una schermata per il raggruppamento delle visite.
 *
 * Usa titolo schermata e chrome strutturale (toolbar, tab bar) — non i viewId del
 * contenuto scrollabile, che cambiano a ogni scroll.
 */
package dev.accessscope.scanner.analyzer

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.title.TitleCandidateLogic
import dev.accessscope.scanner.analyzer.title.TitleTreeWalker
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
     * Titolo stabile per fingerprint: preferisce content markers al titolo display oscillante.
     */
    fun fingerprintTitle(root: AccessibilityNodeInfo, displayTitle: String): String {
        val rootIds = TitleTreeWalker.collectViewIdShorts(root)
        return TitleCandidateLogic.inferTitleFromContentMarkers(rootIds)?.trim()?.takeIf { it.isNotBlank() }
            ?: displayTitle.trim().ifBlank { "unknown" }
    }

    /** Formato fingerprint per test (chrome già raccolto). */
    internal fun formatForTest(packageName: String, title: String, chromeIds: List<String>): String {
        val chrome = chromeIds.sorted().distinct().joinToString("|")
        return if (chrome.isNotEmpty()) "$packageName::$title::$chrome" else "$packageName::$title"
    }

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
