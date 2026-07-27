/**
 * Servizio di accessibilità di AccessScope.
 *
 * Ascolta gli eventi di sistema dalle app target, analizza l'albero dei nodi UI
 * e registra violazioni di accessibilità, controlli superati e risultati TalkBack
 * nel [dev.accessscope.scanner.data.ScanSessionRepository].
 */
package dev.accessscope.scanner.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.analyzer.DynamicContentTracker
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.service.scan.AccessibilityRootSelector
import dev.accessscope.scanner.service.scan.AccessibilityScanScheduler
import dev.accessscope.scanner.service.scan.AccessibilityScreenshotCapture
import dev.accessscope.scanner.service.scan.AccessibilityTreeScanner
import dev.accessscope.scanner.recorder.AccessibilityRootProvider
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
        if (packageName == applicationContext.packageName) return

        val app = application as AccessScopeApp
        val recording = app.recordingController
        if (AccessibilityEventRouter.routesToRecording(recording.isRecording)) {
            val metrics = resources.displayMetrics
            val rootProvider = AccessibilityRootProvider {
                runCatching { rootInActiveWindow }.getOrNull()
            }
            recording.onAccessibilityEvent(
                event,
                metrics.widthPixels,
                metrics.heightPixels,
                rootProvider,
            )
            return
        }

        val isScanning = repository.state.value.isScanning
        val isTarget = repository.isTargetPackage(packageName)

        if (!AccessibilityEventRouter.routesToScan(recording.isRecording, isScanning, isTarget)) return

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
        // Forza i tipi evento a runtime: dopo install/force-stop la config XML può restare stale.
        applyRecordingCapableServiceInfo()
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        lastScanByWindow.clear()
    }

    /**
     * Applica flag/eventi necessari a scan WCAG + recorder Maestro.
     */
    private fun applyRecordingCapableServiceInfo() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_CLICKED or
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
            AccessibilityEvent.TYPE_VIEW_FOCUSED or
            AccessibilityEvent.TYPE_VIEW_SELECTED or
            AccessibilityEvent.TYPE_ANNOUNCEMENT
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    /**
     * Invocato quando il servizio viene scollegato dal sistema.
     *
     * @param intent Intent con cui il servizio era stato avviato, se presente.
     * @return Valore restituito alla superclasse per consentire il rebind automatico.
     */
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
