package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.PassedCheck
import dev.accessscope.scanner.data.ViolationArea

/**
 * Raccoglie controlli superati durante l'analisi (campioni + conteggi).
 * Regole generiche: vale per qualsiasi app nel [scanScope] attivo.
 */
class CheckCollector {

    private data class Key(val area: ViolationArea, val screenTitle: String, val packageName: String)

    private val passedCounts = mutableMapOf<Key, Int>()
    private val samples = mutableMapOf<Key, MutableList<PassedCheck>>()

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
        private const val MAX_SAMPLES_PER_KEY = 4

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
                        passedCount = items.sumOf { it.passedCount },
                        samples = items.flatMap { it.samples }
                            .distinctBy { "${it.viewId}|${it.bounds}|${it.checkLabel}" }
                            .take(MAX_SAMPLES_PER_KEY),
                    )
                }
                .sortedWith(compareBy({ it.screenTitle }, { it.area.ordinal }))
        }

        private fun elementSummary(snap: NodeSnapshot): String {
            snap.accessibleName()?.takeIf { it.isNotBlank() }?.let { return it.take(48) }
            snap.text?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(48) }
            val id = snap.viewId?.substringAfterLast('/')
            return id ?: snap.className.substringAfterLast('.')
        }
    }
}
