/**
 * Mutex applicazione tra registrazione Maestro e scan WCAG.
 */
package dev.accessscope.scanner.recorder

/**
 * Policy mutex scan ↔ recorder (testabile senza UI).
 */
object ScanRecorderMutexPolicy {

    /**
     * Scan può partire solo se non c’è registrazione attiva.
     *
     * @param isRecording Registrazione Maestro attiva.
     */
    fun canStartScan(isRecording: Boolean): Boolean = !isRecording

    /**
     * Registrazione può partire solo se non c’è scan attivo.
     *
     * @param isScanning Scan WCAG attivo.
     */
    fun canStartRecording(isScanning: Boolean): Boolean = !isScanning
}
