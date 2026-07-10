package dev.accessscope.scanner.service.scan

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.service.RootAcquisitionDiagnostics
import dev.accessscope.scanner.service.RootAcquisitionHelper

/**
 * Seleziona e prioritarizza le radici [AccessibilityNodeInfo] da analizzare per il pacchetto target.
 */
internal class AccessibilityRootSelector {

    /**
     * Raccoglie le radici [AccessibilityNodeInfo] da analizzare per il pacchetto target.
     *
     * Itera sulle finestre di sistema (escludendo overlay di accessibilità), con fallback
     * su `event.source` e `rootInActiveWindow`. Le radici vengono filtrate e prioritarizzate
     * tramite [selectRootsToScan] e [prioritizeRoots].
     *
     * @param targetPackage Pacchetto dell'app di cui ottenere le radici.
     * @param eventSource Snapshot clonato di `event.source` (main thread), oppure null.
     * @return Lista di radici clonate da analizzare; il chiamante deve chiamare [AccessibilityNodeInfo.recycle].
     */
    fun obtainRootsForScan(
        targetPackage: String,
        windows: List<android.view.accessibility.AccessibilityWindowInfo>?,
        eventSource: AccessibilityNodeInfo?,
        activeRoot: AccessibilityNodeInfo?,
    ): Pair<List<AccessibilityNodeInfo>, RootAcquisitionDiagnostics> {
        val (acquired, diagnostics) = RootAcquisitionHelper.acquireRoots(
            targetPackage = targetPackage,
            windows = windows,
            eventSource = eventSource,
            activeRoot = activeRoot,
        )
        val filtered = prioritizeRoots(selectRootsToScan(acquired))
        acquired.filter { root -> filtered.none { it === root } }.forEach { it.recycle() }
        return filtered to diagnostics
    }

    /**
     * Seleziona le radici rilevanti per l'analisi, escludendo drawer e riducendo duplicati.
     *
     * Preferisce schermate PIN, dialog/modal e la finestra con punteggio di contenuto più alto.
     *
     * @param roots Elenco candidato di radici ottenute da [obtainRootsForScan].
     * @return Sottoinsieme filtrato di radici da analizzare (tipicamente una sola).
     */
    private fun selectRootsToScan(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val withoutDrawer = roots.filter { !ScreenTitleResolver.isDrawerOnlyRoot(it) }
        val candidates = if (withoutDrawer.isNotEmpty()) withoutDrawer else roots

        val pinRoots = candidates.filter { ScreenTitleResolver.isPinScreen(it) }
        if (pinRoots.isNotEmpty()) return pinRoots

        val modalRoots = candidates.filter { root ->
            val className = root.className?.toString().orEmpty()
            listOf("Dialog", "BottomSheet", "Popup", "AlertDialog", "Modal")
                .any { className.contains(it, true) }
        }
        if (modalRoots.isNotEmpty()) return modalRoots

        val primary = candidates.maxByOrNull { root -> contentRootScore(root) } ?: return candidates
        return listOf(primary)
    }

    /**
     * Calcola un punteggio euristico per identificare la radice con il contenuto principale.
     *
     * Premia view con `scrollview_port` e `card_home`, penalizza elementi di navigazione (`nav_`).
     *
     * @param root Radice candidata da valutare.
     * @return Punteggio numerico; valori più alti indicano contenuto principale.
     */
    private fun contentRootScore(root: AccessibilityNodeInfo): Int {
        val ids = ScreenTitleResolver.rootViewIds(root)
        var score = root.childCount
        if ("scrollview_port" in ids) score += 10_000
        if ("card_home" in ids) score += 5_000
        if (ids.any { it.startsWith("nav_") }) score -= 10_000
        return score
    }

    /**
     * Riordina le radici mettendo in testa PIN e modal rispetto al contenuto ordinario.
     *
     * @param roots Elenco di radici da prioritarizzare.
     * @return Stesso elenco o riordinato con PIN/modal in prima posizione.
     */
    private fun prioritizeRoots(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        if (roots.size <= 1) return roots

        val pinRoots = roots.filter { ScreenTitleResolver.isPinScreen(it) }
        if (pinRoots.isNotEmpty()) {
            val others = roots.filter { root -> pinRoots.none { it == root } }
            return pinRoots + others
        }

        val modalRoots = roots.filter { root ->
            val className = root.className?.toString().orEmpty()
            listOf("Dialog", "BottomSheet", "Popup", "AlertDialog", "Modal")
                .any { className.contains(it, true) }
        }
        if (modalRoots.isNotEmpty()) {
            val others = roots.filter { root -> modalRoots.none { it == root } }
            return modalRoots + others
        }

        return roots
    }
}
