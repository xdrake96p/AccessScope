/**
 * Telemetria raccolta durante la registrazione Maestro.
 */
package dev.accessscope.scanner.recorder.telemetry

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.ScreenFingerprint
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.QuiescenceGap
import dev.accessscope.scanner.recorder.model.ScreenSnapshot
import dev.accessscope.scanner.recorder.optimization.timing.TransitionTimingAnalyzer

/**
 * Accumula snapshot e eventi CONTENT_CHANGED per wait di quiescenza.
 */
class RecordingTelemetry {

    private val snapshots = mutableListOf<ScreenSnapshot>()
    private val contentChangedAt = mutableListOf<Long>()
    private val actionTimestamps = mutableListOf<Long>()

    /**
     * Nota un [TYPE_WINDOW_CONTENT_CHANGED] (anche senza nuova azione).
     */
    fun onContentChanged(timestampMs: Long = System.currentTimeMillis()) {
        contentChangedAt += timestampMs
        // Bound memoria: ultimi ~500 eventi.
        if (contentChangedAt.size > 500) {
            contentChangedAt.subList(0, contentChangedAt.size - 400).clear()
        }
    }

    /**
     * Registra fingerprint schermata dopo un’azione.
     */
    fun capture(
        root: AccessibilityNodeInfo?,
        packageName: String,
        actionIndex: Int,
        timestampMs: Long,
    ) {
        while (actionTimestamps.size <= actionIndex) {
            actionTimestamps += timestampMs
        }
        actionTimestamps[actionIndex] = timestampMs
        if (root == null) {
            snapshots += ScreenSnapshot(
                fingerprint = "unknown",
                title = null,
                packageName = packageName,
                timestampMs = timestampMs,
                actionIndex = actionIndex,
            )
            return
        }
        val title = root.window?.title?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Schermata"
        val fingerprint = ScreenFingerprint.compute(root, packageName, title)
        snapshots += ScreenSnapshot(
            fingerprint = fingerprint,
            title = title,
            packageName = packageName,
            timestampMs = timestampMs,
            actionIndex = actionIndex,
        )
    }

    /** Azzera telemetria (nuova registrazione). */
    fun reset() {
        snapshots.clear()
        contentChangedAt.clear()
        actionTimestamps.clear()
    }

    /**
     * Costruisce telemetria con transizioni e gap di quiescenza.
     *
     * @param actionTimestampsOverride Timestamp per indice (se diverso dal buffer interno).
     */
    fun build(actionTimestampsOverride: List<Long>): FlowTelemetry {
        val stamps = actionTimestampsOverride.ifEmpty { actionTimestamps.toList() }
        val transitions = TransitionTimingAnalyzer.buildTransitions(snapshots, stamps)
        val gaps = buildQuiescenceGaps(stamps, contentChangedAt)
        return FlowTelemetry(
            snapshots = snapshots.toList(),
            transitions = transitions,
            quiescenceGaps = gaps,
        )
    }

    /** Snapshot grezzi (debug). */
    fun rawSnapshots(): List<ScreenSnapshot> = snapshots.toList()

    companion object {
        private const val QUIET_THRESHOLD_MS = 700L

        /**
         * Deriva [QuiescenceGap] tra azioni consecutive da timestamp CONTENT_CHANGED.
         */
        fun buildQuiescenceGaps(
            actionTimestamps: List<Long>,
            contentChangedAt: List<Long>,
        ): List<QuiescenceGap> {
            if (actionTimestamps.size < 2) return emptyList()
            return buildList {
                for (i in 0 until actionTimestamps.size - 1) {
                    val t0 = actionTimestamps[i]
                    val t1 = actionTimestamps[i + 1]
                    if (t1 <= t0) continue
                    val inBetween = contentChangedAt.filter { it in (t0 + 1) until t1 }
                    if (inBetween.isEmpty()) {
                        val quiet = t1 - t0
                        if (quiet >= QUIET_THRESHOLD_MS) {
                            add(
                                QuiescenceGap(
                                    afterActionIndex = i,
                                    quietMs = quiet.coerceAtMost(8_000L),
                                    contentBurstMs = 0L,
                                    contentChangeCount = 0,
                                ),
                            )
                        }
                        continue
                    }
                    val first = inBetween.first()
                    val last = inBetween.last()
                    val burst = (last - first).coerceAtLeast(0L)
                    val quiet = (t1 - last).coerceAtLeast(0L)
                    add(
                        QuiescenceGap(
                            afterActionIndex = i,
                            quietMs = quiet.coerceAtMost(8_000L),
                            contentBurstMs = burst.coerceAtMost(8_000L),
                            contentChangeCount = inBetween.size,
                        ),
                    )
                }
            }
        }

        /**
         * Timeout wait suggerito da un gap (burst + quiet + margine).
         */
        fun suggestedWaitMs(gap: QuiescenceGap): Long {
            val base = gap.contentBurstMs + gap.quietMs.coerceAtLeast(QUIET_THRESHOLD_MS) + 200L
            return base.coerceIn(700L, 8_000L)
        }
    }
}
