/**
 * Analisi differenze registrazione grezza vs output ottimizzato (input prompt Gemini).
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordingVisualContext
import dev.accessscope.scanner.recorder.model.TransitionKind

/**
 * Report diff grezzo/ottimizzato per guidare la revisione AI.
 *
 * @param rawCount Step grezzi REC.
 * @param optimizedCount Step post-pipeline.
 * @param missingTapLabels Tap presenti in grezzo ma assenti in ottimizzato (testo/id).
 * @param longTransitions Indici dove servono wait (deltaMs alto o burst UI).
 * @param weakSelectorSteps Step con selettore debole in ottimizzato.
 * @param lostStepsSummary Testo A0 step persi.
 * @param summaryMarkdown Testo per prompt Gemini.
 */
data class FlowReviewDiffReport(
    val rawCount: Int,
    val optimizedCount: Int,
    val missingTapLabels: List<String>,
    val longTransitions: List<Int>,
    val weakSelectorSteps: List<Int>,
    val lostStepsSummary: String,
    val summaryMarkdown: String,
)

/**
 * Evidenzia cosa la pipeline ha perso o sottostimato rispetto alla REC grezza.
 */
object FlowReviewDiffAnalyzer {

    private const val LONG_DELTA_MS = 1_500L
    private const val QUIET_UI_CHANGES = 8

    /**
     * @param raw Azioni grezze REC.
     * @param optimized Azioni post-[FlowOptimizer].
     * @param telemetry Telemetria opzionale.
     * @param visual Contesto visivo opzionale.
     */
    fun analyze(
        raw: List<RecordedAction>,
        optimized: List<RecordedAction>,
        telemetry: FlowTelemetry?,
        visual: RecordingVisualContext?,
    ): FlowReviewDiffReport {
        val rawTaps = raw.filterIsInstance<RecordedAction.Tap>()
        val optTaps = optimized.filterIsInstance<RecordedAction.Tap>()
        val optInputs = optimized.filterIsInstance<RecordedAction.InputText>()
        val rawTapKeys = rawTaps.map { tapKey(it) }.toSet()
        val optTapKeys = optTaps.map { tapKey(it) }.toSet()
        val missing = rawTaps
            .map { tapKey(it) }
            .filter { it !in optTapKeys }
            .distinct()
            .take(20)

        val longTransitions = buildList {
            telemetry?.transitions?.forEach { t ->
                if (t.kind == TransitionKind.ScreenTransition && t.deltaMs >= LONG_DELTA_MS) {
                    add(t.fromIndex)
                }
            }
            visual?.contentChangeCountPerGap?.forEachIndexed { idx, count ->
                if (count >= QUIET_UI_CHANGES) add(idx)
            }
            telemetry?.quiescenceGaps?.forEach { g ->
                if (g.contentChangeCount >= QUIET_UI_CHANGES) add(g.afterActionIndex)
            }
        }.distinct().sorted()

        val weakSteps = optimized.mapIndexedNotNull { index, action ->
            if (action is RecordedAction.Tap && action.weakSelector) index else null
        }

        val lostSteps = buildList {
            raw.forEachIndexed { index, action ->
                when (action) {
                    is RecordedAction.Tap -> {
                        val key = tapKey(action)
                        if (key !in optTapKeys) add("step $index Tap $key")
                    }
                    is RecordedAction.InputText -> {
                        val has = optInputs.any { it.viewId == action.viewId && it.isPassword == action.isPassword }
                        if (!has) add("step $index InputText id=${action.viewId}")
                    }
                    is RecordedAction.Scroll -> {
                        if (optimized.none { it is RecordedAction.Scroll && it.direction == action.direction }) {
                            add("step $index Scroll ${action.direction}")
                        }
                    }
                    else -> Unit
                }
            }
        }.take(25)

        val lostSummary = if (lostSteps.isEmpty()) {
            "(nessuno)"
        } else {
            lostSteps.joinToString("; ")
        }

        val summary = buildString {
            appendLine("### DIFF automatico grezzo vs ottimizzato (devi correggere in corrected_actions)")
            appendLine("- Step grezzi: ${raw.size}, step ottimizzati: ${optimized.size}")
            if (missing.isNotEmpty()) {
                appendLine("- Tap in REC ma assenti/spostati in B: ${missing.joinToString()}")
            }
            if (longTransitions.isNotEmpty()) {
                appendLine(
                    "- Indici che richiedono WaitForAnimation/wait (transizione lenta o UI animata): " +
                        longTransitions.joinToString(),
                )
            }
            if (weakSteps.isNotEmpty()) {
                appendLine("- Step con selettore debole (weakSelector) da migliorare con id/cd da albero a11y: $weakSteps")
            }
            val screenshotCount = visual?.snapshots?.count { it.jpegBytes != null } ?: 0
            appendLine("- Screenshot allegati: $screenshotCount su ${raw.size} step")
            if (raw.size == optimized.size && missing.isEmpty() && longTransitions.isEmpty()) {
                appendLine(
                    "- ATTENZIONE: B ha stesso numero step di A ma verifica wait mancanti e selettori dagli screenshot.",
                )
            }
            appendLine("- PRIORITÀ: ricostruisci da A + screenshot; non restituire B identico se A o gli screenshot mostrano differenze.")
        }

        return FlowReviewDiffReport(
            rawCount = raw.size,
            optimizedCount = optimized.size,
            missingTapLabels = missing,
            longTransitions = longTransitions,
            weakSelectorSteps = weakSteps,
            lostStepsSummary = lostSummary,
            summaryMarkdown = summary,
        )
    }

    /** Formatta lista per placeholder prompt; `"(nessuno)"` se vuota. */
    fun formatList(values: List<Any>): String =
        if (values.isEmpty()) "(nessuno)" else values.joinToString(", ")

    private fun tapKey(tap: RecordedAction.Tap): String =
        listOfNotNull(
            tap.viewId?.takeIf { it.isNotBlank() },
            tap.text?.takeIf { it.isNotBlank() },
            tap.contentDescription?.takeIf { it.isNotBlank() },
            tap.pointPercentX?.let { "x$it" },
        ).joinToString("|").ifBlank { "anon@${tap.timestampMs}" }
}
