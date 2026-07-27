/**
 * Candidato selettore nella catena di fallback Maestro (id → testo → point).
 */
package dev.accessscope.scanner.recorder.model

/**
 * Un ramo della catena usata da Play/export quando il selettore primario fallisce.
 *
 * @property viewId Resource id (completo o corto).
 * @property text Testo esatto.
 * @property contentDescription Content description.
 * @property pointPercentX Ascissa % (0–100), solo ultima risorsa.
 * @property pointPercentY Ordinata % (0–100).
 */
data class SelectorCandidate(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val pointPercentX: Float? = null,
    val pointPercentY: Float? = null,
) {
    /** `true` se non ha alcun selettore utilizzabile. */
    fun isBlank(): Boolean =
        viewId.isNullOrBlank() &&
            text.isNullOrBlank() &&
            contentDescription.isNullOrBlank() &&
            (pointPercentX == null || pointPercentY == null)

    /** Chiave dedupe per catene. */
    fun dedupeKey(): String =
        listOf(
            viewId.orEmpty(),
            text.orEmpty(),
            contentDescription.orEmpty(),
            pointPercentX?.toString().orEmpty(),
            pointPercentY?.toString().orEmpty(),
        ).joinToString("|")
}
