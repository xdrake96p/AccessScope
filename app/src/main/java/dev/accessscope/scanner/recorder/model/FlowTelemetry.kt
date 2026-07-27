/**
 * Telemetria raccolta durante la registrazione Maestro (snapshot schermata + transizioni).
 */
package dev.accessscope.scanner.recorder.model

/**
 * Snapshot di schermata al momento di un’azione registrata.
 *
 * @param fingerprint Impronta [ScreenFingerprint].
 * @param title Titolo display o fallback.
 * @param packageName Package root finestra.
 * @param timestampMs Timestamp evento.
 * @param actionIndex Indice azione in `actions` al momento dello snapshot.
 */
data class ScreenSnapshot(
    val fingerprint: String,
    val title: String?,
    val packageName: String,
    val timestampMs: Long,
    val actionIndex: Int,
)

/** Classificazione transizione tra due azioni consecutive. */
enum class TransitionKind {
    /** Stessa schermata logica (fingerprint uguale). */
    SameScreen,

    /** Navigazione tra schermate distinte. */
    ScreenTransition,

    /** Overlay/popup breve (fingerprint simile, delta corto). */
    PossibleOverlay,
}

/**
 * Transizione temporale tra due azioni con fingerprint associati.
 *
 * @param fromIndex Indice azione precedente.
 * @param toIndex Indice azione successiva.
 * @param deltaMs Differenza `timestampMs`.
 * @param fromFingerprint Fingerprint allo snapshot `fromIndex`.
 * @param toFingerprint Fingerprint allo snapshot `toIndex`.
 * @param kind Classificazione transizione.
 */
data class RecordedTransition(
    val fromIndex: Int,
    val toIndex: Int,
    val deltaMs: Long,
    val fromFingerprint: String?,
    val toFingerprint: String?,
    val kind: TransitionKind,
)

/**
 * Telemetria completa di una registrazione.
 *
 * @param snapshots Snapshot per indice azione.
 * @param transitions Transizioni derivate da snapshot e timestamp.
 * @param quiescenceGaps Quiet period tra azioni (da WINDOW_CONTENT_CHANGED).
 */
data class FlowTelemetry(
    val snapshots: List<ScreenSnapshot> = emptyList(),
    val transitions: List<RecordedTransition> = emptyList(),
    val quiescenceGaps: List<QuiescenceGap> = emptyList(),
)

/**
 * Quiet window osservata dopo un’azione (loader finito → UI stabile).
 *
 * @param afterActionIndex Indice azione dopo cui attendere.
 * @param quietMs Ms senza CONTENT_CHANGED prima dell’azione successiva.
 * @param contentBurstMs Durata burst di aggiornamenti UI tra le due azioni.
 * @param contentChangeCount Numero eventi CONTENT_CHANGED nel intervallo.
 */
data class QuiescenceGap(
    val afterActionIndex: Int,
    val quietMs: Long,
    val contentBurstMs: Long = 0L,
    val contentChangeCount: Int = 0,
)
