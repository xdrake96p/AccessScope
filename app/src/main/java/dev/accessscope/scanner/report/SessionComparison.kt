/**
 * Confronto numerico tra due sessioni archiviate basato sulle chiavi dedupe.
 */
package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.ArchivedScanSession

/**
 * Riepilogo del delta tra sessione corrente/ultima e quella precedente.
 *
 * @property newCount Violazioni nuove (chiavi in latest non in previous).
 * @property resolvedCount Violazioni risolte (chiavi in previous non in latest).
 * @property unchangedCount Violazioni presenti in entrambe le sessioni.
 * @property scoreDelta Differenza di punteggio (latest − previous).
 */
data class SessionComparison(
    val newCount: Int,
    val resolvedCount: Int,
    val unchangedCount: Int,
    val scoreDelta: Int,
) {
    /** True se esiste almeno una differenza misurabile. */
    val hasDelta: Boolean get() = newCount > 0 || resolvedCount > 0 || scoreDelta != 0
}

/**
 * Utility per confrontare sessioni archiviate tramite [dev.accessscope.scanner.data.ArchivedScanSession.violationKeys].
 */
object SessionComparisonHelper {

    /**
     * Confronta due sessioni usando le chiavi di deduplicazione.
     *
     * @param latest Sessione più recente.
     * @param previous Sessione precedente da confrontare.
     * @return [SessionComparison] con conteggi e delta punteggio.
     */
    fun compare(latest: ArchivedScanSession, previous: ArchivedScanSession): SessionComparison {
        val latestKeys = latest.violationKeys
        val previousKeys = previous.violationKeys
        val newCount = (latestKeys - previousKeys).size
        val resolvedCount = (previousKeys - latestKeys).size
        val unchangedCount = latestKeys.intersect(previousKeys).size
        return SessionComparison(
            newCount = newCount,
            resolvedCount = resolvedCount,
            unchangedCount = unchangedCount,
            scoreDelta = latest.score - previous.score,
        )
    }

    /**
     * Confronta l'ultima sessione archiviata con la penultima per un package.
     *
     * @param latest Ultima sessione; `null` se assente.
     * @param previous Penultima sessione; `null` se assente.
     * @return Confronto o `null` se manca una delle due sessioni.
     */
    fun compareLatestWithPrevious(
        latest: ArchivedScanSession?,
        previous: ArchivedScanSession?,
    ): SessionComparison? {
        if (latest == null || previous == null) return null
        return compare(latest, previous)
    }
}
