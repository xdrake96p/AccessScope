/**
 * Formattazione report Play Maestro in testo condivisibile (cliente / QA).
 */
package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.PlayExecutionReport
import dev.accessscope.scanner.recorder.model.PlayRunKind
import dev.accessscope.scanner.recorder.model.PlayStepResult
import dev.accessscope.scanner.recorder.model.PlayStepStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Genera report testuale leggibile per condivisione o archivio.
 */
object PlayReportFormatter {

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY)

    /**
     * Report completo multilinea in italiano.
     *
     * @param report Report persistito.
     * @return Testo pronto per share/email.
     */
    fun formatFull(report: PlayExecutionReport): String = buildString {
        val kindLabel = if (report.kind == PlayRunKind.PLAY) "Play" else "Validate"
        val esito = if (report.success) "OK" else "KO"
        appendLine("═══════════════════════════════════════")
        appendLine("AccessScope — Report esecuzione Maestro")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Flusso:     ${report.flowName}")
        appendLine("ID flusso:  ${report.flowId}")
        appendLine("App:        ${report.appLabel} (${report.appId})")
        appendLine("Tipo:       $kindLabel")
        appendLine("Run ID:     ${report.runId}")
        appendLine("Inizio:     ${dateFmt.format(Date(report.startedAtMs))}")
        appendLine("Fine:       ${dateFmt.format(Date(report.finishedAtMs))}")
        appendLine("Durata:     ${report.durationMs} ms")
        if (report.kind == PlayRunKind.PLAY && report.clearState) {
            appendLine("Avvio:      cold launch (clear state)")
        }
        appendLine()
        appendLine("ESITO:      $esito")
        appendLine("Step:       ${report.passedSteps} OK · ${report.failedSteps} KO · " +
            "${report.skippedOptionalSteps} skip optional · ${report.totalSteps} totali")
        if (report.selectorWinsCount > 0) {
            appendLine("Selettori:  ${report.selectorWinsCount} ramo alternativo usato")
        }
        report.errorMessage?.let { appendLine("Errore:     $it") }
        appendLine()
        appendLine("── Dettaglio step ──")
        appendLine()
        report.steps.forEach { appendLine(formatStepLine(it)) }
        if (report.divergences.isNotEmpty()) {
            appendLine()
            appendLine("── Note CI / divergenze ──")
            report.divergences.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("Generato da AccessScope Maestro Beta")
    }.trimEnd()

    private fun formatStepLine(step: PlayStepResult): String {
        val num = (step.index + 1).toString().padStart(3, ' ')
        val badge = when (step.status) {
            PlayStepStatus.PASSED -> "OK "
            PlayStepStatus.FAILED -> "KO "
            PlayStepStatus.SKIPPED_OPTIONAL -> "OPT"
            PlayStepStatus.NOT_RUN -> "—  "
        }
        val data = step.dataUsed?.let { " | dati: $it" }.orEmpty()
        val err = step.error?.let { " | $it" }.orEmpty()
        val note = step.note?.let { " | nota: $it" }.orEmpty()
        return "$num [$badge] ${step.summary}$data$err$note"
    }
}
