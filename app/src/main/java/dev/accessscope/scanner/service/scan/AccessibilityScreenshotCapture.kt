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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Acquisizione screenshot tramite API di accessibilità (API 30+).
 *
 * Il callback viene eseguito su [callbackExecutor]: passare l'executor di scansione
 * (non il main executor) così conversione buffer→bitmap e analisi non bloccano la UI.
 * I fallimenti transitori e i bitmap neri vengono ritentati una volta con backoff
 * ([retryScheduler]) prima di marcare la schermata come non catturabile (piano B3).
 */
internal class AccessibilityScreenshotCapture(
    private val service: AccessibilityService,
    private val callbackExecutor: Executor,
    private val screenshotInFlight: AtomicBoolean,
    private val retryScheduler: ScheduledExecutorService? = null,
) {

    /**
     * Acquisisce uno screenshot del display predefinito tramite API di accessibilità (API 30+).
     *
     * Evita acquisizioni concorrenti tramite flag atomico; in caso di errore o API non
     * supportata invoca il callback con `null`.
     *
     * @param onResult Callback invocato su [callbackExecutor] con il bitmap o `null`.
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
        // Il flag in-flight resta true per tutta la catena tentativi+retry: viene
        // rilasciato in finish() prima di consegnare il risultato.
        captureAttempt(onResult, attempt = 0)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureAttempt(onResult: (ScreenshotCapture) -> Unit, attempt: Int) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            callbackExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val bitmap = try {
                        result.hardwareBuffer.toBitmap(result.colorSpace)
                    } finally {
                        // Senza close() il buffer nativo resta allocato: leak che dopo
                        // minuti di sessione fa degradare/fallire gli screenshot.
                        result.hardwareBuffer.close()
                    }
                    val unusable = ScreenshotAnalyzer.isBlackOrEmpty(bitmap)
                    if (unusable) {
                        bitmap.recycle()
                        if (scheduleRetry(onResult, attempt, reason = "black_bitmap")) return
                        finish(onResult, ScreenshotCapture(bitmap = null, screenshotBlocked = true))
                    } else {
                        finish(onResult, ScreenshotCapture(bitmap = bitmap))
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val secure = errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                    if (secure) {
                        // FLAG_SECURE è deterministico: ritentare non serve.
                        AppFileLogger.info("A11yService", "screenshot_secure_window error=$errorCode")
                        finish(onResult, ScreenshotCapture(bitmap = null, flagSecure = true))
                        return
                    }
                    if (scheduleRetry(onResult, attempt, reason = "error_$errorCode")) return
                    finish(onResult, ScreenshotCapture(bitmap = null))
                }
            },
        )
    }

    /**
     * Pianifica un nuovo tentativo con backoff se disponibile e sotto il limite.
     *
     * @return `true` se il retry è stato pianificato (il chiamante non deve consegnare esito).
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun scheduleRetry(
        onResult: (ScreenshotCapture) -> Unit,
        attempt: Int,
        reason: String,
    ): Boolean {
        val scheduler = retryScheduler ?: return false
        if (attempt >= MAX_RETRIES) return false
        AppFileLogger.info("A11yService", "screenshot_retry attempt=${attempt + 1} reason=$reason")
        scheduler.schedule(
            { captureAttempt(onResult, attempt + 1) },
            RETRY_BACKOFF_MS * (attempt + 1),
            TimeUnit.MILLISECONDS,
        )
        return true
    }

    private fun finish(onResult: (ScreenshotCapture) -> Unit, capture: ScreenshotCapture) {
        screenshotInFlight.set(false)
        onResult(capture)
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
        val wrapped = Bitmap.wrapHardwareBuffer(this, colorSpace)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        if (wrapped.config != Bitmap.Config.HARDWARE) return wrapped
        val copy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
        wrapped.recycle()
        // Un bitmap HARDWARE non è campionabile pixel-per-pixel: se la copia fallisce,
        // meglio un 1×1 (marcato "blocked" a valle) che un crash in analisi contrasto.
        return copy ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        /** Tentativi aggiuntivi oltre al primo (piano B3: 2 tentativi totali). */
        const val MAX_RETRIES = 1
        const val RETRY_BACKOFF_MS = 300L
    }
}
