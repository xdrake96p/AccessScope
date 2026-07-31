package dev.accessscope.scanner.analyzer.title

import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap

internal object TitleCache {

    private val lastTitleByPackage = ConcurrentHashMap<String, String>()

    fun get(packageKey: String): String? = lastTitleByPackage[packageKey]

    fun put(packageKey: String, title: String) {
        lastTitleByPackage[packageKey] = title
    }

    /**
     * Svuota la cache dei titoli per pacchetto.
     *
     * @param packageName Nome del pacchetto da rimuovere dalla cache; se `null` o blank,
     *   viene svuotata l'intera mappa.
     */
    fun clear(packageName: String? = null) {
        if (packageName.isNullOrBlank()) {
            lastTitleByPackage.clear()
        } else {
            lastTitleByPackage.remove(packageName)
        }
    }

    fun shouldCacheTitle(root: AccessibilityNodeInfo, title: String, ids: Set<String>): Boolean {
        if (ids.any { it.startsWith("nav_") }) return false
        return true
    }

    /**
     * Verifica se un titolo in cache è ancora valido per il layout corrente.
     */
    fun canReuseCachedTitle(root: AccessibilityNodeInfo, cached: String, ids: Set<String>): Boolean {
        if (ids.any { it.startsWith("nav_") }) return false
        if (TitleTreeWalker.hasScrollableContent(root) && hasActivityChrome(ids)) {
            TitleTreeWalker.findTopBarTitle(root)?.let { top ->
                return top.equals(cached, ignoreCase = true)
            }
            return false
        }
        val fresh = TitleTreeWalker.findSectionTitle(root)
        if (fresh != null && !fresh.equals(cached, ignoreCase = true)) return false
        return true
    }

    /** Chrome strutturale dell'activity (toolbar, tab, bottom nav) — pattern generici Android. */
    private fun hasActivityChrome(ids: Set<String>): Boolean =
        ids.any { id ->
            id.contains("topbar") || id.contains("toolbar") || id.contains("tab_") ||
                id.contains("bottom_nav") || id.contains("navigation") || id.contains("nav_host") ||
                id.contains("action_bar") || id.contains("appbar") || id == "content"
        }
}
