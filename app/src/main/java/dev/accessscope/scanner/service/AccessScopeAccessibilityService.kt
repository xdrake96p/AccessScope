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
import dev.accessscope.scanner.util.DebugTrace
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AccessScopeAccessibilityService : AccessibilityService() {

    init {
        // #region agent log
        DebugTrace.log("H1", "A11yService.<init>", "constructed", emptyMap())
        // #endregion
    }

    private val repository: ScanSessionRepository
        get() = (application as AccessScopeApp).scanRepository

    private val density: Float
        get() = resources.displayMetrics.density

    private val dynamicTracker = DynamicContentTracker()
    private var executor = Executors.newSingleThreadExecutor()
    private val lastScanByWindow = ConcurrentHashMap<String, Long>()
    private val screenshotInFlight = AtomicBoolean(false)
    private val debounceMs = 800L
    private val windowStateDebounceMs = 300L
    private val seenFingerprintsThisSession = mutableSetOf<String>()

    fun resetDynamicTracking() {
        dynamicTracker.reset()
        seenFingerprintsThisSession.clear()
        ScreenTitleResolver.clearTitleCache()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val isScanning = repository.state.value.isScanning
        val isTarget = repository.isTargetPackage(packageName)

        // #region agent log
        if (packageName != applicationContext.packageName) {
            DebugTrace.log("H3", "A11yService.onEvent", "event_received", mapOf(
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

        val silentDynamic = dynamicTracker.isSilentDynamicContent(packageName, event.windowId)
        val scanScope = repository.currentScanScope()
        val analyzer = NodeAccessibilityAnalyzer.create(density, silentDynamic, scanScope)
        val needsScreenshot = scanScope.includes(ViolationArea.COLOR) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        executor.execute {
            val roots = obtainRootsForScan(packageName, event)
            if (roots.isEmpty()) {
                // #region agent log
                DebugTrace.log("H4", "scheduleScan", "no_root", mapOf(
                    "targetPkg" to packageName,
                    "activePkg" to rootInActiveWindow?.packageName?.toString(),
                ))
                // #endregion
                return@execute
            }
            // #region agent log
            DebugTrace.log("H2", "scheduleScan", "roots_obtained", mapOf(
                "targetPkg" to packageName,
                "rootCount" to roots.size,
            ))
            // #endregion
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
    }

    /** Raccoglie tutte le finestre target; preferisce overlay PIN/modal. */
    private fun obtainRootsForScan(
        targetPackage: String,
        event: AccessibilityEvent,
    ): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windows?.forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@forEach
                val windowRoot = window.root ?: return@forEach
                try {
                    if (windowRoot.packageName?.toString() == targetPackage) {
                        roots.add(AccessibilityNodeInfo.obtain(windowRoot))
                    }
                } finally {
                    windowRoot.recycle()
                }
            }
        }

        if (roots.isEmpty()) {
            event.source?.let { source ->
                roots.add(AccessibilityNodeInfo.obtain(source))
            }
            rootInActiveWindow?.let { active ->
                try {
                    if (active.packageName?.toString() == targetPackage) {
                        roots.add(AccessibilityNodeInfo.obtain(active))
                    }
                } finally {
                    active.recycle()
                }
            }
        }

        return prioritizeRoots(selectRootsToScan(roots))
    }

    /** Esclude drawer e, se possibile, analizza una sola finestra contenuto per evento. */
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

    private fun contentRootScore(root: AccessibilityNodeInfo): Int {
        val ids = ScreenTitleResolver.rootViewIds(root)
        var score = root.childCount
        if ("scrollview_port" in ids) score += 10_000
        if ("card_home" in ids) score += 5_000
        if (ids.any { it.startsWith("nav_") }) score -= 10_000
        return score
    }

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
        DebugTrace.log("H5", "scanRoot", "analyzed", mapOf(
            "pkg" to packageName,
            "screen" to screenTitle,
            "violations" to result.violations.size,
            "talkback" to result.screenReaderFindings.size,
            "hasScreenshot" to (screenshot != null),
        ))
        // #endregion
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        dynamicTracker.reset()
        lastScanByWindow.clear()
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AccessScopeAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // #region agent log
        DebugTrace.log("H1", "onServiceConnected", "service_bound", emptyMap())
        // #endregion
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        lastScanByWindow.clear()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        // #region agent log
        DebugTrace.log("H1", "onUnbind", "service_unbound", emptyMap())
        // #endregion
        return super.onUnbind(intent)
    }
}
