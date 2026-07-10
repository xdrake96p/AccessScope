package dev.accessscope.scanner.util

import android.graphics.Bitmap

/**
 * Esito acquisizione screenshot per una passata di scansione.
 */
data class ScreenshotCapture(
    val bitmap: Bitmap?,
    /** Schermata FLAG_SECURE, fallimento secure, o bitmap quasi tutto nero. */
    val secureOrUnusable: Boolean,
)
