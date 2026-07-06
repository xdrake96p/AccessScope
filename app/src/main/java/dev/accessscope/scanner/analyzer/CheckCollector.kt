/**
 * Raccolta dei controlli di accessibilità superati durante l'analisi di una schermata.
 *
 * Oltre alle violazioni, il report può includere un riepilogo dei controlli passati
 * (etichette, target di tocco, contrasto) con campioni rappresentativi per area WCAG.
 */
package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.PassedCheck
import dev.accessscope.scanner.data.ViolationArea

/**
 * Accumula conteggi e campioni dei controlli superati, raggruppati per area,
 * schermata e package.
 */
class CheckCollector {

    /** Chiave interna per raggruppare i pass per area, titolo e package. */
    private data class Key(val area: ViolationArea, val screenTitle: String, val packageName: String)

    private val passedCounts = mutableMapOf<Key, Int>()
    private val samples = mutableMapOf<Key, MutableList<PassedCheck>>()

    /**
     * Registra un controllo superato per un nodo specifico.
     *
     * @param area Area di violazione/controllo WCAG (es. [ViolationArea.LABELS]).
     * @param screenTitle Titolo umano della schermata corrente.
     * @param packageName Package dell'applicazione analizzata.
     * @param checkLabel Etichetta descrittiva del controllo superato.
     * @param snap Snapshot del nodo che ha superato il controllo.
     * @param wcagRef Riferimento WCAG opzionale (es. "1.4.3").
     * @param detail Dettaglio opzionale; se assente viene derivato dallo snapshot.
     */
    fun recordPass(
        area: ViolationArea,
        screenTitle: String,
        packageName: String,
        checkLabel: String,
        snap: NodeSnapshot,
        wcagRef: String? = null,
        detail: String? = null,
    ) {
        val key = Key(area, screenTitle, packageName)
        passedCounts[key] = (passedCounts[key] ?: 0) + 1
        val bucket = samples.getOrPut(key) { mutableListOf() }
        if (bucket.size >= MAX_SAMPLES_PER_KEY) return
        val viewShort = snap.viewId?.substringAfterLast('/')
        val dedupe = "${viewShort}|${snap.boundsLabel()}|$checkLabel"
        if (bucket.any { "${it.viewId?.substringAfterLast('/')}|${it.bounds}|${it.checkLabel}" == dedupe }) return
        bucket += PassedCheck(
            area = area,
            checkLabel = checkLabel,
            screenTitle = screenTitle,
            packageName = packageName,
            elementSummary = detail ?: elementSummary(snap),
            viewId = snap.viewId,
            bounds = snap.boundsLabel(),
            wcagRef = wcagRef,
        )
    }

    /**
     * Costruisce i riepiloghi finali dei controlli superati.
     *
     * @return Lista di [CheckAreaSummary] con conteggi e campioni per ogni chiave accumulata.
     */
    fun buildSummaries(): List<CheckAreaSummary> =
        passedCounts.map { (key, count) ->
            CheckAreaSummary(
                area = key.area,
                screenTitle = key.screenTitle,
                packageName = key.packageName,
                passedCount = count,
                samples = samples[key].orEmpty().toList(),
            )
        }

    companion object {
        /** Numero massimo di campioni conservati per ogni combinazione area/schermata/package. */
        private const val MAX_SAMPLES_PER_KEY = 4

        /**
         * Unisce più liste di riepiloghi prendendo il massimo conteggio per chiave
         * (evita gonfiaggio durante re-analisi della stessa schermata in scroll).
         *
         * @param summaries Liste di riepiloghi da fondere.
         * @return Lista unificata ordinata per titolo schermata e area.
         */
        fun merge(summaries: List<CheckAreaSummary>): List<CheckAreaSummary> {
            if (summaries.isEmpty()) return emptyList()
            return summaries
                .groupBy { Triple(it.area, it.screenTitle, it.packageName) }
                .map { (_, items) ->
                    val first = items.first()
                    CheckAreaSummary(
                        area = first.area,
                        screenTitle = first.screenTitle,
                        packageName = first.packageName,
                        passedCount = items.maxOf { it.passedCount },
                        samples = items.flatMap { it.samples }
                            .distinctBy { "${it.viewId}|${it.bounds}|${it.checkLabel}" }
                            .take(MAX_SAMPLES_PER_KEY),
                    )
                }
                .sortedWith(compareBy({ it.screenTitle }, { it.area.ordinal }))
        }

        /**
         * Produce un riassunto testuale breve dell'elemento per i report.
         *
         * @param snap Snapshot del nodo.
         * @return Nome accessibile, testo visibile, view ID o nome classe (max 48 caratteri).
         */
        private fun elementSummary(snap: NodeSnapshot): String {
            snap.accessibleName()?.takeIf { it.isNotBlank() }?.let { return it.take(48) }
            snap.text?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(48) }
            val id = snap.viewId?.substringAfterLast('/')
            return id ?: snap.className.substringAfterLast('.')
        }
    }
}
