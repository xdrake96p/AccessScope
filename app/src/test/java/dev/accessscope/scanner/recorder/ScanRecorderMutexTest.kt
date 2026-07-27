package dev.accessscope.scanner.recorder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mutex scan WCAG ↔ registrazione Maestro. */
class ScanRecorderMutexTest {

    @Test
    fun startScan_blockedWhenRecording() {
        assertFalse(ScanRecorderMutexPolicy.canStartScan(isRecording = true))
    }

    @Test
    fun startRecording_blockedWhenScanning() {
        assertFalse(ScanRecorderMutexPolicy.canStartRecording(isScanning = true))
    }

    @Test
    fun bothAllowedWhenIdle() {
        assertTrue(ScanRecorderMutexPolicy.canStartScan(isRecording = false))
        assertTrue(ScanRecorderMutexPolicy.canStartRecording(isScanning = false))
    }
}
