/**
 * Esporta un report Markdown di affidabilità scansione per debug e benchmark interni.
 *
 * Il file viene salvato in Download insieme al PDF e aiuta a valutare falsi positivi,
 * confidenza delle misure e copertura per sessione.
 */
package dev.accessscope.scanner.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.report.SessionComparison
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Genera report `.md` dettagliato per analisi anti-allucinazione post-scansione.
 */
class ScanReliabilityReportExporter(private val context: Context) {

  /**
   * @return Percorso del file salvato o errore.
   */
  fun export(
    targetPackages: Set<String>,
    violations: List<AccessibilityViolation>,
    screenReaderFindings: List<ScreenReaderFinding>,
    uniqueScreens: Int,
    scanAnalyses: Int,
    scanScopeLabel: String,
    scannedScreens: List<String>,
    checkSummaries: List<CheckAreaSummary>,
    sessionComparison: SessionComparison? = null,
    appVersion: String = "",
  ): Result<String> = runCatching {
    val filtered = ReportHelper.filterViolations(violations)
    val markdown = buildMarkdown(
      targetPackages = targetPackages,
      allViolations = violations,
      filtered = filtered,
      screenReaderFindings = screenReaderFindings,
      uniqueScreens = uniqueScreens,
      scanAnalyses = scanAnalyses,
      scanScopeLabel = scanScopeLabel,
      scannedScreens = scannedScreens,
      checkSummaries = checkSummaries,
      sessionComparison = sessionComparison,
      appVersion = appVersion,
    )
    saveMarkdown(markdown, fileName())
  }

  private fun buildMarkdown(
    targetPackages: Set<String>,
    allViolations: List<AccessibilityViolation>,
    filtered: List<AccessibilityViolation>,
    screenReaderFindings: List<ScreenReaderFinding>,
    uniqueScreens: Int,
    scanAnalyses: Int,
    scanScopeLabel: String,
    scannedScreens: List<String>,
    checkSummaries: List<CheckAreaSummary>,
    sessionComparison: SessionComparison?,
    appVersion: String,
  ): String = buildString {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALY).format(Date())
  appendLine("# AccessScope — Report affidabilità scansione")
    appendLine()
    appendLine("> File generato automaticamente per debug interno. Usare per confrontare sessioni,")
    appendLine("> individuare possibili allucinazioni e misurare miglioramenti tra versioni.")
    appendLine()
    appendLine("## Metadati sessione")
    appendLine()
    appendLine("| Campo | Valore |")
    appendLine("|-------|--------|")
    appendLine("| Data | $date |")
    appendLine("| App AccessScope | $appVersion |")
    appendLine("| Package target | ${targetPackages.joinToString(", ")} |")
    appendLine("| Ambito | $scanScopeLabel |")
    appendLine("| Schermate uniche | $uniqueScreens |")
    appendLine("| Analisi eseguite | $scanAnalyses |")
    appendLine("| Violazioni raw | ${allViolations.size} |")
    appendLine("| Violazioni report (filtrate) | ${filtered.size} |")
    appendLine("| TalkBack findings | ${screenReaderFindings.size} |")
    appendLine("| Check OK registrati | ${ReportHelper.totalPassedChecks(checkSummaries)} |")
    appendLine()

    sessionComparison?.let { cmp ->
      appendLine("## Confronto sessione precedente")
      appendLine()
      appendLine("| Metrica | Valore |")
      appendLine("|---------|--------|")
      appendLine("| Nuove violazioni | ${cmp.newCount} |")
      appendLine("| Risolte | ${cmp.resolvedCount} |")
      appendLine("| Invariate | ${cmp.unchangedCount} |")
      appendLine("| Δ score | ${cmp.scoreDelta} |")
      appendLine()
    }

    appendLine("## Riepilogo per ambito")
    appendLine()
    ViolationArea.entries.forEach { area ->
      val count = filtered.count { it.area == area }
      val passes = checkSummaries.count { it.area == area }
      appendLine("- **${area.emoji} ${area.title}**: $count problemi, $passes check OK")
    }
    appendLine()

    appendLine("## Schermate visitate")
    appendLine()
    if (scannedScreens.isEmpty()) {
      appendLine("_Nessuna schermata registrata — possibile problema di scansione o sessione vuota._")
    } else {
      scannedScreens.forEachIndexed { i, title -> appendLine("${i + 1}. $title") }
    }
    appendLine()

    appendLine("## Segnali di possibile allucinazione")
    appendLine()
    val suspects = buildSuspicionList(filtered)
    if (suspects.isEmpty()) {
      appendLine("_Nessun pattern sospetto automatico su violazioni filtrate._")
    } else {
      appendLine("| viewId | Tipo | Conf. | Motivo sospetto | Schermata |")
      appendLine("|--------|------|-------|-----------------|-----------|")
      suspects.forEach { s ->
        appendLine("| `${s.viewId}` | ${s.type} | ${"%.0f".format(s.confidence * 100)}% | ${s.reason} | ${s.screen} |")
      }
    }
    appendLine()

    appendLine("## Violazioni a bassa confidenza (<80%)")
    appendLine()
    val lowConf = filtered.filter { it.confidence < 0.80f }.sortedBy { it.confidence }
    if (lowConf.isEmpty()) {
      appendLine("_Nessuna._")
    } else {
      lowConf.forEach { v -> appendViolationDetail(v) }
    }
    appendLine()

    appendLine("## Tutte le violazioni nel report")
    appendLine()
    filtered.groupBy { it.screenTitle }.forEach { (screen, items) ->
      appendLine("### $screen (${items.size})")
      appendLine()
      items.sortedByDescending { it.confidence }.forEach { v -> appendViolationDetail(v) }
      appendLine()
    }

    appendLine("## Riferimento benchmark Nexi (mps-accessibility-verification)")
    appendLine()
    appendLine("ID noti come **falso positivo** nel benchmark manuale — se compaiono, regression:")
    appendLine()
    KNOWN_FALSE_POSITIVE_IDS.forEach { id -> appendLine("- `$id`") }
    appendLine()
    appendLine("ID **confermati** — devono restare se presenti nel codice sorgente:")
    appendLine()
    KNOWN_TRUE_POSITIVE_IDS.forEach { id -> appendLine("- `$id`") }
    appendLine()
    appendLine("### Presenza nel report corrente")
    appendLine()
    appendLine("| ID | Atteso benchmark | Presente in report |")
    appendLine("|----|------------------|-------------------|")
    (KNOWN_FALSE_POSITIVE_IDS + KNOWN_TRUE_POSITIVE_IDS).distinct().forEach { id ->
      val expected = if (id in KNOWN_FALSE_POSITIVE_IDS) "FP (non segnalare)" else "TP (segnalare)"
      val present = filtered.any { viewIdShort(it.viewId) == id }
      appendLine("| `$id` | $expected | ${if (present) "⚠️ SÌ" else "no"} |")
    }
    appendLine()
    appendLine("---")
    appendLine("*Generato da AccessScope ScanReliabilityReportExporter*")
  }

  private fun StringBuilder.appendViolationDetail(v: AccessibilityViolation) {
    val id = viewIdShort(v.viewId) ?: "—"
    appendLine("- **${v.type.displayName}** (`$id`) — conf ${(v.confidence * 100).toInt()}%")
    appendLine("  - Schermata: ${v.screenTitle}")
    v.elementLabel?.let { appendLine("  - Label: $it") }
    v.measuredValue?.let { appendLine("  - Misurato: $it (richiesto ${v.requiredValue ?: "—"})") }
    appendLine("  - Dettaglio: ${v.details}")
    v.bounds?.let { appendLine("  - Bounds: $it") }
  }

  private fun buildSuspicionList(violations: List<AccessibilityViolation>): List<Suspicion> {
    val out = mutableListOf<Suspicion>()
    violations.forEach { v ->
      val id = viewIdShort(v.viewId) ?: return@forEach
      val reason = when {
        id in KNOWN_FALSE_POSITIVE_IDS ->
          "ID classificato FP nel benchmark MPS/BFF"
        v.type in setOf(ViolationType.LOW_COLOR_CONTRAST, ViolationType.LOW_NON_TEXT_CONTRAST) &&
          v.confidence < 0.78f ->
          "Contrasto screenshot con confidenza borderline"
        v.type == ViolationType.OVERLAPPING_TOUCH_TARGETS &&
          id in setOf("content", "scrollview_port", "container", "tv_tab") ->
          "Overlap strutturale su container/tab"
        v.type == ViolationType.OVERLAPPING_TOUCH_TARGETS &&
          v.details.contains("px²") && v.details.substringBefore("px").filter { it.isDigit() }
            .toIntOrNull()?.let { it > 100_000 } == true ->
          "Overlap area molto grande (probabile gerarchia layout)"
        else -> null
      }
      if (reason != null) {
        out += Suspicion(id, v.type.displayName, v.confidence, reason, v.screenTitle)
      }
    }
    return out.distinctBy { "${it.viewId}:${it.type}" }
  }

  private fun viewIdShort(viewId: String?): String? =
    viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

  private fun saveMarkdown(content: String, fileName: String): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      }
      val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("Impossibile creare file in Download")
      context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        ?: error("Impossibile scrivere file")
      return "Download/$fileName"
    }
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, fileName)
    file.writeText(content, Charsets.UTF_8)
    return file.absolutePath
  }

  private fun fileName() =
    "AccessScope_Reliability_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.md"

  private data class Suspicion(
    val viewId: String,
    val type: String,
    val confidence: Float,
    val reason: String,
    val screen: String,
  )

  companion object {
    val KNOWN_FALSE_POSITIVE_IDS = setOf(
      "currency", "tv_title_second_section", "amount_uscite_effects", "new_payment",
      "currency_paym", "causale", "import_positive", "currency_incom", "edt_ragione_sociale",
      "content", "tv_tab",
    )

    val KNOWN_TRUE_POSITIVE_IDS = setOf(
      "last_30", "rubrica_label", "vop_info", "select_accounts",
      "tv_see_account_movements", "see_all_insolved", "tv_custom", "tv_incassi",
      "topbar", "bonifico_online",
    )
  }
}
