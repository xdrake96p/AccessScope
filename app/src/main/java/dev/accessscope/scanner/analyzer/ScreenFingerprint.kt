/**
 * Calcolo dell'impronta digitale di una schermata per il raggruppamento e il deduplicamento
 * delle violazioni di accessibilità nello stesso contesto visivo.
 *
 * L'impronta combina package, titolo schermata e gli identificatori delle view interattive
 * visibili, producendo una stringa stabile utilizzabile come chiave di correlazione.
 */
package dev.accessscope.scanner.analyzer

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Utility per generare un'impronta sintetica di una schermata a partire dall'albero
 * di accessibilità.
 */
object ScreenFingerprint {

    /**
     * Calcola l'impronta univoca di una schermata.
     *
     * @param root Nodo radice dell'albero di accessibilità della finestra corrente.
     * @param packageName Nome del package dell'applicazione in analisi.
     * @param screenTitle Titolo umano della schermata, già risolto da [ScreenTitleResolver].
     * @return Stringa composta da package, titolo, view ID interattivi ordinati e conteggio figli della radice.
     */
    fun compute(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
    ): String {
        val viewIds = mutableListOf<String>()
        collectInteractiveIds(root, viewIds, limit = 20)
        val sortedIds = viewIds.sorted().joinToString("|")
        return "$packageName::$screenTitle::$sortedIds::${root.childCount}"
    }

    /**
     * Raccoglie ricorsivamente gli ID risorsa delle view interattive visibili.
     *
     * @param node Nodo corrente in visita depth-first.
     * @param output Lista mutabile in cui accumulare gli ID raccolti.
     * @param limit Numero massimo di ID da raccogliere prima di interrompere la scansione.
     */
    private fun collectInteractiveIds(
        node: AccessibilityNodeInfo,
        output: MutableList<String>,
        limit: Int,
    ) {
        if (output.size >= limit) return
        if (!node.isVisibleToUser) return

        val id = node.viewIdResourceName
        val interactive = node.isClickable || node.isFocusable || node.isEditable
        if (interactive && !id.isNullOrBlank()) {
            output.add(id)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInteractiveIds(child, output, limit)
            child.recycle()
        }
    }
}
