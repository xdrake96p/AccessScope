package dev.accessscope.scanner.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifica che il tracking schermate per il report dinamico non alteri il conteggio scansione.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ScanSessionRepositoryTest {

    private lateinit var repository: ScanSessionRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("access_scope_scan", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        repository = ScanSessionRepository(context)
    }

    @Test
    fun startScan_resetsScreenCounters() {
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        repository.registerUniqueScreen("fp-home", "Home")
        repository.stopScan()

        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        val state = repository.state.value
        assertEquals(0, state.uniqueScreens)
        assertTrue(state.visitedScreens.isEmpty())
        assertTrue(state.violations.isEmpty())
    }

    @Test
    fun registerUniqueScreen_incrementsOnlyOnFirstFingerprint() {
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)

        repository.registerUniqueScreen("fp-home", "Home")
        repository.registerUniqueScreen("fp-settings", "Impostazioni")
        repository.registerUniqueScreen("fp-home", "Home aggiornata")

        val state = repository.state.value
        assertEquals(2, state.uniqueScreens)
        assertEquals(2, state.visitedScreens.size)
        assertEquals(listOf("Home aggiornata", "Impostazioni"), state.visitedScreenTitles)
        assertEquals("Home aggiornata", state.visitedScreens.first().title)
        assertEquals(0, state.visitedScreens[0].visitIndex)
        assertEquals(1, state.visitedScreens[1].visitIndex)
    }

    @Test
    fun addViolations_dedupesWithoutAffectingScreenCount() {
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        repository.registerUniqueScreen("fp-home", "Home")

        val violation = AccessibilityViolation(
            type = ViolationType.MISSING_LABEL,
            viewClassName = "android.widget.Button",
            screenTitle = "Home",
            packageName = "com.example.app",
            details = "test",
            viewId = "com.example:id/btn",
            screenFingerprint = "fp-home",
        )
        repository.addViolations(listOf(violation, violation))

        val state = repository.state.value
        assertEquals(1, state.violations.size)
        assertEquals(1, state.uniqueScreens)
    }

    @Test
    fun stopScan_preservesCollectedDataButEndsSession() {
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        repository.registerUniqueScreen("fp-home", "Home")
        repository.stopScan()

        val state = repository.state.value
        assertFalse(state.isScanning)
        assertEquals(1, state.uniqueScreens)
        assertEquals(1, state.visitedScreens.size)
    }
}
