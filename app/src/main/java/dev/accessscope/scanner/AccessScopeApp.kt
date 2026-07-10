/**
 * Punto di ingresso dell'applicazione AccessScope.
 *
 * Inizializza il repository delle sessioni di scansione, ripristina eventuali scansioni
 * interrotte e coordina l'arresto della scansione con la generazione del report PDF.
 */
package dev.accessscope.scanner

import android.app.Application
import android.content.Intent
import android.util.Log
import dev.accessscope.scanner.bridge.ACTION_SCAN_COMPLETE
import dev.accessscope.scanner.bridge.BRIDGE_LOG_TAG
import dev.accessscope.scanner.bridge.EXTRA_PACKAGE_NAME
import dev.accessscope.scanner.bridge.EXTRA_SESSION_ID
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.export.PdfReportExporter
import dev.accessscope.scanner.export.ScanReliabilityReportExporter
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.FavoriteAppsStore
import dev.accessscope.scanner.util.ScanHistoryStore
import dev.accessscope.scanner.util.ScanEvidenceStore
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

    /** Cache JPEG screenshot ed evidenze annotate per sessione di scansione. */
    val scanEvidenceStore: ScanEvidenceStore by lazy { ScanEvidenceStore(this) }

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
        AppFileLogger.init(this)
        installCrashHandler()
        scanRepository = ScanSessionRepository(this)
        scanRepository.stopCallback = { stopScanSession(fromOverlay = true) }

        if (scanRepository.restorePersistedScan()) {
            ScanOverlayService.start(this)
        }

        AppFileLogger.info("AccessScopeApp", "started pid=${android.os.Process.myPid()} restoredScan=${scanRepository.state.value.isScanning}")
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val snapshot = if (::scanRepository.isInitialized) scanRepository.state.value else null
            AppFileLogger.error(
                "Crash",
                buildString {
                    append("Uncaught on thread=${thread.name}")
                    snapshot?.let {
                        append(" scanning=${it.isScanning}")
                        append(" violations=${it.violations.size}")
                        append(" screens=${it.uniqueScreens}")
                        append(" packages=${it.selectedPackages.joinToString()}")
                    }
                },
                throwable,
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
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

        AppFileLogger.info(
            "AccessScopeApp",
            "stopScan fromOverlay=$fromOverlay scanning=${snapshot.isScanning} violations=${snapshot.violations.size}",
        )

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
                sessionId = snapshot.sessionId,
            )
            lastArchivedSessionId = archived.id
            scanHistoryStore.archive(archived)
            notifyScanComplete(archived.id, archived.targetPackages.firstOrNull())
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
                    AppFileLogger.info("AccessScopeApp", "pdf_ok path=$path")
                },
                onFailure = { error ->
                    scanRepository.setError(error.message ?: "Errore export PDF")
                    AppFileLogger.error("AccessScopeApp", "pdf_fail ${error.message}")
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
                        AppFileLogger.info("AccessScopeApp", "reliability_md_ok path=$mdPath")
                    },
                    onFailure = { error ->
                        AppFileLogger.error("AccessScopeApp", "reliability_md_fail ${error.message}")
                    },
                )
            }

            withContext(Dispatchers.IO) {
                scanEvidenceStore.cleanupExcept(scanHistoryStore.allSessionIds())
            }
        }
    }

    private fun notifyScanComplete(sessionId: String, packageName: String?) {
        sendBroadcast(Intent(ACTION_SCAN_COMPLETE).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            setPackage(this@AccessScopeApp.packageName)
        })
        Log.i(
            BRIDGE_LOG_TAG,
            "scan_complete sessionId=$sessionId package=${packageName.orEmpty()}",
        )
    }
}
