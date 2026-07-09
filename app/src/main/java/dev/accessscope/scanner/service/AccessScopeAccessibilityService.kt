/**
 * Servizio di accessibilità di AccessScope.
 *
 * Ascolta gli eventi di sistema dalle app target, analizza l'albero dei nodi UI
 * e registra violazioni di accessibilità, controlli superati e risultati TalkBack
 * nel [dev.accessscope.scanner.data.ScanSessionRepository].
 */
package dev.accessscope.scanner.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.analyzer.DynamicContentTracker
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.analyzer.NodeAccessibilityAnalyzer
import dev.accessscope.scanner.analyzer.ScreenFingerprint
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.util.AppFileLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [AccessibilityService] che esegue l'analisi automatica delle schermate
 * delle app selezionate durante una sessione di scansione attiva.
 *
 * Gestisce debounce degli eventi, acquisizione screenshot per i controlli sul colore
 * e deduplicazione delle schermate tramite fingerprint.
 */
class AccessScopeAccessibilityService : AccessibilityService() {

    init {
        // #region agent log
        AppFileLogger.log("H1", "A11yService.<init>", "constructed", emptyMap())
        // #endregion
    }

    private val repository: ScanSessionRepository
        get() = (application as AccessScopeApp).scanRepository

    private val density: Float
        get() = resources.displayMetrics.density

    private val dynamicTracker = DynamicContentTracker()
    private var executor = Executors.newSingleThreadExecutor()
    private val retryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lastScanByWindow = ConcurrentHashMap<String, Long>()
    private val retryScheduledKeys = ConcurrentHashMap.newKeySet<String>()
    private val screenshotInFlight = AtomicBoolean(false)
    private val debounceMs = 800L
    private val windowStateDebounceMs = 300L
    private val seenFingerprintsThisSession = mutableSetOf<String>()

    /**
     * Azzera lo stato del tracker di contenuto dinamico e la cache dei titoli schermata.
     *
     * Invocato all'avvio di una nuova sessione di scansione per evitare contaminazione
     * dai dati della sessione precedente.
     */
    fun resetDynamicTracking() {
        dynamicTracker.reset()
        seenFingerprintsThisSession.clear()
        ScreenTitleResolver.clearTitleCache()
    }

    /**
     * Gestisce gli eventi di accessibilità provenienti dal sistema.
     *
     * Filtra gli eventi per pacchetto target e stato di scansione, poi delega
     * l'analisi a [scheduleScan] per cambi di finestra e contenuto.
     *
     * @param event Evento di accessibilità ricevuto; ignorato se `null`.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val isScanning = repository.state.value.isScanning
        val isTarget = repository.isTargetPackage(packageName)

        // #region agent log
        if (packageName != applicationContext.packageName) {
            AppFileLogger.log("H3", "A11yService.onEvent", "event_received", mapOf(
                "pkg" to packageName,
                "type" to event.eventType,
                "isScanning" to isScanning,
                "isTarget" to isTarget,
            ))
        }
        // #endregion

        if (!isScanning) return
        if (packageName == applicationContext.packageName) return
        if (!isTarget) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> {
                dynamicTracker.onContentChanged(packageName, event.windowId)
                scheduleScan(packageName, event)
            }
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                dynamicTracker.onAnnouncement(packageName)
            }
        }
    }

    /**
     * Pianifica l'analisi di una finestra con debounce temporale.
     *
     * Esegue l'analisi su un thread dedicato, acquisendo eventualmente uno screenshot
     * se l'ambito di scansione include i controlli sul colore (API 30+).
     *
     * @param packageName Pacchetto dell'app target da analizzare.
     * @param event Evento di accessibilità che ha scatenato la scansione.
     */
    private fun scheduleScan(packageName: String, event: AccessibilityEvent) {
        val windowKey = "${packageName}_${event.windowId}_${event.className}"
        val now = System.currentTimeMillis()
        val last = lastScanByWindow[windowKey] ?: 0L
        val debounce = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            windowStateDebounceMs
        } else {
            debounceMs
        }
        if (now - last < debounce) return
        lastScanByWindow[windowKey] = now

        val eventSourceSnapshot = obtainEventSourceSnapshot(event)

        val silentDynamic = dynamicTracker.isSilentDynamicContent(packageName, event.windowId)
        val scanScope = repository.currentScanScope()
        val analyzer = NodeAccessibilityAnalyzer.create(density, silentDynamic, scanScope)
        val needsScreenshot = scanScope.includes(ViolationArea.COLOR) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        executor.execute {
            try {
                runScanAttempt(
                    packageName = packageName,
                    event = event,
                    eventSourceSnapshot = eventSourceSnapshot,
                    silentDynamic = silentDynamic,
                    scanScope = scanScope,
                    analyzer = analyzer,
                    needsScreenshot = needsScreenshot,
                    isRetry = false,
                )
            } finally {
                eventSourceSnapshot?.recycle()
            }
        }
    }

    private fun runScanAttempt(
        packageName: String,
        event: AccessibilityEvent,
        eventSourceSnapshot: AccessibilityNodeInfo?,
        silentDynamic: Boolean,
        scanScope: dev.accessscope.scanner.data.ScanScope,
        analyzer: NodeAccessibilityAnalyzer,
        needsScreenshot: Boolean,
        isRetry: Boolean,
    ) {
        val windowKey = "${packageName}_${event.windowId}_${event.className}"
        val activeRoot = rootInActiveWindow
        val (roots, diagnostics) = obtainRootsForScan(
            targetPackage = packageName,
            eventSource = eventSourceSnapshot,
            activeRoot = activeRoot,
        )
        if (roots.isEmpty()) {
            AppFileLogger.log(
                "H4",
                "scheduleScan",
                if (isRetry) "no_root_retry" else "no_root",
                mapOf(
                    "targetPkg" to packageName,
                    "activePkg" to diagnostics.activePackage,
                    "focusedPkg" to diagnostics.focusedPackage,
                    "windowCount" to diagnostics.windowCount,
                    "candidates" to diagnostics.candidateCount,
                    "isRetry" to isRetry,
                ),
            )
            if (!isRetry &&
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                retryScheduledKeys.add(windowKey)
            ) {
                retryExecutor.schedule({
                    retryScheduledKeys.remove(windowKey)
                    runScanAttempt(
                        packageName = packageName,
                        event = event,
                        eventSourceSnapshot = null,
                        silentDynamic = silentDynamic,
                        scanScope = scanScope,
                        analyzer = analyzer,
                        needsScreenshot = needsScreenshot,
                        isRetry = true,
                    )
                }, 120, TimeUnit.MILLISECONDS)
            }
            return
        }
        AppFileLogger.log(
            "H2",
            "scheduleScan",
            if (isRetry) "roots_obtained_retry" else "roots_obtained",
            mapOf(
                "targetPkg" to packageName,
                "rootCount" to roots.size,
                "sources" to diagnostics.selectedSources.joinToString(),
                "activePkg" to diagnostics.activePackage,
                "isRetry" to isRetry,
            ),
        )
        if (needsScreenshot) {
            captureScreenshot { bitmap ->
                try {
                    roots.forEach { root ->
                        scanRoot(root, packageName, event, bitmap, analyzer)
                    }
                } finally {
                    bitmap?.recycle()
                    roots.forEach { it.recycle() }
                }
            }
        } else {
            try {
                roots.forEach { root ->
                    scanRoot(root, packageName, event, null, analyzer)
                }
            } finally {
                roots.forEach { it.recycle() }
            }
        }
    }

    /**
     * Clona [AccessibilityEvent.source] sul thread dell'evento, prima che venga riciclato dal framework.
     */
    private fun obtainEventSourceSnapshot(event: AccessibilityEvent): AccessibilityNodeInfo? =
        try {
            event.source?.let { AccessibilityNodeInfo.obtain(it) }
        } catch (e: IllegalStateException) {
            AppFileLogger.info("A11yService", "event_source_not_sealed type=${event.eventType}")
            null
        }

    /**
     * Raccoglie le radici [AccessibilityNodeInfo] da analizzare per il pacchetto target.
     *
     * Itera sulle finestre di sistema (escludendo overlay di accessibilità), con fallback
     * su `event.source` e `rootInActiveWindow`. Le radici vengono filtrate e prioritarizzate
     * tramite [selectRootsToScan] e [prioritizeRoots].
     *
     * @param targetPackage Pacchetto dell'app di cui ottenere le radici.
     * @param eventSource Snapshot clonato di `event.source` (main thread), oppure null.
     * @return Lista di radici clonate da analizzare; il chiamante deve chiamare [AccessibilityNodeInfo.recycle].
     */
    private fun obtainRootsForScan(
        targetPackage: String,
        eventSource: AccessibilityNodeInfo?,
        activeRoot: AccessibilityNodeInfo?,
    ): Pair<List<AccessibilityNodeInfo>, RootAcquisitionDiagnostics> {
        val windows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) windows else null
        val (acquired, diagnostics) = RootAcquisitionHelper.acquireRoots(
            targetPackage = targetPackage,
            windows = windows,
            eventSource = eventSource,
            activeRoot = activeRoot,
        )
        val filtered = prioritizeRoots(selectRootsToScan(acquired))
        acquired.filter { root -> filtered.none { it === root } }.forEach { it.recycle() }
        return filtered to diagnostics
    }

    /**
     * Seleziona le radici rilevanti per l'analisi, escludendo drawer e riducendo duplicati.
     *
     * Preferisce schermate PIN, dialog/modal e la finestra con punteggio di contenuto più alto.
     *
     * @param roots Elenco candidato di radici ottenute da [obtainRootsForScan].
     * @return Sottoinsieme filtrato di radici da analizzare (tipicamente una sola).
     */
    private fun selectRootsToScan(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val withoutDrawer = roots.filter { !ScreenTitleResolver.isDrawerOnlyRoot(it) }
        val candidates = if (withoutDrawer.isNotEmpty()) withoutDrawer else roots

        val pinRoots = candidates.filter { ScreenTitleResolver.isPinScreen(it) }
        if (pinRoots.isNotEmpty()) return pinRoots

        val modalRoots = candidates.filter { root ->
            val className = root.className?.toString().orEmpty()
            listOf("Dialog", "BottomSheet", "Popup", "AlertDialog", "Modal")
                .any { className.contains(it, true) }
        }
        if (modalRoots.isNotEmpty()) return modalRoots

        val primary = candidates.maxByOrNull { root -> contentRootScore(root) } ?: return candidates
        return listOf(primary)
    }

    /**
     * Calcola un punteggio euristico per identificare la radice con il contenuto principale.
     *
     * Premia view con `scrollview_port` e `card_home`, penalizza elementi di navigazione (`nav_`).
     *
     * @param root Radice candidata da valutare.
     * @return Punteggio numerico; valori più alti indicano contenuto principale.
     */
    private fun contentRootScore(root: AccessibilityNodeInfo): Int {
        val ids = ScreenTitleResolver.rootViewIds(root)
        var score = root.childCount
        if ("scrollview_port" in ids) score += 10_000
        if ("card_home" in ids) score += 5_000
        if (ids.any { it.startsWith("nav_") }) score -= 10_000
        return score
    }

    /**
     * Riordina le radici mettendo in testa PIN e modal rispetto al contenuto ordinario.
     *
     * @param roots Elenco di radici da prioritarizzare.
     * @return Stesso elenco o riordinato con PIN/modal in prima posizione.
     */
    private fun prioritizeRoots(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        if (roots.size <= 1) return roots

        val pinRoots = roots.filter { ScreenTitleResolver.isPinScreen(it) }
        if (pinRoots.isNotEmpty()) {
            val others = roots.filter { root -> pinRoots.none { it == root } }
            return pinRoots + others
        }

        val modalRoots = roots.filter { root ->
            val className = root.className?.toString().orEmpty()
            listOf("Dialog", "BottomSheet", "Popup", "AlertDialog", "Modal")
                .any { className.contains(it, true) }
        }
        if (modalRoots.isNotEmpty()) {
            val others = roots.filter { root -> modalRoots.none { it == root } }
            return modalRoots + others
        }

        return roots
    }

    /**
     * Acquisisce uno screenshot del display predefinito tramite API di accessibilità (API 30+).
     *
     * Evita acquisizioni concorrenti tramite flag atomico; in caso di errore o API non
     * supportata invoca il callback con `null`.
     *
     * @param onResult Callback invocato sul thread principale con il bitmap o `null`.
     */
    private fun captureScreenshot(onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null)
            return
        }
        if (!screenshotInFlight.compareAndSet(false, true)) {
            onResult(null)
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    screenshotInFlight.set(false)
                    onResult(result.hardwareBuffer.toBitmap(result.colorSpace))
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInFlight.set(false)
                    onResult(null)
                }
            },
        )
    }

    /**
     * Converte un [HardwareBuffer] dello screenshot in [Bitmap] modificabile in memoria.
     *
     * @receiver Buffer hardware restituito dall'API di screenshot.
     * @param colorSpace Spazio colore associato al buffer.
     * @return Bitmap in formato ARGB_8888, pronto per l'analisi del contrasto.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun HardwareBuffer.toBitmap(colorSpace: ColorSpace): Bitmap {
        val bitmap = Bitmap.wrapHardwareBuffer(this, colorSpace)
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
    }

    /**
     * Analizza una singola radice dell'albero UI e aggiorna il repository con i risultati.
     *
     * Ignora overlay transitori e drawer; registra violazioni, controlli superati,
     * risultati TalkBack e schermate uniche tramite fingerprint.
     *
     * @param root Radice dell'albero da analizzare.
     * @param packageName Pacchetto dell'app target.
     * @param event Evento di accessibilità che ha scatenato la scansione.
     * @param screenshot Bitmap opzionale per controlli sul colore; `null` se non disponibile.
     * @param analyzer Analizzatore configurato per l'ambito e la densità correnti.
     */
    private fun scanRoot(
        root: AccessibilityNodeInfo,
        packageName: String,
        event: AccessibilityEvent,
        screenshot: Bitmap?,
        analyzer: NodeAccessibilityAnalyzer,
    ) {
        if (ScreenTitleResolver.isTransientOverlay(root)) {
            return
        }
        if (ScreenTitleResolver.isDrawerOnlyRoot(root)) {
            return
        }
        val screenTitle = ScreenTitleResolver.resolve(root, event)
        val fingerprint = ScreenFingerprint.compute(root, packageName, screenTitle)
        val result = analyzer.analyzeTree(root, packageName, screenTitle, screenshot, fingerprint)
        repository.addViolations(result.violations)
        repository.addCheckSummaries(result.checkSummaries)
        if (repository.currentScanScope().includes(ViolationArea.SCREEN_READER)) {
            repository.addScreenReaderFindings(result.screenReaderFindings)
        }
        repository.incrementScanAnalysis()
        val isNewScreen = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            !seenFingerprintsThisSession.contains(fingerprint)
        if (isNewScreen) {
            seenFingerprintsThisSession.add(fingerprint)
            repository.registerUniqueScreen(fingerprint, screenTitle)
        }
        // #region agent log
        AppFileLogger.info("A11yService", "pass_summary screen=$screenTitle violations=${result.violations.size}")
        // #endregion
        AppFileLogger.info(
            "A11yService",
            "analyzed pkg=$packageName screen=$screenTitle violations=${result.violations.size}",
        )
    }

    /**
     * Callback di interruzione del servizio di accessibilità; non richiede azioni specifiche.
     */
    override fun onInterrupt() = Unit

    /**
     * Pulisce risorse e riferimenti statici alla distruzione del servizio.
     */
    override fun onDestroy() {
        dynamicTracker.reset()
        lastScanByWindow.clear()
        retryScheduledKeys.clear()
        retryExecutor.shutdownNow()
        instance = null
        super.onDestroy()
    }

    /**
     * Riferimenti statici e utilità per l'avvio/arresto del servizio.
     */
    companion object {
        /**
         * Istanza attiva del servizio, se connessa al sistema.
         * Usata per reset e diagnostica da altri componenti.
         */
        @Volatile
        var instance: AccessScopeAccessibilityService? = null
            private set
    }

    /**
     * Invocato quando il servizio viene collegato al framework di accessibilità.
     *
     * Registra l'istanza globale e ricrea l'executor se era stato chiuso.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // #region agent log
        AppFileLogger.log("H1", "onServiceConnected", "service_bound", emptyMap())
        // #endregion
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        lastScanByWindow.clear()
    }

    /**
     * Invocato quando il servizio viene scollegato dal sistema.
     *
     * @param intent Intent con cui il servizio era stato avviato, se presente.
     * @return Valore restituito alla superclasse per consentire il rebind automatico.
     */
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        // #region agent log
        AppFileLogger.log("H1", "onUnbind", "service_unbound", emptyMap())
        // #endregion
        return super.onUnbind(intent)
    }
}
