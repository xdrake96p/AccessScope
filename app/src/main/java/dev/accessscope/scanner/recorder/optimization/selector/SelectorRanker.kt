/**
 * Ranking selettori Maestro: id-first, point solo come fallback; catena ordinata per Play.
 */
package dev.accessscope.scanner.recorder.optimization.selector

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceBundle
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.SelectorCandidate

/** Tipo selettore preferito per export/replay. */
enum class SelectorKind {
    ViewId,
    Text,
    ContentDescription,
    Point,
}

/**
 * Score e preferenza selettore per tap/long-press.
 *
 * @param kind Tipo preferito.
 * @param score Punteggio 0..100.
 */
data class SelectorRank(
    val kind: SelectorKind,
    val score: Int,
)

/**
 * Ordina selettori: viewId stabile > contentDescription > testo > point.
 */
object SelectorRanker {

    private const val ID_BASE = 60
    private const val ID_INTEL_BONUS = 25
    private const val ID_REPEAT_BONUS = 15
    private const val TEXT_STABLE_BONUS = 40
    private const val POINT_SCORE = 20
    private const val EXPORT_ID_THRESHOLD = 50
    private const val EXPORT_POINT_THRESHOLD = 40

    /**
     * Calcola rank per tap.
     */
    fun rankTap(
        action: RecordedAction.Tap,
        allActions: List<RecordedAction>,
        intel: ScanIntelligenceBundle? = null,
        telemetry: FlowTelemetry? = null,
    ): SelectorRank {
        val idScore = scoreViewId(action.viewId, allActions, intel, telemetry)
        if (idScore >= EXPORT_ID_THRESHOLD) return SelectorRank(SelectorKind.ViewId, idScore)

        val cdScore = scoreText(action.contentDescription, allActions)
        if (cdScore >= EXPORT_ID_THRESHOLD) return SelectorRank(SelectorKind.ContentDescription, cdScore)

        val textScore = scoreText(action.text, allActions)
        if (textScore >= EXPORT_ID_THRESHOLD) return SelectorRank(SelectorKind.Text, textScore)

        if (action.pointPercentX != null && action.pointPercentY != null) {
            return SelectorRank(SelectorKind.Point, POINT_SCORE)
        }
        return SelectorRank(SelectorKind.ViewId, idScore)
    }

    /**
     * Costruisce la catena ordinata di candidati per [action].
     * Ordine: id stabile → contentDescription → text → point (ultima risorsa).
     */
    fun buildChain(
        action: RecordedAction.Tap,
        allActions: List<RecordedAction>,
        intel: ScanIntelligenceBundle? = null,
        telemetry: FlowTelemetry? = null,
    ): List<SelectorCandidate> {
        val scored = mutableListOf<Pair<Int, SelectorCandidate>>()
        val idScore = scoreViewId(action.viewId, allActions, intel, telemetry)
        if (idScore > 0 && !action.viewId.isNullOrBlank()) {
            scored += idScore to SelectorCandidate(viewId = action.viewId)
        }
        val cdScore = scoreText(action.contentDescription, allActions)
        if (cdScore > 0 && !action.contentDescription.isNullOrBlank()) {
            scored += (cdScore + 1) to SelectorCandidate(contentDescription = action.contentDescription)
        }
        val textScore = scoreText(action.text, allActions)
        if (textScore > 0 && !action.text.isNullOrBlank()) {
            // Testo leggermente sotto cd a parità; section header → testo prioritario su id ambiguo.
            val boost = if (MaestroSelectorHeuristics.isAmbiguousSharedViewId(action.viewId)) 30 else 0
            scored += (textScore + boost) to SelectorCandidate(text = action.text)
        }
        if (action.pointPercentX != null && action.pointPercentY != null) {
            scored += POINT_SCORE to SelectorCandidate(
                pointPercentX = action.pointPercentX,
                pointPercentY = action.pointPercentY,
            )
        }
        return scored
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.dedupeKey() }
            .filterNot { it.isBlank() }
            .ifEmpty {
                listOfNotNull(
                    SelectorCandidate(
                        viewId = action.viewId,
                        text = action.text,
                        contentDescription = action.contentDescription,
                        pointPercentX = action.pointPercentX,
                        pointPercentY = action.pointPercentY,
                    ).takeUnless { it.isBlank() },
                )
            }
    }

    /**
     * Popola o raffina [RecordedAction.Tap.selectorChain].
     *
     * Se la catena REC è già presente, fa merge con il ranking optimize (non la sovrascrive alla cieca).
     */
    fun attachChains(
        actions: List<RecordedAction>,
        intel: ScanIntelligenceBundle? = null,
        telemetry: FlowTelemetry? = null,
    ): List<RecordedAction> =
        actions.map { action ->
            if (action !is RecordedAction.Tap) return@map action
            val ranked = buildChain(action, actions, intel, telemetry)
            if (action.selectorChain.isEmpty()) {
                return@map action.copy(selectorChain = ranked)
            }
            // Merge: candidati REC prima, poi ranked; dedupe preservando ordine.
            val merged = (action.selectorChain + ranked)
                .distinctBy { it.dedupeKey() }
                .filterNot { it.isBlank() }
            action.copy(selectorChain = merged)
        }

    /**
     * `true` se l’export YAML può usare solo id (no point).
     */
    fun shouldExportIdOnly(
        action: RecordedAction.Tap,
        allActions: List<RecordedAction>,
        intel: ScanIntelligenceBundle? = null,
        telemetry: FlowTelemetry? = null,
    ): Boolean {
        val rank = rankTap(action, allActions, intel, telemetry)
        return rank.kind == SelectorKind.ViewId && rank.score >= EXPORT_ID_THRESHOLD
    }

    /**
     * `true` se point è l’unica opzione affidabile per export.
     */
    fun shouldExportPoint(
        action: RecordedAction.Tap,
        allActions: List<RecordedAction>,
        intel: ScanIntelligenceBundle? = null,
    ): Boolean {
        val rank = rankTap(action, allActions, intel)
        return rank.kind == SelectorKind.Point && rank.score <= EXPORT_POINT_THRESHOLD
    }

    private fun scoreViewId(
        viewId: String?,
        allActions: List<RecordedAction>,
        intel: ScanIntelligenceBundle?,
        telemetry: FlowTelemetry?,
    ): Int {
        val short = MaestroSelectorHeuristics.shortViewId(viewId)
        if (short.isNullOrBlank() || MaestroSelectorHeuristics.isNoiseViewId(short)) return 0
        if (MaestroSelectorHeuristics.isStructuralContainerViewId(short)) return 0
        if (MaestroSelectorHeuristics.isAmbiguousSharedViewId(short)) return 0
        if (MaestroSelectorHeuristics.isVolatileViewId(short)) return 15
        var score = ID_BASE
        val repeats = allActions.count {
            MaestroSelectorHeuristics.shortViewId(
                when (it) {
                    is RecordedAction.Tap -> it.viewId
                    is RecordedAction.LongPress -> it.viewId
                    is RecordedAction.InputText -> it.viewId
                    else -> null
                },
            ) == short
        }
        if (repeats >= 2) score += ID_REPEAT_BONUS
        val intelCount = intel?.elements[short]?.occurrenceCount ?: 0
        if (intelCount >= 1) score += ID_INTEL_BONUS
        val telemetryCount = (telemetry?.snapshots ?: emptyList()).count { snap ->
            snap.fingerprint.contains(short, ignoreCase = true)
        }
        if (telemetryCount >= 2) score += 10
        return score.coerceAtMost(100)
    }

    private fun scoreText(text: String?, allActions: List<RecordedAction>): Int {
        val value = text?.trim().orEmpty()
        if (value.isBlank() || isDynamicText(value)) return 0
        val repeats = allActions.count {
            when (it) {
                is RecordedAction.Tap -> it.text == value || it.contentDescription == value
                is RecordedAction.LongPress -> it.text == value || it.contentDescription == value
                else -> false
            }
        }
        return if (repeats >= 2) TEXT_STABLE_BONUS + 10 else TEXT_STABLE_BONUS
    }

    private fun isDynamicText(text: String): Boolean {
        if (text.length <= 2) return true
        if (text.all { it.isDigit() || it == '.' || it == ',' || it == ':' }) return true
        return false
    }
}
