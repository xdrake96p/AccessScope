/**
 * Bridge tra cronologia scan WCAG e pipeline Maestro intelligente.
 */
package dev.accessscope.scanner.recorder.intelligence

import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.ScanSessionState
import dev.accessscope.scanner.data.VisitedScreen
import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.util.ScanHistoryStore

/**
 * Costruisce [ScanIntelligenceBundle] da archivio o stato live Scan+Flusso.
 *
 * @param scanHistoryStore Store cronologia sessioni.
 */
class ScanIntelligenceProvider(
    private val scanHistoryStore: ScanHistoryStore,
) {

    /**
     * Carica intelligence per [appId], preferendo sessione live se in scan sullo stesso package.
     *
     * @param appId Package Android target.
     * @param liveState Stato scan live (Scan+Flusso) opzionale.
     * @return Bundle intelligence; vuoto se nessuna sessione disponibile.
     */
    fun load(appId: String, liveState: ScanSessionState? = null): ScanIntelligenceBundle {
        val live = liveState?.takeIf {
            it.isScanning && it.selectedPackages.contains(appId) && it.visitedScreens.isNotEmpty()
        }
        if (live != null) {
            return fromVisitedScreens(live.visitedScreens, live.violations.map { it.viewId })
        }
        val archived = scanHistoryStore.getLatest(appId) ?: return ScanIntelligenceBundle()
        return fromArchived(archived)
    }

  private fun fromArchived(session: ArchivedScanSession): ScanIntelligenceBundle =
        fromVisitedScreens(session.visitedScreens, session.violations.map { it.viewId })

    private fun fromVisitedScreens(
        visited: List<VisitedScreen>,
        violationViewIds: List<String?>,
    ): ScanIntelligenceBundle {
        if (visited.isEmpty()) return ScanIntelligenceBundle()

        val visitCounts = visited.groupingBy { it.fingerprint }.eachCount()
        val screens = visited
            .distinctBy { it.fingerprint }
            .associate { screen ->
                val nextFp = visited
                    .windowed(2)
                    .filter { it[0].fingerprint == screen.fingerprint }
                    .groupingBy { it[1].fingerprint }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                screen.fingerprint to ScreenIntel(
                    fingerprint = screen.fingerprint,
                    title = screen.title,
                    visitCount = visitCounts[screen.fingerprint] ?: 1,
                    visitIndex = screen.visitIndex,
                    typicalNextFingerprint = nextFp,
                )
            }

        val mainPath = visited
            .sortedBy { it.visitIndex }
            .map { it.fingerprint }
            .distinct()

        val elements = buildElementIntel(visited, violationViewIds)

        return ScanIntelligenceBundle(
            screens = screens,
            elements = elements,
            mainPathFingerprints = mainPath,
        )
    }

    private fun buildElementIntel(
        visited: List<VisitedScreen>,
        violationViewIds: List<String?>,
    ): Map<String, ElementIntel> {
        val defaultFp = visited.minByOrNull { it.visitIndex }?.fingerprint.orEmpty()
        val counts = mutableMapOf<String, Int>()
        violationViewIds.forEach { raw ->
            val short = MaestroSelectorHeuristics.shortViewId(raw) ?: return@forEach
            if (MaestroSelectorHeuristics.isNoiseViewId(short)) return@forEach
            counts[short] = counts.getOrDefault(short, 0) + 1
        }
        return counts.mapValues { (viewId, count) ->
            ElementIntel(
                viewId = viewId,
                screenFingerprint = defaultFp,
                occurrenceCount = count,
            )
        }
    }
}
