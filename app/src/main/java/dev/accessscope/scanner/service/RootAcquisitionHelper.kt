/**
 * Selezione e prioritizzazione delle radici UI per la scansione accessibilità.
 *
 * Logica estratta da [AccessScopeAccessibilityService] per essere testabile e
 * garantire il tracciamento anche quando l'app target viene aperta esternamente.
 */
package dev.accessscope.scanner.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Origine di una radice candidata per l'analisi. */
enum class RootSource {
    FOCUSED_WINDOW,
    EVENT_SOURCE,
    ACTIVE_WINDOW,
    BACKGROUND_WINDOW,
}

/**
 * Metadati di una finestra candidata (senza nodo, per test unitari).
 *
 * @property packageName Package della radice finestra.
 * @property source Tipo di origine.
 * @property windowId ID finestra per deduplicazione.
 * @property dedupeKey Chiave alternativa se windowId non disponibile.
 */
data class RootCandidateMeta(
    val packageName: String,
    val source: RootSource,
    val windowId: Int = -1,
    val dedupeKey: String = "w:$windowId",
)

/** Diagnostica dell'acquisizione radici (log e debug). */
data class RootAcquisitionDiagnostics(
    val targetPackage: String,
    val windowCount: Int,
    val focusedPackage: String?,
    val activePackage: String?,
    val selectedSources: List<RootSource>,
    val candidateCount: Int,
)

/**
 * Helper per raccogliere radici [AccessibilityNodeInfo] con priorità finestra attiva/focus.
 */
object RootAcquisitionHelper {

    private const val PRIORITY_FOCUSED = 0
    private const val PRIORITY_EVENT_SOURCE = 1
    private const val PRIORITY_ACTIVE = 2
    private const val PRIORITY_BACKGROUND = 3

    /**
     * Ordina i metadati candidati per priorità sorgente e deduplica.
     */
    fun prioritizeCandidates(
        targetPackage: String,
        candidates: List<RootCandidateMeta>,
    ): List<RootCandidateMeta> {
        val matching = candidates.filter { it.packageName == targetPackage }
        if (matching.isEmpty()) return emptyList()

        val sorted = matching.sortedWith(
            compareBy<RootCandidateMeta> { sourcePriority(it.source) }
                .thenBy { it.windowId },
        )

        val seen = LinkedHashSet<String>()
        return sorted.filter { candidate ->
            seen.add(candidate.dedupeKey)
        }
    }

    /**
     * Raccoglie radici clonate da finestre, event source e finestra attiva.
     *
     * @param targetPackage Package monitorato.
     * @param windows Elenco finestre dal servizio (può essere null pre-Lollipop).
     * @param eventSource Snapshot clonato di event.source.
     * @param activeRoot rootInActiveWindow (non clonato — verrà clonato se selezionato).
     */
    fun acquireRoots(
        targetPackage: String,
        windows: List<AccessibilityWindowInfo>?,
        eventSource: AccessibilityNodeInfo?,
        activeRoot: AccessibilityNodeInfo?,
    ): Pair<List<AccessibilityNodeInfo>, RootAcquisitionDiagnostics> {
        val metaCandidates = mutableListOf<RootCandidateMeta>()
        val nodeByKey = LinkedHashMap<String, AccessibilityNodeInfo>()

        var focusedPackage: String? = null
        var activePackage: String? = null
        var windowCount = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windows?.forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@forEach
                windowCount++
                val windowRoot = window.root ?: return@forEach
                try {
                    val pkg = windowRoot.packageName?.toString() ?: return@forEach
                    val isFocused = window.isFocused
                    val isActive = window.isActive
                    if (isFocused) focusedPackage = pkg
                    if (isActive) activePackage = pkg

                    if (pkg != targetPackage) return@forEach

                    val source = if (isActive || isFocused) {
                        RootSource.FOCUSED_WINDOW
                    } else {
                        RootSource.BACKGROUND_WINDOW
                    }
                    val key = "win:${window.id}"
                    metaCandidates += RootCandidateMeta(
                        packageName = pkg,
                        source = source,
                        windowId = window.id,
                        dedupeKey = key,
                    )
                    nodeByKey[key] = AccessibilityNodeInfo.obtain(windowRoot)
                } finally {
                    windowRoot.recycle()
                }
            }
        }

        eventSource?.packageName?.toString()?.let { pkg ->
            if (pkg == targetPackage) {
                val key = "event:${System.identityHashCode(eventSource)}"
                metaCandidates += RootCandidateMeta(
                    packageName = pkg,
                    source = RootSource.EVENT_SOURCE,
                    dedupeKey = key,
                )
                nodeByKey[key] = AccessibilityNodeInfo.obtain(eventSource)
            }
        }

        activeRoot?.let { active ->
            try {
                val pkg = active.packageName?.toString()
                activePackage = pkg
                if (pkg == targetPackage) {
                    val key = "active:${System.identityHashCode(active)}"
                    if (!nodeByKey.containsKey(key)) {
                        metaCandidates += RootCandidateMeta(
                            packageName = pkg,
                            source = RootSource.ACTIVE_WINDOW,
                            dedupeKey = key,
                        )
                        nodeByKey[key] = AccessibilityNodeInfo.obtain(active)
                    }
                }
            } finally {
                active.recycle()
            }
        }

        val prioritized = prioritizeCandidates(targetPackage, metaCandidates)
        val hasHighPriority = prioritized.any {
            it.source == RootSource.FOCUSED_WINDOW ||
                it.source == RootSource.EVENT_SOURCE ||
                it.source == RootSource.ACTIVE_WINDOW
        }
        val selectedMeta = if (hasHighPriority) {
            prioritized.filter {
                it.source != RootSource.BACKGROUND_WINDOW
            }.ifEmpty { prioritized }
        } else {
            prioritized
        }

        val roots = selectedMeta.mapNotNull { nodeByKey[it.dedupeKey] }
        nodeByKey.values.filter { root -> roots.none { it === root } }.forEach { it.recycle() }

        val diagnostics = RootAcquisitionDiagnostics(
            targetPackage = targetPackage,
            windowCount = windowCount,
            focusedPackage = focusedPackage,
            activePackage = activePackage,
            selectedSources = selectedMeta.map { it.source },
            candidateCount = metaCandidates.size,
        )
        return roots to diagnostics
    }

    private fun sourcePriority(source: RootSource): Int = when (source) {
        RootSource.FOCUSED_WINDOW -> PRIORITY_FOCUSED
        RootSource.EVENT_SOURCE -> PRIORITY_EVENT_SOURCE
        RootSource.ACTIVE_WINDOW -> PRIORITY_ACTIVE
        RootSource.BACKGROUND_WINDOW -> PRIORITY_BACKGROUND
    }
}
