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
import dev.accessscope.scanner.export.pdf.PdfReportExporter
import dev.accessscope.scanner.export.ScanReliabilityReportExporter
import dev.accessscope.scanner.recorder.CredentialVault
import dev.accessscope.scanner.recorder.FlowOptimizer
import dev.accessscope.scanner.recorder.FlowPlaybackController
import dev.accessscope.scanner.recorder.FlowPlayer
import dev.accessscope.scanner.recorder.FlowStore
import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.RecordingSessionController
import dev.accessscope.scanner.recorder.SavedFlow
import dev.accessscope.scanner.recorder.SelectorChainHealer
import dev.accessscope.scanner.recorder.SelectorFailRateStore
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.model.PlayOutcome
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.service.PlaybackOverlayService
import dev.accessscope.scanner.service.RecordingOverlayService
import dev.accessscope.scanner.service.ScanOverlayService
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.DebugSessionLog
import dev.accessscope.scanner.recorder.ScanRecorderMutexPolicy
import dev.accessscope.scanner.recorder.intelligence.ScanIntelligenceProvider
import dev.accessscope.scanner.recorder.MaestroSaveProgress
import dev.accessscope.scanner.recorder.review.GeminiFlashFlowReviewer
import dev.accessscope.scanner.recorder.review.MaestroReviewSettingsStore
import dev.accessscope.scanner.util.PermissionHelper
import dev.accessscope.scanner.util.FavoriteAppsStore
import dev.accessscope.scanner.util.ScanHistoryStore
import dev.accessscope.scanner.util.ScanEvidenceStore
import dev.accessscope.scanner.util.ScanSettingsStore
import dev.accessscope.scanner.util.ThemePreferencesStore
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.report.SessionComparisonHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** Persistenza YAML Maestro (Beta). */
    val flowStore: FlowStore by lazy { FlowStore(this) }
    val credentialVault: CredentialVault by lazy { CredentialVault(this) }
    val selectorFailRateStore: SelectorFailRateStore by lazy { SelectorFailRateStore(this) }

    /** Impostazioni revisione AI Maestro (API key Gemini). */
    val maestroReviewSettingsStore: MaestroReviewSettingsStore by lazy { MaestroReviewSettingsStore(this) }

    /** Client revisione YAML con Gemini Flash. */
    val geminiFlowReviewer: GeminiFlashFlowReviewer by lazy {
        GeminiFlashFlowReviewer(maestroReviewSettingsStore)
    }

    /** Sessione di registrazione azioni utente → YAML Maestro (Beta). */
    val recordingController = RecordingSessionController(this)

    private val _maestroSaveProgress = MutableStateFlow(MaestroSaveProgress())
    /** Progresso save/review visibile in [dev.accessscope.scanner.ui.screen.FlowsScreen]. */
    val maestroSaveProgress: StateFlow<MaestroSaveProgress> = _maestroSaveProgress.asStateFlow()

    /** Playback in-app flussi Maestro (Beta). */
    val playbackController = FlowPlaybackController()

    private val pdfExporter by lazy { PdfReportExporter(this) }
    private val reliabilityExporter by lazy { ScanReliabilityReportExporter(this) }
    private var lastArchivedSessionId: String? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackJob: Job? = null

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
            val filtered = ReportHelper.filterViolations(
                snapshot.violations,
                scanSettingsStore.includeLowConfidenceFindings,
            )
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
                visitedScreens = snapshot.visitedScreens,
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
                    includeLowConfidence = scanSettingsStore.includeLowConfidenceFindings,
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

    /**
     * Avvia registrazione Maestro (Beta). Mutex con lo scan WCAG.
     *
     * @return Messaggio errore o `null` se ok.
     */
    fun startRecordingSession(targetPackage: String, targetLabel: String): String? {
        if (!ScanRecorderMutexPolicy.canStartRecording(scanRepository.state.value.isScanning)) {
            return "Ferma prima la scansione accessibilità"
        }
        if (recordingController.isRecording) {
            return "Registrazione già in corso"
        }
        if (!PermissionHelper.isAccessibilityServiceReady(
                this,
                AccessScopeAccessibilityService::class.java,
            )
        ) {
            if (!PermissionHelper.isAccessibilityServiceEnabled(
                    this,
                    AccessScopeAccessibilityService::class.java,
                )
            ) {
                return "Abilita il servizio di accessibilità AccessScope nelle impostazioni."
            }
            // Dopo install/force-stop il toggle può restare ON senza service bound → zero eventi.
            PermissionHelper.safeStartSettingsIntent(
                this,
                PermissionHelper.accessibilityServiceIntent(
                    this,
                    AccessScopeAccessibilityService::class.java,
                ),
            )
            return "Accessibilità ON ma servizio non collegato (tipico dopo update APK). " +
                "In Accessibilità: OFF → attendi 2s → ON → Consenti. " +
                "Poi torna in AccessScope e riprova. Se continua, riavvia il telefono."
        }
        if (playbackController.isPlaying) {
            return "Ferma prima il playback del flusso"
        }
        if (!recordingController.start(targetPackage, targetLabel)) {
            return "Impossibile avviare la registrazione"
        }
        RecordingOverlayService.start(this)
        // Porta in foreground l'app target se possibile
        runCatching {
            val launch = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            }
        }
        AppFileLogger.info(
            "AccessScopeApp",
            "recording_start pkg=$targetPackage a11yInstance=${AccessScopeAccessibilityService.instance != null}",
        )
        return null
    }

    /**
     * Termina la registrazione, revisione AI Gemini, salva YAML e chiude l’overlay.
     *
     * @param save Se true salva YAML in [FlowStore].
     * @return [SavedFlow] se salvato, altrimenti null.
     */
    suspend fun stopRecordingSession(save: Boolean = true): SavedFlow? = withContext(Dispatchers.IO) {
        RecordingOverlayService.stop(this@AccessScopeApp)
        if (!recordingController.isRecording && recordingController.state.value.actions.isEmpty()) {
            return@withContext null
        }
        val pkgEarly = recordingController.state.value.targetPackage
        val labelEarly = recordingController.state.value.targetLabel ?: pkgEarly
        val stepPreview = recordingController.realStepCount(recordingController.state.value.actions)
        if (save && stepPreview > 0) {
            _maestroSaveProgress.value = MaestroSaveProgress(
                active = true,
                phase = "Preparazione registrazione…",
                appLabel = labelEarly,
                stepCount = stepPreview,
            )
        }
        val saveSessionStartedMs = if (save && stepPreview > 0) System.currentTimeMillis() else null
        waitForVisualCapturesIdle()
        if (save) {
            _maestroSaveProgress.update {
                it.copy(phase = "Acquisizione screenshot…")
            }
        }
        val stopResult = if (recordingController.isRecording) {
            recordingController.stop()
        } else {
            RecordingSessionController.StopRecordingResult(
                actions = recordingController.state.value.actions,
                telemetry = recordingController.buildTelemetry(),
            )
        }
        val actions = stopResult.actions
        val pkg = recordingController.state.value.targetPackage ?: run {
            clearMaestroSaveProgress()
            return@withContext null
        }
        val label = recordingController.state.value.targetLabel ?: pkg
        if (!save || actions.isEmpty()) {
            recordingController.cancel()
            recordingController.clearVisualBuffer()
            clearMaestroSaveProgress()
            return@withContext null
        }
        val visualContext = recordingController.visualBuffer.toContext()
        try {
            _maestroSaveProgress.update {
                it.copy(
                    active = true,
                    phase = "Ottimizzazione e revisione Gemini Flash…",
                    appLabel = label,
                    stepCount = recordingController.realStepCount(actions),
                )
            }
            val intel = ScanIntelligenceProvider(scanHistoryStore).load(
                pkg,
                scanRepository.state.value.takeIf { it.isScanning },
            )
            val optimizationContext = OptimizationContext(
                appId = pkg,
                telemetry = stopResult.telemetry,
                scanIntel = intel,
            )
            _maestroSaveProgress.update { it.copy(phase = "Salvataggio YAML…") }
            val flow = flowStore.saveFlow(
                name = "Flusso $label",
                appId = pkg,
                appLabel = label,
                actions = actions,
                optimize = true,
                optimizationContext = optimizationContext,
                telemetry = stopResult.telemetry,
                visualContext = visualContext,
                geminiReviewer = geminiFlowReviewer,
                onReviewProgress = { phase ->
                    _maestroSaveProgress.update { it.copy(phase = phase) }
                },
                sessionLogSinceMs = saveSessionStartedMs,
            )
            _maestroSaveProgress.value = MaestroSaveProgress(
                active = false,
                phase = "",
                appLabel = label,
                stepCount = flow.stepCount,
                lastSavedFlowId = flow.id,
            )
            AppFileLogger.info(
                "AccessScopeApp",
                "recording_saved id=${flow.id} steps=${flow.stepCount} aiFallback=${flowStore.lastReviewResult?.usedFallback}",
            )
            flow
        } catch (e: Exception) {
            AppFileLogger.info("AccessScopeApp", "recording_save_failed ${e.message}")
            clearMaestroSaveProgress()
            throw e
        } finally {
            recordingController.clearVisualBuffer()
        }
    }

    private fun clearMaestroSaveProgress() {
        _maestroSaveProgress.value = MaestroSaveProgress()
    }

    private suspend fun waitForVisualCapturesIdle() {
        repeat(80) {
            if (recordingController.isVisualCaptureIdle) return
            delay(100)
        }
    }

    /**
     * Avvia save REC + revisione AI su scope applicazione (overlay / UI).
     */
    fun scheduleStopRecordingSession(
        save: Boolean = true,
        onComplete: (SavedFlow?) -> Unit = {},
    ) {
        applicationScope.launch {
            val flow = stopRecordingSession(save)
            onComplete(flow)
        }
    }

    /**
     * Avvia il playback in-app di un flusso salvato (richiede `actions.json`).
     *
     * @param flow Flusso target.
     * @param withScan Se true, lo scan WCAG può già essere attivo in parallelo.
     * @param clearState Se true, tenta stopApp + clear dati + cold launch prima del flusso.
     * @return Messaggio errore o `null` se avviato.
     */
    fun startFlowPlayback(
        flow: SavedFlow,
        withScan: Boolean = false,
        clearState: Boolean = false,
    ): String? {
        if (recordingController.isRecording) {
            return "Ferma prima la registrazione Maestro"
        }
        if (playbackController.isPlaying) {
            return "Playback già in corso"
        }
        if (AccessScopeAccessibilityService.instance == null) {
            return "Servizio accessibilità non collegato. Riattiva AccessScope in Accessibilità."
        }
        val actions = flowStore.readActions(flow.id)
            ?: return "Flusso senza azioni tipizzate — ri-registra o importa YAML."
        val sanitized = FlowOptimizer.sanitizeForPlay(actions)
        // #region agent log
        DebugSessionLog.log(
            "H7",
            "AccessScopeApp.startFlowPlayback",
            "play_actions",
            mapOf(
                "flowId" to flow.id,
                "rawCount" to actions.size,
                "playCount" to sanitized.size,
                "clearState" to clearState,
                "rawTypes" to actions.joinToString(",") { it::class.simpleName.orEmpty() },
                "playTypes" to sanitized.joinToString(",") { it::class.simpleName.orEmpty() },
            ),
        )
        // #endregion
        if (!playbackController.begin(flow.id, sanitized.size, withScan)) {
            return "Impossibile avviare il playback"
        }
        PlaybackOverlayService.start(this)
        val player = FlowPlayer(
            context = this,
            serviceProvider = { AccessScopeAccessibilityService.instance },
            credentialVault = credentialVault,
        )
        playbackJob?.cancel()
        playbackJob = applicationScope.launch(Dispatchers.Default) {
            val outcome = runCatching {
                player.play(sanitized, clearState = clearState) { index, total ->
                    playbackController.onStep(index, total)
                    PlaybackOverlayService.updateStep(this@AccessScopeApp, index, total)
                }
            }.getOrElse { e ->
                PlayOutcome(error = e.message ?: "Playback fallito")
            }
            if (outcome.error == null && outcome.selectorWins.isNotEmpty()) {
                runCatching { flowStore.applySelectorWins(flow.id, outcome.selectorWins) }
                AppFileLogger.info(
                    "AccessScopeApp",
                    "selector_heal applied=${outcome.selectorWins.size} id=${flow.id}",
                )
            }
            var healMsg: String? = null
            if (outcome.error != null) {
                val stepIdx = SelectorChainHealer.parseStepIndex(outcome.error)
                val failedTap = stepIdx?.let { sanitized.getOrNull(it) as? RecordedAction.Tap }
                if (failedTap != null && stepIdx != null) {
                    val promote = selectorFailRateStore.recordFailure(
                        flow.id,
                        failedTap.viewId,
                        failedTap.text,
                    )
                    if (promote) {
                        val promoted = SelectorChainHealer.applyPromotion(sanitized, stepIdx)
                        if (promoted != null) {
                            flowStore.updateFlow(flow.id, promoted, optimize = false)
                            selectorFailRateStore.clear(flow.id, failedTap.viewId, failedTap.text)
                            healMsg = "Selettore aggiornato (ramo successivo) — riprova Play"
                            AppFileLogger.info(
                                "AccessScopeApp",
                                "fail_rate_promote flow=${flow.id} step=$stepIdx",
                            )
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                // Note su rami "morbidi" che il maestro CLI non ha (segreto non risolto,
                // fallback selettore/coordinate/PIN-pad, wait soft-fail): il playback resta
                // verde (demo sicura), ma un flusso verde qui non garantisce che maestro test
                // verifichi esattamente le stesse cose — vedi PlayOutcome.divergences.
                val ciNote = if (outcome.divergences.isNotEmpty()) " · ${outcome.divergences.size} note CI" else ""
                val endMsg = when {
                    healMsg != null -> "${outcome.error}\n$healMsg"
                    outcome.error != null -> outcome.error
                    outcome.selectorWins.isNotEmpty() ->
                        "Playback completato (${sanitized.size} step, ${outcome.selectorWins.size} selettori aggiornati)$ciNote"
                    else -> "Playback completato (${sanitized.size} step)$ciNote"
                }
                playbackController.end(endMsg)
                if (outcome.error == null) {
                    PlaybackOverlayService.stop(this@AccessScopeApp)
                } else {
                    PlaybackOverlayService.showError(this@AccessScopeApp, endMsg ?: "")
                }
            }
        }
        AppFileLogger.info(
            "AccessScopeApp",
            "playback_start id=${flow.id} steps=${sanitized.size} clearState=$clearState",
        )
        return null
    }

    /**
     * Interrompe il playback corrente.
     *
     * @param userCancelled Se true, messaggio «annullato dall’utente».
     */
    fun stopFlowPlayback(userCancelled: Boolean = false) {
        playbackJob?.cancel()
        playbackJob = null
        PlaybackOverlayService.stop(this)
        if (playbackController.isPlaying || userCancelled) {
            playbackController.end(
                if (userCancelled) "Playback interrotto" else playbackController.state.value.statusMessage,
            )
        }
    }

    /**
     * Avvia scan WCAG sul package del flusso e, in parallelo, il playback.
     *
     * @return Messaggio errore o `null` se ok.
     */
    fun startScanWithFlow(flow: SavedFlow): String? {
        if (recordingController.isRecording) {
            return "Ferma prima la registrazione Maestro"
        }
        if (playbackController.isPlaying) {
            return "Ferma prima il playback in corso"
        }
        if (!PermissionHelper.isAccessibilityServiceEnabled(
                this,
                AccessScopeAccessibilityService::class.java,
            )
        ) {
            return "Abilita il servizio di accessibilità AccessScope."
        }
        if (AccessScopeAccessibilityService.instance == null) {
            return "Servizio accessibilità non collegato. Riattiva AccessScope in Accessibilità."
        }
        if (!PermissionHelper.canDrawOverlays(this)) {
            return "Concedi il permesso di sovrapposizione."
        }
        val scope = scanSettingsStore.getScanScope()
        scanRepository.startScan(setOf(flow.appId), scope)
        AccessScopeAccessibilityService.instance?.resetDynamicTracking()
        ScanOverlayService.start(this)
        val error = startFlowPlayback(flow, withScan = true)
        if (error != null) {
            // Rollback: senza playback la scansione appena avviata resterebbe attiva
            // con l'overlay appeso e nessun flusso in esecuzione.
            scanRepository.stopScan()
            ScanOverlayService.stop(this)
        }
        return error
    }

    /**
     * `true` se il flusso richiede PIN/password e il vault non li ha ancora.
     */
    fun needsMaestroCredentials(flow: SavedFlow): Boolean {
        val actions = flowStore.readActions(flow.id) ?: return false
        val appId = flow.appId
        var needPin = false
        var needPassword = false
        for (a in actions) {
            if (a !is RecordedAction.InputText) continue
            val t = a.text.trim()
            val pinLike = MaestroSelectorHeuristics.isPinLikeField(a.viewId) ||
                t.equals(CredentialVault.PLACEHOLDER_PIN, ignoreCase = true)
            val passLike = a.isPassword || t == "****" ||
                t.equals(CredentialVault.PLACEHOLDER_PASSWORD, ignoreCase = true)
            if (pinLike) needPin = true
            if (passLike && !pinLike) needPassword = true
        }
        if (needPin && credentialVault.get(appId, CredentialVault.Kind.Pin).isNullOrBlank()) return true
        if (needPassword && credentialVault.get(appId, CredentialVault.Kind.Password).isNullOrBlank()) return true
        return false
    }

    /**
     * Dry-run find-only sui tap/assert del flusso (senza gesture).
     *
     * @return Messaggio esito per Toast/UI.
     */
    suspend fun validateFlow(flow: SavedFlow): String {
        if (AccessScopeAccessibilityService.instance == null) {
            return "Servizio accessibilità non collegato. Riattiva AccessScope in Accessibilità."
        }
        val actions = flowStore.readActions(flow.id)
            ?: return "Flusso senza azioni tipizzate"
        val sanitized = FlowOptimizer.sanitizeForPlay(actions)
        val player = FlowPlayer(this, { AccessScopeAccessibilityService.instance }, credentialVault)
        val outcome = player.validate(sanitized)
        if (outcome.error != null) return outcome.error
        return if (outcome.validateFailures.isEmpty()) {
            "Validate OK (${sanitized.size} step)"
        } else {
            "Validate: ${outcome.validateFailures.size} step non trovati (indici ${outcome.validateFailures.map { it + 1 }})"
        }
    }

    /**
     * Salva PIN/password nel vault locale per [appId] (Play resolve `${PIN}` / `${PASSWORD}`).
     */
    fun saveMaestroCredential(appId: String, pin: String?, password: String?) {
        if (!pin.isNullOrBlank()) credentialVault.put(appId, CredentialVault.Kind.Pin, pin)
        if (!password.isNullOrBlank()) credentialVault.put(appId, CredentialVault.Kind.Password, password)
    }
}
