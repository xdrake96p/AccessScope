package dev.accessscope.scanner.service.scan

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.analyzer.DynamicContentTracker
import dev.accessscope.scanner.analyzer.NodeAccessibilityAnalyzer
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.util.AppFileLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Pianifica e coordina le analisi di accessibilità con debounce e retry.
 */
internal class AccessibilityScanScheduler(
    private val service: AccessScopeAccessibilityService,
    private val repository: ScanSessionRepository,
    private val dynamicTracker: DynamicContentTracker,
    private val densityProvider: () -> Float,
    private val executor: java.util.concurrent.Executor,
    private val retryExecutor: ScheduledExecutorService,
    private val lastScanByWindow: ConcurrentHashMap<String, Long>,
    private val retryScheduledKeys: MutableSet<String>,
    private val rootSelector: AccessibilityRootSelector,
    private val screenshotCapture: AccessibilityScreenshotCapture,
    private val treeScanner: AccessibilityTreeScanner,
    private val debounceMs: Long = 800L,
    private val windowStateDebounceMs: Long = 300L,
) {

    /**
     * Pianifica l'analisi di una finestra con debounce temporale.
     *
     * Esegue l'analisi su un thread dedicato, acquisendo eventualmente uno screenshot
     * se l'ambito di scansione include i controlli sul colore (API 30+).
     *
     * @param packageName Pacchetto dell'app target da analizzare.
     * @param event Evento di accessibilità che ha scatenato la scansione.
     */
    fun scheduleScan(packageName: String, event: AccessibilityEvent) {
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

        val eventSourceSnapshot = treeScanner.obtainEventSourceSnapshot(event)

        val silentDynamic = dynamicTracker.isSilentDynamicContent(packageName, event.windowId)
        val scanScope = repository.currentScanScope()
        val analyzer = NodeAccessibilityAnalyzer.create(densityProvider(), silentDynamic, scanScope)
        val captureScreenshot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        executor.execute {
            try {
                runScanAttempt(
                    packageName = packageName,
                    event = event,
                    eventSourceSnapshot = eventSourceSnapshot,
                    silentDynamic = silentDynamic,
                    scanScope = scanScope,
                    analyzer = analyzer,
                    needsScreenshot = captureScreenshot,
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
        val activeRoot = service.rootInActiveWindow
        val windows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) service.windows else null
        val (roots, diagnostics) = rootSelector.obtainRootsForScan(
            targetPackage = packageName,
            windows = windows,
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
            screenshotCapture.capture { capture ->
                try {
                    roots.forEach { root ->
                        treeScanner.scanRoot(root, packageName, event, capture, analyzer)
                    }
                } finally {
                    capture.bitmap?.recycle()
                    roots.forEach { it.recycle() }
                }
            }
        } else {
            try {
                roots.forEach { root ->
                    treeScanner.scanRoot(root, packageName, event, null, analyzer)
                }
            } finally {
                roots.forEach { it.recycle() }
            }
        }
    }
}
