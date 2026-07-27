/**
 * Routing eventi accessibilità: registrazione vs scan WCAG.
 */
package dev.accessscope.scanner.service

/**
 * Logica pura per isolare recorder e scan (testabile senza service bound).
 */
object AccessibilityEventRouter {

    /**
     * Eventi devono andare al recorder Maestro.
     *
     * @param isRecording Registrazione attiva.
     */
    fun routesToRecording(isRecording: Boolean): Boolean = isRecording

    /**
     * Eventi devono alimentare lo scan WCAG.
     *
     * @param isRecording Registrazione attiva (blocca scan).
     * @param isScanning Scan attivo.
     * @param isTargetPackage Evento dal package monitorato.
     */
    fun routesToScan(isRecording: Boolean, isScanning: Boolean, isTargetPackage: Boolean): Boolean =
        !isRecording && isScanning && isTargetPackage
}
