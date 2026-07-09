/**
 * Tracciamento diagnostico strutturato — delega a [AppFileLogger] su file.
 */
package dev.accessscope.scanner.util

/**
 * Logger di debug legacy; scrive su file tramite [AppFileLogger].
 */
object DebugTrace {

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "scroll-debug",
    ) {
        AppFileLogger.log(hypothesisId, location, "$message runId=$runId", data)
    }
}
