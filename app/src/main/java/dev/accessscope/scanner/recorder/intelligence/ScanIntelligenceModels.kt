/**
 * Intelligence da sessioni scan WCAG archiviate per ottimizzazione Maestro.
 */
package dev.accessscope.scanner.recorder.intelligence

/**
 * Metadati schermata da cronologia scan.
 *
 * @param fingerprint Impronta schermata visitata.
 * @param title Titolo umano.
 * @param visitCount Numero visite nella sessione.
 * @param visitIndex Ordine prima visita (0-based).
 * @param typicalNextFingerprint Fingerprint più frequente subito dopo (se noto).
 */
data class ScreenIntel(
    val fingerprint: String,
    val title: String,
    val visitCount: Int,
    val visitIndex: Int,
    val typicalNextFingerprint: String? = null,
)

/**
 * Affidabilità di un viewId osservato in scan.
 *
 * @param viewId Id risorsa (short o completo).
 * @param screenFingerprint Schermata dove è stato osservato.
 * @param occurrenceCount Occorrenze in violazioni/check della sessione.
 */
data class ElementIntel(
    val viewId: String,
    val screenFingerprint: String,
    val occurrenceCount: Int,
)

/**
 * Bundle intelligence per un package target.
 *
 * @param screens Map fingerprint → intel schermata.
 * @param elements Map viewId short → intel elemento.
 * @param mainPathFingerprints Fingerprints sul percorso principale scan (ordine visita).
 */
data class ScanIntelligenceBundle(
    val screens: Map<String, ScreenIntel> = emptyMap(),
    val elements: Map<String, ElementIntel> = emptyMap(),
    val mainPathFingerprints: List<String> = emptyList(),
)
