/**
 * Metadati applicazione per selezione e visualizzazione nell'UI.
 */
package dev.accessscope.scanner.data

/** Metadati di un'applicazione installata sul dispositivo. */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean = false,
    val isFavorite: Boolean = false,
)
