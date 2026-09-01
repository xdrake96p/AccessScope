/**
 * Modelli per contesto visivo e semantico catturato durante REC Maestro (revisione AI).
 */
package dev.accessscope.scanner.recorder.model

import dev.accessscope.scanner.data.ScreenProtectionReason

/**
 * Nodo compatto dell'albero accessibility al momento di un'azione.
 *
 * @param viewId Resource-id corto (se presente).
 * @param text Testo visibile o hint.
 * @param contentDescription Content description TalkBack.
 * @param className Nome classe widget (es. `Button`).
 * @param role Ruolo a11y (es. `clickable`, `editable`).
 * @param boundsPx Rettangolo `[left, top, right, bottom]` in px schermo.
 * @param clickable Se il nodo è cliccabile.
 * @param editable Se accetta input testo.
 * @param password Se campo password.
 * @param checked Stato checked (checkbox/switch).
 * @param selected Se selezionato (tab/list item).
 * @param depth Profondità nell'albero (0 = root).
 */
data class CompactA11yNode(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val role: String? = null,
    val boundsPx: List<Int> = emptyList(),
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false,
    val checked: Boolean? = null,
    val selected: Boolean? = null,
    val depth: Int = 0,
)

/**
 * Snapshot visivo + semantico per un indice azione (solo RAM durante REC/review).
 *
 * @param actionIndex Indice in `actions` al momento della cattura.
 * @param jpegBytes JPEG scalato per multimodal Gemini; `null` se FLAG_SECURE o fallimento.
 * @param wireframeJpeg Wireframe sintetico da albero a11y (schermate protette).
 * @param treeSummary Albero a11y compatto della schermata.
 * @param windowTitle Titolo finestra se disponibile.
 * @param secureWindow `true` se screenshot bloccato (FLAG_SECURE).
 * @param protectionReason Motivo protezione schermata.
 * @param semanticTranscript Narrativa testuale per Gemini (A6).
 * @param deltaMs Ms dall'azione precedente.
 */
data class ActionVisualSnapshot(
    val actionIndex: Int,
    val jpegBytes: ByteArray? = null,
    val wireframeJpeg: ByteArray? = null,
    val treeSummary: List<CompactA11yNode> = emptyList(),
    val windowTitle: String? = null,
    val secureWindow: Boolean = false,
    val protectionReason: ScreenProtectionReason = ScreenProtectionReason.NONE,
    val semanticTranscript: String = "",
    val deltaMs: Long = 0L,
) {
    /** JPEG da allegare a Gemini (reale o wireframe). */
    fun bestImageBytes(): ByteArray? = jpegBytes ?: wireframeJpeg

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActionVisualSnapshot) return false
        return actionIndex == other.actionIndex &&
            jpegBytes.contentEquals(other.jpegBytes) &&
            wireframeJpeg.contentEquals(other.wireframeJpeg) &&
            treeSummary == other.treeSummary &&
            windowTitle == other.windowTitle &&
            secureWindow == other.secureWindow &&
            protectionReason == other.protectionReason &&
            semanticTranscript == other.semanticTranscript &&
            deltaMs == other.deltaMs
    }

    override fun hashCode(): Int {
        var result = actionIndex
        result = 31 * result + (jpegBytes?.contentHashCode() ?: 0)
        result = 31 * result + (wireframeJpeg?.contentHashCode() ?: 0)
        result = 31 * result + treeSummary.hashCode()
        result = 31 * result + (windowTitle?.hashCode() ?: 0)
        result = 31 * result + secureWindow.hashCode()
        result = 31 * result + protectionReason.hashCode()
        result = 31 * result + semanticTranscript.hashCode()
        result = 31 * result + deltaMs.hashCode()
        return result
    }
}

/**
 * Bundle completo passato alla revisione Gemini (riferimenti in RAM fino a dispose).
 *
 * @param snapshots Snapshot per indice azione.
 * @param contentChangeCountPerGap Eventi CONTENT_CHANGED tra azioni (allineati a telemetria).
 */
data class RecordingVisualContext(
    val snapshots: List<ActionVisualSnapshot> = emptyList(),
    val contentChangeCountPerGap: List<Int> = emptyList(),
)

private fun ByteArray?.contentEquals(other: ByteArray?): Boolean =
    when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> this.contentEquals(other)
    }
