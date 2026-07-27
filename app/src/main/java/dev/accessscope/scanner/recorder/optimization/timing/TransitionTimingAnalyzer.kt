/**
 * Analisi tempi tra azioni registrate per timeout adattivi Maestro.
 */
package dev.accessscope.scanner.recorder.optimization.timing

import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordedTransition
import dev.accessscope.scanner.recorder.model.ScreenSnapshot
import dev.accessscope.scanner.recorder.model.TransitionKind

/**
 * Deriva transizioni e timeout da snapshot e timestamp azioni.
 */
object TransitionTimingAnalyzer {

    private const val OVERLAY_MAX_MS = 1_500L

    /**
     * Costruisce transizioni tra azioni consecutive.
     *
     * @param snapshots Snapshot per indice azione.
     * @param actionTimestamps `timestampMs` per ogni azione (allineato a indici).
     * @return Lista transizioni classificate.
     */
    fun buildTransitions(
        snapshots: List<ScreenSnapshot>,
        actionTimestamps: List<Long>,
    ): List<RecordedTransition> {
        if (actionTimestamps.size < 2) return emptyList()
        val fpByIndex = snapshots.associateBy { it.actionIndex }
        return buildList {
            for (i in 0 until actionTimestamps.size - 1) {
                val delta = actionTimestamps[i + 1] - actionTimestamps[i]
                val fromFp = fpByIndex[i]?.fingerprint
                val toFp = fpByIndex[i + 1]?.fingerprint
                val kind = classify(fromFp, toFp, delta)
                add(
                    RecordedTransition(
                        fromIndex = i,
                        toIndex = i + 1,
                        deltaMs = delta,
                        fromFingerprint = fromFp,
                        toFingerprint = toFp,
                        kind = kind,
                    ),
                )
            }
        }
    }

    /**
     * Primo delta di transizione schermata osservato in telemetria.
     *
     * @param telemetry Telemetria con transizioni.
     * @return Millisecondi o `null`.
     */
    fun firstScreenTransitionMs(telemetry: FlowTelemetry?): Long? =
        telemetry?.transitions
            ?.firstOrNull { it.kind == TransitionKind.ScreenTransition }
            ?.deltaMs

    /**
     * Timeout animazione dopo launch: `clamp(delta primo ScreenTransition * 1.3, 1500..8000)`.
     *
     * @param telemetry Telemetria opzionale.
     * @return Timeout in ms (default 2000).
     */
    fun launchAnimationTimeoutMs(telemetry: FlowTelemetry?): Long {
        val observed = firstScreenTransitionMs(telemetry)
        return clamp(
            observed?.let { (it * 1.3).toLong() },
            minMs = 1_500L,
            maxMs = 8_000L,
            fallbackMs = 2_000L,
        )
    }

    /**
     * Timeout `extendedWaitUntil` adattivo.
     *
     * @param observedMs Delta osservato in telemetria o revisit scan.
     * @param fallbackMs Valore se osservato assente.
     * @return Timeout clamp 3000..15000.
     */
    fun extendedWaitTimeoutMs(observedMs: Long?, fallbackMs: Long = 5_000L): Long =
        clamp(
            observedMs?.let { (it * 1.5).toLong() },
            minMs = 3_000L,
            maxMs = 15_000L,
            fallbackMs = fallbackMs,
        )

    /**
     * Breve wait same-screen tap→input (300..800ms se delta < 500ms).
     *
     * @param deltaMs Delta osservato tra azioni.
     * @return Timeout ms o `null` se non serve wait.
     */
    fun sameScreenShortWaitMs(deltaMs: Long?): Long? {
        if (deltaMs == null || deltaMs >= 500L) return null
        return deltaMs.coerceIn(300L, 800L)
    }

    /**
     * Clamp con fallback.
     *
     * @param value Valore grezzo.
     * @param minMs Minimo.
     * @param maxMs Massimo.
     * @param fallbackMs Se value null.
     */
    fun clamp(value: Long?, minMs: Long, maxMs: Long, fallbackMs: Long): Long {
        val raw = value ?: return fallbackMs
        return raw.coerceIn(minMs, maxMs)
    }

    private fun classify(fromFp: String?, toFp: String?, deltaMs: Long): TransitionKind {
        if (fromFp == null || toFp == null) return TransitionKind.SameScreen
        if (fromFp == toFp) return TransitionKind.SameScreen
        if (deltaMs <= OVERLAY_MAX_MS && similarFingerprint(fromFp, toFp)) {
            return TransitionKind.PossibleOverlay
        }
        return TransitionKind.ScreenTransition
    }

    private fun similarFingerprint(a: String, b: String): Boolean {
        val titleA = a.substringAfter("::").substringBefore("::")
        val titleB = b.substringAfter("::").substringBefore("::")
        return titleA == titleB || titleA.startsWith(titleB) || titleB.startsWith(titleA)
    }
}
