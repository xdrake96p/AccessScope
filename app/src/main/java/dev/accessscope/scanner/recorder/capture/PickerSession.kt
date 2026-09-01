/**
 * Stato sessione sheet picker (rubrica / IBAN / beneficiario) durante registrazione Maestro.
 */
package dev.accessscope.scanner.recorder.capture

/**
 * Traccia apertura/chiusura overlay picker per abilitare tap su voci lista in REC.
 */
class PickerSession {

    private var state: State = State.Closed
    private var lastIconTapAtMs: Long = 0L

    private sealed class State {
        data object Closed : State()
        data class Opening(val iconTapAtMs: Long) : State()
        data class Open(val title: String, val openedAtMs: Long) : State()
    }

    /** @return `true` se un picker è aperto o in apertura. */
    fun isOpen(): Boolean = state !is State.Closed

    /** Titolo sheet corrente, se noto. */
    fun title(): String? = (state as? State.Open)?.title

    /** Tap su icona rubrica/IBAN — abilita assert sheet e tap lista. */
    fun onIconTap(now: Long) {
        lastIconTapAtMs = now
        state = State.Opening(now)
    }

    /** Sheet comparso (WINDOW_STATE / overlay rilevato). */
    fun onPickerTitle(title: String, now: Long) {
        state = State.Open(title, now)
    }

    /** Voce lista selezionata o sheet chiuso. */
    fun close() {
        state = State.Closed
    }

    fun reset() {
        state = State.Closed
        lastIconTapAtMs = 0L
    }

    /**
     * Assert orphan guard: sheet senza tap icona recente.
     *
     * @param now Timestamp corrente.
     * @param windowMs Finestra massima dopo tap icona.
     */
    fun hasRecentIconTap(now: Long, windowMs: Long = ICON_TAP_ASSERT_WINDOW_MS): Boolean =
        lastIconTapAtMs > 0L && now - lastIconTapAtMs <= windowMs

    private companion object {
        const val ICON_TAP_ASSERT_WINDOW_MS = 3_000L
    }
}
