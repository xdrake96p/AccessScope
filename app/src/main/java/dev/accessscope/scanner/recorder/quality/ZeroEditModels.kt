/**
 * Modelli del contratto ZeroEdit: YAML Maestro pronto all’uso senza edit manuali.
 */
package dev.accessscope.scanner.recorder.quality

import dev.accessscope.scanner.recorder.optimization.lint.FlowLintIssue
import dev.accessscope.scanner.recorder.optimization.lint.LintSeverity

/**
 * Gravità bloccante per il contratto ZeroEdit (oltre a WARNING/INFO del lint).
 *
 * Gli Error impediscono di pubblicare YAML “quasi buono”.
 */
enum class ZeroEditSeverity {
    /** Blocca il save/export pubblico. */
    Error,
    /** Avviso non bloccante. */
    Warning,
    /** Informativo. */
    Info,
}

/**
 * Singola violazione del contratto ZeroEdit.
 *
 * @property stepIndex Indice 0-based nello step list (o -1 se globale).
 * @property code Codice macchina (es. `POINT_ONLY`).
 * @property severity Gravità.
 * @property message Messaggio utente in italiano.
 */
data class ZeroEditIssue(
    val stepIndex: Int,
    val code: String,
    val severity: ZeroEditSeverity,
    val message: String,
)

/**
 * Esito del gate ZeroEdit su un flusso ottimizzato.
 *
 * @property issues Elenco segnalazioni.
 * @property actions Azioni eventualmente heal-ate (stesso input se nessun heal).
 */
data class ZeroEditReport(
    val issues: List<ZeroEditIssue>,
    val actions: List<dev.accessscope.scanner.recorder.RecordedAction>,
) {
    /** `true` se ci sono Error bloccanti. */
    val hasErrors: Boolean get() = issues.any { it.severity == ZeroEditSeverity.Error }

    /** Contatore Error. */
    val errorCount: Int get() = issues.count { it.severity == ZeroEditSeverity.Error }

    /** Contatore Warning. */
    val warningCount: Int get() = issues.count { it.severity == ZeroEditSeverity.Warning }

    /**
     * Messaggio sintetico per Toast/UI.
     * @return Testo breve o `null` se tutto ok.
     */
    fun userSummary(): String? {
        if (!hasErrors && warningCount == 0) return null
        return buildString {
            if (hasErrors) append("$errorCount problemi bloccanti")
            if (warningCount > 0) {
                if (isNotEmpty()) append(" · ")
                append("$warningCount avvisi")
            }
            val first = issues.firstOrNull { it.severity == ZeroEditSeverity.Error }
                ?: issues.firstOrNull()
            if (first != null) {
                append(": ")
                if (first.stepIndex >= 0) append("step ${first.stepIndex + 1} — ")
                append(first.message)
            }
        }
    }
}

/**
 * Converte una [FlowLintIssue] in [ZeroEditIssue] (mappa gravità).
 *
 * @param issue Issue del FlowLinter.
 * @return Issue ZeroEdit equivalente.
 */
fun FlowLintIssue.toZeroEditIssue(): ZeroEditIssue {
    val sev = when (severity) {
        LintSeverity.ERROR -> ZeroEditSeverity.Error
        LintSeverity.WARNING -> ZeroEditSeverity.Warning
        LintSeverity.INFO -> ZeroEditSeverity.Info
    }
    return ZeroEditIssue(
        stepIndex = stepIndex,
        code = rule.name,
        severity = sev,
        message = message,
    )
}
