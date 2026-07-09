/**
 * Punto di ingresso dell'applicazione AccessScope.
 *
 * Inizializza il repository delle sessioni di scansione, ripristina eventuali scansioni
 * interrotte e coordina l'arresto della scansione con la generazione del report PDF.
 */
package dev.accessscope.scanner

import android.app.Application
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.export.PdfReportExporter
import dev.accessscope.scanner.export.ScanReliabilityReportExporter
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.DebugTrace
import dev.accessscope.scanner.util.FavoriteAppsStore
import dev.accessscope.scanner.util.ScanHistoryStore
import dev.accessscope.scanner.util.ScanSettingsStore
import dev.accessscope.scanner.util.ThemePreferencesStore
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.report.SessionComparisonHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Classe [Application] principale di AccessScope.
 *
 * Mantiene lo stato globale della sessione di scansione, le preferenze utente
 * e orchestra l'export del report PDF al termine di una scansione.
 */
class AccessScopeApp : Application() {

    /** Repository condiviso che gestisce lo stato corrente della sessione di scansione. */
    lateinit var scanRepository: ScanSessionRepository
        private set

    /** Store persistente per le app contrassegnate come preferite dall'utente. */
    val favoriteAppsStore: FavoriteAppsStore by lazy { FavoriteAppsStore(this) }

    /** Store persistente per le impostazioni di scansione (ambito, pacchetti selezionati, ecc.). */
    val scanSettingsStore: ScanSettingsStore by lazy { ScanSettingsStore(this) }

    /** Store persistente per la preferenza tema (chiaro / scuro / sistema). */
    val themePreferencesStore: ThemePreferencesStore by lazy { ThemePreferencesStore(this) }

    /** Store file-based per la cronologia delle sessioni di scansione (max 20 per app). */
    val scanHistoryStore: ScanHistoryStore by lazy { ScanHistoryStore(this) }

    private val pdfExporter by lazy { PdfReportExporter(this) }
    private val reliabilityExporter by lazy { ScanReliabilityReportExporter(this) }
    private var lastArchivedSessionId: String? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Inizializza l'applicazione: crea il repository, registra i callback
     * e ripristina una scansione eventualmente persistita.
     */
    override fun onCreate() {
        super.onCreate()
        scanRepository = ScanSessionRepository(this)
        scanRepository.stopCallback = { stopScanSession(fromOverlay = true) }

        if (scanRepository.restorePersistedScan()) {
            ScanOverlayService.start(this)
        }

        // #region agent log
        DebugTrace.log("H1", "AccessScopeApp.onCreate", "app_started", mapOf(
            "pid" to android.os.Process.myPid(),
            "restoredScan" to scanRepository.state.value.isScanning,
        ))
        // #endregion
    }

    /**
     * Arresta la sessione di scansione corrente, chiude l'overlay e genera il report PDF
     * se era in corso una sessione con dati raccolti.
     *
     * @param fromOverlay `true` se l'arresto è stato richiesto dall'overlay flottante
     *                    o dalla notifica; `false` se avviato dall'interfaccia principale.
     */
    fun stopScanSession(fromOverlay: Boolean = false) {
        // Sempre chiudi overlay — anche se lo stato in memoria è perso dopo restart processo.
        ScanOverlayService.stop(this)

        val snapshot = scanRepository.state.value
        val hadActiveSession = snapshot.isScanning ||
            snapshot.violations.isNotEmpty() ||
            snapshot.uniqueScreens > 0

        // #region agent log
        DebugTrace.log("H-STOP2", "AccessScopeApp.stopScanSession", "called", mapOf(
            "fromOverlay" to fromOverlay,
            "isScanning" to snapshot.isScanning,
            "violations" to snapshot.violations.size,
            "screens" to snapshot.uniqueScreens,
            "analyses" to snapshot.scanAnalyses,
            "hadActiveSession" to hadActiveSession,
        ))
        // #endregion

        if (hadActiveSession) {
            val filtered = ReportHelper.filterViolations(snapshot.violations)
            val score = ReportHelper.computeScore(filtered, snapshot.uniqueScreens.coerceAtLeast(1))
            val archived = scanHistoryStore.buildArchivedSession(
                targetPackages = snapshot.selectedPackages,
                violations = filtered,
                screenReaderFindings = snapshot.screenReaderFindings,
                uniqueScreens = snapshot.uniqueScreens,
                scanAnalyses = snapshot.scanAnalyses,
                scanScopeLabel = snapshot.scanScope.label(),
                score = score,
            )
            lastArchivedSessionId = archived.id
            scanHistoryStore.archive(archived)
        }

        scanRepository.stopScan()

        if (!hadActiveSession) return

        applicationScope.launch {
            val current = scanRepository.state.value
            val comparison = current.selectedPackages.firstOrNull()?.let { pkg ->
                SessionComparisonHelper.compareLatestWithPrevious(
                    scanHistoryStore.getLatest(pkg),
                    scanHistoryStore.getPrevious(pkg),
                )
            }
            val appVersion = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull().orEmpty()

            val pdfResult = withContext(Dispatchers.IO) {
                pdfExporter.export(
                    targetPackages = current.selectedPackages,
                    violations = current.violations,
                    screenReaderFindings = current.screenReaderFindings,
                    uniqueScreens = current.uniqueScreens,
                    scanAnalyses = current.scanAnalyses,
                    scanScopeLabel = current.scanScope.label(),
                    scannedScreens = current.visitedScreenTitles,
                    checkSummaries = current.checkSummaries,
                )
            }
            pdfResult.fold(
                onSuccess = { path ->
                    scanRepository.setPdfPath(path)
                    lastArchivedSessionId?.let { scanHistoryStore.updateSessionPdfPath(it, path) }
                    DebugTrace.log("H-STOP2", "stopScanSession", "pdf_ok", mapOf("path" to path))
                },
                onFailure = { error ->
                    scanRepository.setError(error.message ?: "Errore export PDF")
                    DebugTrace.log("H-STOP2", "stopScanSession", "pdf_fail", mapOf(
                        "error" to (error.message ?: "unknown"),
                    ))
                },
            )

            if (scanSettingsStore.reliabilityReportEnabled) {
                val mdResult = withContext(Dispatchers.IO) {
                    reliabilityExporter.export(
                        targetPackages = current.selectedPackages,
                        violations = current.violations,
                        screenReaderFindings = current.screenReaderFindings,
                        uniqueScreens = current.uniqueScreens,
                        scanAnalyses = current.scanAnalyses,
                        scanScopeLabel = current.scanScope.label(),
                        scannedScreens = current.visitedScreenTitles,
                        checkSummaries = current.checkSummaries,
                        sessionComparison = comparison,
                        appVersion = appVersion,
                    )
                }
                mdResult.fold(
                    onSuccess = { mdPath ->
                        scanRepository.setReliabilityMdPath(mdPath)
                        DebugTrace.log("H-STOP2", "stopScanSession", "reliability_md_ok", mapOf("path" to mdPath))
                    },
                    onFailure = { error ->
                        DebugTrace.log("H-STOP2", "stopScanSession", "reliability_md_fail", mapOf(
                            "error" to (error.message ?: "unknown"),
                        ))
                    },
                )
            }
        }
    }
}
