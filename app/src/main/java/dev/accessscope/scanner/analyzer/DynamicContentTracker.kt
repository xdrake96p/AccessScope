/**
 * Monitoraggio dei cambiamenti di contenuto UI senza corrispondenti annunci di accessibilità.
 *
 * Rileva pattern di aggiornamento silenzioso (es. assenza di live region o
 * [android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT]) che possono
 * impedire agli utenti di screen reader di percepire variazioni dinamiche.
 */
package dev.accessscope.scanner.analyzer

import java.util.concurrent.ConcurrentHashMap

/**
 * Traccia, per finestra e package, la frequenza dei cambi di contenuto rispetto
 * agli ultimi annunci accessibility ricevuti.
 */
class DynamicContentTracker {

    private val contentChanges = ConcurrentHashMap<String, WindowState>()

    /**
     * Stato interno di una singola finestra monitorata.
     *
     * @property changeCount Numero cumulativo di eventi di cambio contenuto osservati.
     * @property lastAnnouncementMs Timestamp dell'ultimo annuncio accessibility ricevuto (epoch ms).
     * @property lastChangeMs Timestamp dell'ultimo cambio contenuto osservato (epoch ms).
     */
    data class WindowState(
        var changeCount: Int = 0,
        var lastAnnouncementMs: Long = 0,
        var lastChangeMs: Long = 0,
    )

    /**
     * Registra un evento di cambio contenuto per la finestra indicata.
     *
     * @param packageName Package dell'applicazione che ha emesso il cambio.
     * @param windowId Identificatore della finestra (tipicamente da [android.view.accessibility.AccessibilityWindowInfo]).
     */
    fun onContentChanged(packageName: String, windowId: Int) {
        val key = "$packageName:$windowId"
        val now = System.currentTimeMillis()
        val state = contentChanges.getOrPut(key) { WindowState() }
        state.changeCount++
        state.lastChangeMs = now
    }

    /**
     * Registra un annuncio accessibility ricevuto, aggiornando tutte le finestre del package.
     *
     * @param packageName Package dell'applicazione che ha emesso l'annuncio.
     */
    fun onAnnouncement(packageName: String) {
        val now = System.currentTimeMillis()
        contentChanges.keys.filter { it.startsWith("$packageName:") }.forEach { key ->
            contentChanges[key]?.lastAnnouncementMs = now
        }
    }

    /**
     * Verifica se la finestra mostra contenuto dinamico senza annunci recenti.
     *
     * @param packageName Package dell'applicazione in analisi.
     * @param windowId Identificatore della finestra da valutare.
     * @return `true` se ci sono stati almeno 4 cambi senza annuncio successivo entro 10 secondi.
     */
    fun isSilentDynamicContent(packageName: String, windowId: Int): Boolean {
        val key = "$packageName:$windowId"
        val state = contentChanges[key] ?: return false
        val now = System.currentTimeMillis()
        return state.changeCount >= 4 &&
            state.lastAnnouncementMs < state.lastChangeMs &&
            now - state.lastChangeMs < 10_000
    }

    /**
     * Azzera tutto lo stato tracciato.
     */
    fun reset() = contentChanges.clear()
}
