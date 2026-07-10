package dev.accessscope.scanner.service.scan

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import dev.accessscope.scanner.util.AppFileLogger
import dev.accessscope.scanner.util.ScreenshotAnalyzer
import dev.accessscope.scanner.util.ScreenshotCapture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Acquisizione screenshot tramite API di accessibilità (API 30+).
 */
internal class AccessibilityScreenshotCapture(
    private val service: AccessibilityService,
    private val mainExecutor: Executor,
    private val screenshotInFlight: AtomicBoolean,
) {

    /**
     * Acquisisce uno screenshot del display predefinito tramite API di accessibilità (API 30+).
     *
     * Evita acquisizioni concorrenti tramite flag atomico; in caso di errore o API non
     * supportata invoca il callback con `null`.
     *
     * @param onResult Callback invocato sul thread principale con il bitmap o `null`.
     */
    fun capture(onResult: (ScreenshotCapture) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(ScreenshotCapture(bitmap = null))
            return
        }
        if (!screenshotInFlight.compareAndSet(false, true)) {
            onResult(ScreenshotCapture(bitmap = null))
            return
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    screenshotInFlight.set(false)
                    val bitmap = result.hardwareBuffer.toBitmap(result.colorSpace)
                    val unusable = ScreenshotAnalyzer.isBlackOrEmpty(bitmap)
                    if (unusable) {
                        bitmap.recycle()
                        onResult(ScreenshotCapture(bitmap = null, screenshotBlocked = true))
                    } else {
                        onResult(ScreenshotCapture(bitmap = bitmap))
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInFlight.set(false)
                    val secure = errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                    if (secure) {
                        AppFileLogger.info("A11yService", "screenshot_secure_window error=$errorCode")
                    }
                    onResult(ScreenshotCapture(bitmap = null, flagSecure = secure))
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
}
