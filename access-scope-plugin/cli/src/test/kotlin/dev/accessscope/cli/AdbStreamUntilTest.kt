package dev.accessscope.cli

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [streamProcessUntil] è la base del wait "push" (via logcat) di
 * [ResultFetcher.waitForScanComplete] — testata qui con `sh` al posto di adb/logcat reali, per
 * restare un test JVM puro senza device.
 */
class AdbStreamUntilTest {

    @Test
    fun streamUntil_returnsAsSoonAsLineMatches_withoutWaitingFullProcessLifetime() {
        val start = System.currentTimeMillis()
        val found = streamProcessUntil(
            command = listOf("sh", "-c", "echo noise; sleep 0.2; echo scan_complete; sleep 5"),
            timeoutSeconds = 10,
        ) { line -> line.contains("scan_complete") }
        val elapsedMs = System.currentTimeMillis() - start

        assertTrue(found)
        // Deve tornare appena vede la riga (~0.2s), non aspettare i 5s finali del processo.
        assertTrue(elapsedMs < 3000, "expected early return, took ${elapsedMs}ms")
    }

    @Test
    fun streamUntil_timesOut_whenLineNeverAppears() {
        val found = streamProcessUntil(
            command = listOf("sh", "-c", "echo noise; sleep 5"),
            timeoutSeconds = 1,
        ) { line -> line.contains("scan_complete") }

        assertFalse(found)
    }
}
