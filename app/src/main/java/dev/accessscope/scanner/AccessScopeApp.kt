package dev.accessscope.scanner

import android.app.Application
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.export.PdfReportExporter
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.DebugTrace
import dev.accessscope.scanner.util.FavoriteAppsStore
import dev.accessscope.scanner.util.ScanSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessScopeApp : Application() {

    lateinit var scanRepository: ScanSessionRepository
        private set
    val favoriteAppsStore: FavoriteAppsStore by lazy { FavoriteAppsStore(this) }
    val scanSettingsStore: ScanSettingsStore by lazy { ScanSettingsStore(this) }
    private val pdfExporter by lazy { PdfReportExporter(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

        scanRepository.stopScan()

        if (!hadActiveSession) return

        applicationScope.launch {
            val current = scanRepository.state.value
            val result = withContext(Dispatchers.IO) {
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
            result.fold(
                onSuccess = { path ->
                    scanRepository.setPdfPath(path)
                    DebugTrace.log("H-STOP2", "stopScanSession", "pdf_ok", mapOf("path" to path))
                },
                onFailure = { error ->
                    scanRepository.setError(error.message ?: "Errore export PDF")
                    DebugTrace.log("H-STOP2", "stopScanSession", "pdf_fail", mapOf(
                        "error" to (error.message ?: "unknown"),
                    ))
                },
            )
        }
    }
}
