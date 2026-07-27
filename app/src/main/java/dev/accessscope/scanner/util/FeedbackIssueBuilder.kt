/**
 * Costruisce URL GitHub Issues precompilati per feedback utente.
 */
package dev.accessscope.scanner.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Helper per aprire una issue GitHub con titolo e corpo precompilati.
 */
object FeedbackIssueBuilder {
    private const val ISSUE_URL = "https://github.com/xdrake96p/AccessScope/issues/new"
    private const val TEMPLATE = "feedback.yml"

    /** Tipo di segnalazione selezionabile dall'utente. */
    enum class FeedbackType(val label: String) {
        SCAN_INACCURATE("Scansione imprecisa"),
        BUG("Bug"),
        IMPROVEMENT("Miglioramento"),
        OTHER("Altro"),
    }

    /**
     * Genera l'URL per aprire GitHub Issues con template e campi precompilati.
     *
     * @param type Tipo di segnalazione scelto dall'utente.
     * @param description Testo libero descrittivo.
     * @param scanContext Contesto ultima scansione (opzionale).
     * @param deviceInfo Informazioni dispositivo (opzionale).
     * @return URL completo da passare a [android.content.Intent.ACTION_VIEW].
     */
    fun buildUrl(
        type: FeedbackType,
        description: String,
        scanContext: String? = null,
        deviceInfo: String? = null,
        reliabilityMdFileName: String? = null,
    ): String {
        val title = URLEncoder.encode("[Feedback] ${type.label}", StandardCharsets.UTF_8)
        val body = buildString {
            appendLine("## Tipo")
            appendLine(type.label)
            appendLine()
            appendLine("## Descrizione")
            appendLine(description.trim())
            if (!scanContext.isNullOrBlank()) {
                appendLine()
                appendLine("## Ultima scansione")
                appendLine(scanContext.trim())
            }
            if (!deviceInfo.isNullOrBlank()) {
                appendLine()
                appendLine("## Dispositivo")
                appendLine(deviceInfo.trim())
            }
            if (!reliabilityMdFileName.isNullOrBlank()) {
                appendLine()
                appendLine("## Report affidabilità")
                appendLine(
                    "File allegato tramite app: `$reliabilityMdFileName` " +
                        "(usa il foglio condividi per allegarlo alla issue).",
                )
            }
        }
        val encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8)
        return "$ISSUE_URL?template=$TEMPLATE&title=$title&body=$encodedBody"
    }

    /**
     * Formatta un riepilogo non sensibile dell'ultima scansione per allegare alla issue.
     */
    fun formatScanContext(
        packages: Set<String>,
        sessionId: String?,
        score: Int?,
        sampleViolations: List<String>,
    ): String = buildString {
        appendLine("- Package: ${packages.joinToString(", ").ifBlank { "n/d" }}")
        appendLine("- Session ID: ${sessionId ?: "n/d"}")
        appendLine("- Punteggio: ${score?.let { "$it/100" } ?: "n/d"}")
        if (sampleViolations.isNotEmpty()) {
            appendLine("- Violazioni esempio:")
            sampleViolations.forEach { appendLine("  - $it") }
        }
    }

    /**
     * URL issue precompilata per area Maestro (bug o miglioramento).
     *
     * @param bug `true` = bug, `false` = miglioramento.
     * @param deviceInfo Riga dispositivo/versione.
     * @return URL GitHub Issues.
     */
    fun buildMaestroUrl(bug: Boolean, deviceInfo: String? = null): String {
        val kind = if (bug) "Bug" else "Miglioramento"
        val title = URLEncoder.encode("[Maestro] $kind", StandardCharsets.UTF_8)
        val body = buildString {
            appendLine("## Area")
            appendLine("Maestro (Beta) — registrazione / Play / YAML")
            appendLine()
            appendLine("## Tipo")
            appendLine(kind)
            appendLine()
            appendLine("## Descrizione")
            appendLine("(Descrivi il problema o la proposta)")
            appendLine()
            appendLine("## Passi per riprodurre")
            appendLine("1. …")
            appendLine("2. …")
            if (!deviceInfo.isNullOrBlank()) {
                appendLine()
                appendLine("## Dispositivo")
                appendLine(deviceInfo.trim())
            }
        }
        val encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8)
        return "$ISSUE_URL?template=$TEMPLATE&title=$title&body=$encodedBody"
    }

    /**
     * Formatta informazioni dispositivo e versione app.
     */
    fun formatDeviceInfo(
        model: String,
        apiLevel: Int,
        appVersion: String,
    ): String = buildString {
        appendLine("- Modello: $model")
        appendLine("- API Android: $apiLevel")
        appendLine("- AccessScope: $appVersion")
    }
}
