/**
 * Genera un prompt strutturato per assistenti AI a partire dai risultati di scansione.
 *
 * Il testo è ottimizzato per ChatGPT, Claude, Gemini e strumenti simili: contesto Android,
 * riferimenti WCAG, elenco problemi prioritizzati e istruzioni di output attese.
 */
package dev.accessscope.scanner.report

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationSeverity

/**
 * Dati di input per la costruzione del prompt AI.
 *
 * @property violations Violazioni filtrate da includere nel prompt.
 * @property screenReaderFindings Note dalla simulazione TalkBack.
 * @property targetPackageNames Package Android analizzati.
 * @property packageLabels Mappa package → nome visualizzato app.
 * @property uniqueScreens Numero di schermate uniche visitate.
 * @property scanScopeLabel Etichetta ambito scansione (es. «Completa»).
 */
data class AiPromptInput(
    val violations: List<AccessibilityViolation>,
    val screenReaderFindings: List<ScreenReaderFinding>,
    val targetPackageNames: Set<String>,
    val packageLabels: Map<String, String>,
    val uniqueScreens: Int,
    val scanScopeLabel: String,
)

/**
 * Costruisce prompt in italiano per correggere i problemi di accessibilità rilevati.
 */
object AiPromptBuilder {

    private const val MAX_VIOLATIONS = 45
    private const val MAX_TALKBACK = 20

    /**
     * Crea il prompt completo pronto per essere incollato in un assistente AI.
     *
     * @param input Dati della sessione di scansione AccessScope.
     * @return Testo markdown strutturato.
     */
    fun build(input: AiPromptInput): String {
        val violations = ReportHelper.filterViolations(input.violations)
        val sorted = ReportHelper.sortBySeverity(violations).take(MAX_VIOLATIONS)
        val talkBack = input.screenReaderFindings.take(MAX_TALKBACK)
        val score = ReportHelper.computeScore(violations, input.uniqueScreens.coerceAtLeast(1))
        val appsLine = formatApps(input.targetPackageNames, input.packageLabels)

        return buildString {
            appendLine("# Correzione accessibilità Android — prompt AccessScope")
            appendLine()
            appendLine("Sei un esperto di accessibilità mobile Android (WCAG 2.2, Material Design, TalkBack).")
            appendLine("Analizza i problemi sotto e proponi **fix concreti** per un'app Android nativa (View/XML e/o Jetpack Compose).")
            appendLine()
            appendLine("## Contesto scansione")
            appendLine("- **Tool:** AccessScope (scanner WCAG in tempo reale)")
            appendLine("- **App analizzate:** $appsLine")
            appendLine("- **Schermate uniche visitate:** ${input.uniqueScreens}")
            appendLine("- **Ambito controlli:** ${input.scanScopeLabel}")
            appendLine("- **Punteggio stimato:** $score/100 (${ReportHelper.scoreLabel(score)})")
            appendLine("- **Violazioni rilevate:** ${violations.size} (nel prompt: ${sorted.size})")
            appendLine("- **Note TalkBack:** ${input.screenReaderFindings.size}")
            appendLine()
            appendLine("## Cosa devi produrre")
            appendLine("1. **Priorità** — ordina gli interventi (critici → minori) con stima effort (S/M/L).")
            appendLine("2. **Fix per problema** — per ciascuno indica:")
            appendLine("   - File/vista probabile (`viewId`, classe, schermata)")
            appendLine("   - Causa radice")
            appendLine("   - Patch suggerita (XML attributi `contentDescription`, `importantForAccessibility`, contrasto colori, `minHeight`/`minWidth`, heading, ecc. oppure equivalente Compose)")
            appendLine("   - Riferimento WCAG")
            appendLine("   - Come verificare con TalkBack")
            appendLine("3. **Pattern riutilizzabili** — se più problemi condividono la stessa causa, proponi una regola/componente condiviso.")
            appendLine("4. **Checklist finale** — 5 passi di verifica manuale post-fix.")
            appendLine()
            appendLine("## Vincoli")
            appendLine("- Mantieni le stringhe UI in **italiano**.")
            appendLine("- Non rimuovere funzionalità; migliora solo l'accessibilità.")
            appendLine("- Preferisci fix nativi Android (`contentDescription`, semantica, contrasto, touch target ≥ 48dp) rispetto a workaround fragili.")
            appendLine("- Se manca il sorgente, indica comunque la modifica attesa e chiedi il file layout/Composable pertinente.")
            appendLine()

            if (sorted.isNotEmpty()) {
                appendLine("## Problemi rilevati (per schermata)")
                appendLine()
                ReportHelper.groupViolationsBySection(sorted).forEach { (section, items) ->
                    appendLine("### Schermata: ${section.screenTitle}")
                    if (section.hasSubsection) {
                        appendLine("Sezione: ${section.sectionTitle}")
                    }
                    items.forEach { v ->
                        appendViolation(v)
                    }
                    appendLine()
                }
                if (violations.size > sorted.size) {
                    appendLine("_… e altri ${violations.size - sorted.size} problemi omessi per lunghezza. Chiedi l'export PDF completo se necessario._")
                    appendLine()
                }
            }

            if (talkBack.isNotEmpty()) {
                appendLine("## Simulazione TalkBack")
                appendLine()
                talkBack.forEach { f ->
                    appendLine("- **${f.screenTitle}** — ${f.issue}")
                    f.viewId?.let { appendLine("  - viewId: `${shortId(it)}`") }
                    f.announcedText?.takeIf { it.isNotBlank() }?.let {
                        appendLine("  - Annuncio attuale: «$it»")
                    }
                    appendLine("  - Classe: `${f.nodeClassName.substringAfterLast('.')}`")
                }
                appendLine()
            }

            if (sorted.isEmpty() && talkBack.isEmpty()) {
                appendLine("## Nessun problema strutturato")
                appendLine("La scansione non ha prodotto violazioni dettagliate. Suggerisci un piano di audit manuale WCAG per app Android banking/enterprise.")
                appendLine()
            }

            appendLine("## Formato risposta")
            appendLine("Usa markdown con sezioni chiare. Inizia con un riepilogo esecutivo (3 bullet), poi i fix dettagliati.")
        }.trim()
    }

    private fun StringBuilder.appendViolation(v: AccessibilityViolation) {
        val severity = severityLabel(v.type.severity)
        appendLine("- **[${severity}] ${v.type.displayName}** (${v.type.wcagRef})")
        appendLine("  - Schermata: ${v.screenTitle}")
        v.viewId?.let { appendLine("  - viewId: `${shortId(it)}`") }
        appendLine("  - Classe: `${v.viewClassName.substringAfterLast('.')}`")
        v.elementLabel?.takeIf { it.isNotBlank() }?.let { appendLine("  - Etichetta/testo: «$it»") }
        v.bounds?.let { appendLine("  - Bounds: $it") }
        if (v.measuredValue != null || v.requiredValue != null) {
            appendLine("  - Misura: ${v.measuredValue ?: "—"} (richiesto: ${v.requiredValue ?: "—"})")
        }
        appendLine("  - Dettaglio: ${v.details}")
        v.remediation?.takeIf { it.isNotBlank() }?.let { appendLine("  - Suggerimento scanner: $it") }
        appendLine("  - Spiegazione: ${v.simpleExplanation}")
    }

    private fun formatApps(packages: Set<String>, labels: Map<String, String>): String =
        if (packages.isEmpty()) {
            "non specificato"
        } else {
            packages.joinToString { pkg ->
                labels[pkg]?.let { "$it (`$pkg`)" } ?: "`$pkg`"
            }
        }

    private fun shortId(viewId: String): String = viewId.substringAfterLast('/')

    private fun severityLabel(severity: ViolationSeverity): String = when (severity) {
        ViolationSeverity.CRITICAL -> "CRITICO"
        ViolationSeverity.SERIOUS -> "GRAVE"
        ViolationSeverity.MODERATE -> "MODERATO"
        ViolationSeverity.MINOR -> "MINORE"
    }
}
