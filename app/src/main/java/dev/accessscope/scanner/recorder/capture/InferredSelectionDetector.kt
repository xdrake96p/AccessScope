/**
 * Inferenza di selezioni utente non osservabili tramite eventi di accessibilità.
 *
 * Alcune app non emettono `TYPE_VIEW_CLICKED` sul tocco fisico di una riga di lista (la riga è
 * `clickable="true"` ma il tocco è gestito con un listener grezzo che bypassa `View.performClick()`)
 * e non emettono `TYPE_VIEW_TEXT_CHANGED` quando popolano il campo via `setText()`. Su queste app
 * il recorder, che osserva solo gli eventi, perde completamente il passaggio.
 *
 * Questo rilevatore non osserva eventi: confronta lo **stato dello schermo** prima e dopo e ricava
 * la selezione da una prova strutturale — *un campo ora contiene un testo che poco prima era
 * visibile altrove sullo schermo*. Non serve sapere cos'è un "picker": vale per rubriche, liste
 * IBAN, selettori di paese, dropdown e qualunque altra lista di scelta, in qualunque app.
 */
package dev.accessscope.scanner.recorder.capture

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Stato dello schermo utile all'inferenza.
 *
 * @property fieldValues Valore corrente dei campi (`viewId` completo → testo reale, mai l'hint).
 * @property visibleTexts Tutti i testi e contentDescription visibili, per risalire alla voce scelta.
 */
data class ScreenSnapshot(
    val fieldValues: Map<String, String>,
    val visibleTexts: Set<String>,
) {
    companion object {
        val EMPTY = ScreenSnapshot(emptyMap(), emptySet())
    }
}

/**
 * Ricava selezioni da confronto di stato schermo, senza dipendere da eventi né da nomi di id.
 */
object InferredSelectionDetector {

    /** Lunghezza minima di un valore perché il match sia considerato affidabile (anti-rumore). */
    private const val MIN_MATCH_LENGTH = 6

    /**
     * Fotografa lo stato corrente delle root.
     *
     * Considera "campo" un nodo `isEditable` **oppure** di classe `EditText`: alcune app rendono
     * i campi valorizzati da un picker non digitabili (`focusable="false"`, `isEditable=false`)
     * proprio per forzare la selezione dalla lista — restano comunque i campi da osservare.
     *
     * @param roots Root correnti (una per finestra visibile).
     */
    fun snapshot(roots: List<AccessibilityNodeInfo>): ScreenSnapshot {
        val fields = mutableMapOf<String, String>()
        val texts = mutableSetOf<String>()

        fun walk(node: AccessibilityNodeInfo) {
            val hint = node.hintText?.toString()?.trim()
            val text = node.text?.toString()?.trim()
            val contentDescription = node.contentDescription?.toString()?.trim()

            val realText = text?.takeIf { it.isNotBlank() && it != hint }
            val isField = node.isEditable ||
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true
            if (isField) {
                val viewId = node.viewIdResourceName
                if (!viewId.isNullOrBlank() && realText != null) {
                    fields[viewId] = realText
                }
            } else {
                realText?.let { texts += it }
                contentDescription?.takeIf { it.isNotBlank() }?.let { texts += it }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }

        roots.forEach { walk(it) }
        return ScreenSnapshot(fieldValues = fields, visibleTexts = texts)
    }

    /**
     * Selezione inferita: un campo ha assunto un valore che era visibile poco prima sullo schermo.
     *
     * @param before Valori campo prima del cambio schermo.
     * @param after Valori campo dopo il cambio schermo.
     * @param recentVisibleTexts Testi visti di recente (tipicamente la lista appena chiusa).
     * @param ignoredFieldIds Campi da escludere perché il valore è stato digitato dall'utente
     *   (già registrato come `inputText`) — evita di duplicare uno step di digitazione.
     * @return Valore selezionato da usare come selettore testo, o `null` se non c'è prova.
     */
    fun inferSelection(
        before: Map<String, String>,
        after: Map<String, String>,
        recentVisibleTexts: Set<String>,
        ignoredFieldIds: Set<String> = emptySet(),
    ): InferredSelection? {
        if (recentVisibleTexts.isEmpty()) return null
        val normalizedVisible = recentVisibleTexts.associateBy { normalize(it) }

        for ((viewId, value) in after) {
            if (viewId in ignoredFieldIds) continue
            if (before[viewId] == value) continue
            if (value.length < MIN_MATCH_LENGTH) continue
            val match = matchVisibleText(normalize(value), normalizedVisible) ?: continue
            return InferredSelection(fieldViewId = viewId, value = value, matchedVisibleText = match)
        }
        return null
    }

    /**
     * Cerca il testo visibile corrispondente al valore del campo.
     *
     * Il match è tollerante per contenimento (non solo uguaglianza) perché la riga di lista e il
     * campo spesso formattano lo stesso dato in modo diverso — es. un IBAN mostrato a gruppi di
     * quattro cifre nella lista e compatto nel campo.
     */
    private fun matchVisibleText(
        normalizedValue: String,
        normalizedVisible: Map<String, String>,
    ): String? {
        normalizedVisible[normalizedValue]?.let { return it }
        return normalizedVisible.entries
            .firstOrNull { (visible, _) ->
                visible.length >= MIN_MATCH_LENGTH &&
                    (visible.contains(normalizedValue) || normalizedValue.contains(visible))
            }
            ?.value
    }

    /** Confronto insensibile a maiuscole, spaziatura e formattazione di gruppo. */
    private fun normalize(text: String): String =
        text.lowercase().filter { !it.isWhitespace() }

    /**
     * Esito inferenza.
     *
     * @property fieldViewId Campo che ha assunto il valore.
     * @property value Valore assunto dal campo.
     * @property matchedVisibleText Testo (non normalizzato) che era visibile e ha prodotto il match:
     *   è il selettore migliore per il replay, perché è ciò che l'utente ha realmente toccato.
     */
    data class InferredSelection(
        val fieldViewId: String,
        val value: String,
        val matchedVisibleText: String,
    )
}
