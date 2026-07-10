package dev.accessscope.scanner.util

import android.graphics.Bitmap

/**
 * Esito acquisizione screenshot per una passata di scansione.
 */
data class ScreenshotCapture(
    val bitmap: Bitmap?,
    /** `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` dal sistema. */
    val flagSecure: Boolean = false,
    /** Bitmap vuoto/nero o capture non utilizzabile (non implica FLAG_SECURE). */
    val screenshotBlocked: Boolean = false,
) {
    /** @deprecated Usare [flagSecure] e [screenshotBlocked] separatamente. */
    @Deprecated("Use flagSecure or screenshotBlocked")
    val secureOrUnusable: Boolean
        get() = flagSecure || screenshotBlocked
}
