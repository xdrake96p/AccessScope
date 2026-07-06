package dev.accessscope.scanner.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.analyzer.DynamicContentTracker
import dev.accessscope.scanner.analyzer.ScreenTitleResolver
import dev.accessscope.scanner.analyzer.NodeAccessibilityAnalyzer
import dev.accessscope.scanner.data.ScanSessionRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AccessScopeAccessibilityService : AccessibilityService() {

    private val repository: ScanSessionRepository
        get() = (application as AccessScopeApp).scanRepository

    private val density: Float
        get() = resources.displayMetrics.density

    private val dynamicTracker = DynamicContentTracker()
    private val executor = Executors.newSingleThreadExecutor()
    private val lastScanByWindow = ConcurrentHashMap<String, Long>()
    private val screenshotInFlight = AtomicBoolean(false)
    private val debounceMs = 800L

    fun resetDynamicTracking() = dynamicTracker.reset()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!repository.state.value.isScanning) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        if (!repository.isTargetPackage(packageName)) return

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
        if (now - last < debounceMs) return
        lastScanByWindow[windowKey] = now

        val silentDynamic = dynamicTracker.isSilentDynamicContent(packageName, event.windowId)
        val analyzer = NodeAccessibilityAnalyzer.create(density, silentDynamic)

        executor.execute {
            val root = rootInActiveWindow ?: event.source ?: return@execute
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureScreenshot { bitmap ->
                    try {
                        scanRoot(root, packageName, event, bitmap, analyzer)
                    } finally {
                        bitmap?.recycle()
                        root.recycle()
                    }
                }
            } else {
                try {
                    scanRoot(root, packageName, event, null, analyzer)
                } finally {
                    root.recycle()
                }
            }
        }
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
        val screenTitle = ScreenTitleResolver.resolve(root, event)
        val result = analyzer.analyzeTree(root, packageName, screenTitle, screenshot)
        repository.addViolations(result.violations)
        repository.addScreenReaderFindings(result.screenReaderFindings)
        repository.incrementScreenCount()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        dynamicTracker.reset()
        executor.shutdown()
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            executor.shutdownNow()
        }
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
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
