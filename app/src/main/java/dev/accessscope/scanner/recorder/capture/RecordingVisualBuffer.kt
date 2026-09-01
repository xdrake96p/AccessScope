/**
 * Buffer in-memory per snapshot visivi Maestro (JPEG + albero a11y per azione).
 *
 * Lifecycle: creato all'avvio REC, svuotato dopo revisione AI o cancel/stop senza save.
 */
package dev.accessscope.scanner.recorder.capture

import dev.accessscope.scanner.recorder.model.ActionVisualSnapshot
import dev.accessscope.scanner.recorder.model.RecordingVisualContext

/**
 * Holder thread-safe per snapshot per indice azione (solo RAM).
 */
class RecordingVisualBuffer {

    private val lock = Any()
    private val byIndex = linkedMapOf<Int, ActionVisualSnapshot>()
    private val contentChangesBetween = mutableListOf<Int>()

    /** Numero snapshot memorizzati. */
    val size: Int get() = synchronized(lock) { byIndex.size }

    /**
     * Registra snapshot per [actionIndex].
     *
     * @param snapshot Dati visivi/semantici; sostituisce eventuale entry precedente.
     */
    fun put(snapshot: ActionVisualSnapshot) {
        synchronized(lock) {
            byIndex[snapshot.actionIndex] = snapshot
        }
    }

    /**
     * Incrementa contatore CONTENT_CHANGED tra azione [afterIndex] e la successiva.
     */
    fun noteContentChanged(afterIndex: Int) {
        synchronized(lock) {
            while (contentChangesBetween.size <= afterIndex) {
                contentChangesBetween += 0
            }
            contentChangesBetween[afterIndex] = contentChangesBetween[afterIndex] + 1
        }
    }

    /**
     * Costruisce contesto per revisione AI (copia difensiva).
     */
    fun toContext(): RecordingVisualContext = synchronized(lock) {
        RecordingVisualContext(
            snapshots = byIndex.values.sortedBy { it.actionIndex },
            contentChangeCountPerGap = contentChangesBetween.toList(),
        )
    }

    /**
     * Libera tutta la RAM (JPEG + metadati). Obbligatorio post-review.
     */
    fun clear() {
        synchronized(lock) {
            byIndex.clear()
            contentChangesBetween.clear()
        }
    }
}
