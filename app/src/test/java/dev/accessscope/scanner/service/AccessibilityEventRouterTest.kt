package dev.accessscope.scanner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressione routing eventi accessibilità recorder vs scan.
 */
class AccessibilityEventRouterTest {

    @Test
    fun routesToRecording_whenRecordingActive() {
        assertTrue(AccessibilityEventRouter.routesToRecording(isRecording = true))
    }

    @Test
    fun routesToScan_blockedWhenRecording() {
        assertFalse(
            AccessibilityEventRouter.routesToScan(
                isRecording = true,
                isScanning = true,
                isTargetPackage = true,
            ),
        )
    }

    @Test
    fun routesToScan_activeWhenScanningAndTarget() {
        assertTrue(
            AccessibilityEventRouter.routesToScan(
                isRecording = false,
                isScanning = true,
                isTargetPackage = true,
            ),
        )
    }

    @Test
    fun routesToScan_activeDuringPlayback() {
        assertTrue(
            AccessibilityEventRouter.routesToScan(
                isRecording = false,
                isScanning = true,
                isTargetPackage = true,
            ),
        )
    }
}
