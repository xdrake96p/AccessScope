/**
 * Validazione identificatori accettati dal bridge (sessionId, package name).
 */
package dev.accessscope.scanner.bridge

object BridgeIds {

    private val SESSION_ID = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )

    /** Package Android: lettere, cifre, `_`, `.` — niente path traversal. */
    private val PACKAGE_NAME = Regex("""^[a-zA-Z][\w.]{0,254}$""")

    fun isValidSessionId(sessionId: String): Boolean =
        sessionId.isNotBlank() && SESSION_ID.matches(sessionId)

    fun isValidPackageName(packageName: String): Boolean =
        packageName.isNotBlank() &&
            PACKAGE_NAME.matches(packageName) &&
            !packageName.contains("..")
}
