/**
 * Esportazione del report di accessibilità in formato PDF.
 *
 * Genera un documento multipagina con copertina, panoramica, copertura controlli,
 * dettaglio per schermata e glossario, salvandolo nella cartella Download.
 */
package dev.accessscope.scanner.export.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.CheckAreaSummary
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.report.ReportHelper
import java.io.File
import java.io.FileOutputStream

/**
 * Esportatore che trasforma i risultati di una sessione di scansione
 * in un report PDF leggibile e condivisibile.
 *
 * @property context Contesto Android usato per l'accesso a MediaStore e alle risorse di sistema.
 */
class PdfReportExporter(private val context: Context) {

    /**
     * Genera e salva il report PDF con tutti i dati della sessione di scansione.
     *
     * @param targetPackages Insieme dei pacchetti delle app analizzate.
     * @param violations Elenco completo delle violazioni rilevate.
     * @param screenReaderFindings Risultati della simulazione TalkBack.
     * @param uniqueScreens Numero di schermate uniche visitate.
     * @param scanAnalyses Numero totale di analisi eseguite (default: uguale a [uniqueScreens]).
     * @param scanScopeLabel Etichetta testuale dell'ambito di scansione (es. "Completa").
     * @param scannedScreens Titoli delle schermate visitate, in ordine di navigazione.
     * @param checkSummaries Riepiloghi dei controlli superati per area e schermata.
     * @return [Result] con il percorso del file salvato in caso di successo, o l'eccezione in caso di errore.
     */
    fun export(
        targetPackages: Set<String>,
        violations: List<AccessibilityViolation>,
        screenReaderFindings: List<ScreenReaderFinding>,
        uniqueScreens: Int,
        scanAnalyses: Int = uniqueScreens,
        scanScopeLabel: String = "Completa",
        scannedScreens: List<String> = emptyList(),
        checkSummaries: List<CheckAreaSummary> = emptyList(),
    ): Result<String> = runCatching {
        val filtered = ReportHelper.filterViolations(violations)
        val document = PdfDocument()
        val ctx = PdfContext(document)
        val passedTotal = ReportHelper.totalPassedChecks(checkSummaries)

        PdfCoverRenderer.drawCover(ctx, targetPackages, uniqueScreens, scanAnalyses, scanScopeLabel, filtered, screenReaderFindings.size, passedTotal)
        PdfAuxiliarySections.drawSummary(ctx, filtered, screenReaderFindings, scannedScreens)
        PdfAuxiliarySections.drawExecutiveSummary(ctx, filtered)
        PdfAuxiliarySections.drawCheckCoverage(ctx, checkSummaries, filtered)
        PdfAuxiliarySections.drawHowToRead(ctx)
        PdfAuxiliarySections.drawSeveritySections(ctx, filtered, screenReaderFindings, checkSummaries)

        PdfAuxiliarySections.drawGlossary(ctx)
        ctx.finish()
        savePdf(context, document, ctx.fileName())
    }
}

/**
 * Scrive il documento PDF su disco tramite MediaStore (API 29+) o file system legacy.
 *
 * @param context Contesto Android per l'accesso a MediaStore.
 * @param document Documento PDF completato da salvare.
 * @param fileName Nome file desiderato (es. `AccessScope_20260101_120000.pdf`).
 * @return Percorso relativo o assoluto del file salvato.
 */
private fun savePdf(context: Context, document: PdfDocument, fileName: String): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Impossibile creare il PDF.")
        resolver.openOutputStream(uri)?.use { document.writeTo(it) }
            ?: error("Impossibile scrivere il PDF.")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        document.close()
        "Download/$fileName"
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        file.absolutePath
    }
}
