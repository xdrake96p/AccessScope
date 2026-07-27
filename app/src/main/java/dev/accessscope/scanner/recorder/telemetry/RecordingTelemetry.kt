/**
 * Raccolta snapshot schermata durante registrazione Maestro.
 */
package dev.accessscope.scanner.recorder.telemetry

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.ScreenFingerprint
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.ScreenSnapshot
import dev.accessscope.scanner.recorder.optimization.timing.TransitionTimingAnalyzer

/**
 * Accumula snapshot per ogni batch di azioni registrate.
 */
class RecordingTelemetry {

    private val snapshots = mutableListOf<ScreenSnapshot>()

    /**
     * Registra fingerprint schermata dopo un’azione.
     *
     * @param root Root finestra attiva (non viene riciclato).
     * @param packageName Package target.
     * @param actionIndex Indice ultima azione in lista.
     * @param timestampMs Timestamp evento.
     */
    fun capture(
        root: AccessibilityNodeInfo?,
        packageName: String,
        actionIndex: Int,
        timestampMs: Long,
    ) {
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
    }

    /**
     * Costruisce telemetria con transizioni analizzate.
     *
     * @param actionTimestamps Timestamp per indice azione (per delta tra azioni).
     * @return [FlowTelemetry] completa.
     */
    fun build(actionTimestamps: List<Long>): FlowTelemetry {
        val transitions = TransitionTimingAnalyzer.buildTransitions(snapshots, actionTimestamps)
        return FlowTelemetry(snapshots = snapshots.toList(), transitions = transitions)
    }

    /** Snapshot grezzi (debug). */
    fun rawSnapshots(): List<ScreenSnapshot> = snapshots.toList()
}
