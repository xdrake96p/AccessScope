/**
 * Servizio di accessibilità di AccessScope.
 *
 * Ascolta gli eventi di sistema dalle app target, analizza l'albero dei nodi UI
 * e registra violazioni di accessibilità, controlli superati e risultati TalkBack
 * nel [dev.accessscope.scanner.data.ScanSessionRepository].
 */
package dev.accessscope.scanner.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.analyzer.DynamicContentTracker
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.service.scan.AccessibilityRootSelector
import dev.accessscope.scanner.service.scan.AccessibilityScanScheduler
import dev.accessscope.scanner.service.scan.AccessibilityScreenshotCapture
import dev.accessscope.scanner.service.scan.AccessibilityTreeScanner
import dev.accessscope.scanner.util.AppFileLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
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

    private val evidenceStore
        get() = (application as AccessScopeApp).scanEvidenceStore

    private val density: Float
        get() = resources.displayMetrics.density

    private val dynamicTracker = DynamicContentTracker()
    private var executor = Executors.newSingleThreadExecutor()
    private val retryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val lastScanByWindow = ConcurrentHashMap<String, Long>()
    private val retryScheduledKeys = ConcurrentHashMap.newKeySet<String>()
    private val screenshotInFlight = AtomicBoolean(false)
    private val seenFingerprintsThisSession = mutableSetOf<String>()

    private val rootSelector = AccessibilityRootSelector()
    private val screenshotCapture by lazy {
        AccessibilityScreenshotCapture(this, mainExecutor, screenshotInFlight)
    }
    private val treeScanner by lazy {
        AccessibilityTreeScanner(repository, evidenceStore, seenFingerprintsThisSession)
    }
    private val scanScheduler by lazy {
        AccessibilityScanScheduler(
            service = this,
            repository = repository,
            dynamicTracker = dynamicTracker,
            densityProvider = { density },
            executor = executor,
            retryExecutor = retryExecutor,
            lastScanByWindow = lastScanByWindow,
            retryScheduledKeys = retryScheduledKeys,
            rootSelector = rootSelector,
            screenshotCapture = screenshotCapture,
            treeScanner = treeScanner,
        )
    }

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
     * l'analisi a [AccessibilityScanScheduler.scheduleScan] per cambi di finestra e contenuto.
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
            AppFileLogger.log("H1", "A11yService.onEvent", "event_received", mapOf(
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
                scanScheduler.scheduleScan(packageName, event)
            }
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                dynamicTracker.onAnnouncement(packageName)
            }
        }
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
