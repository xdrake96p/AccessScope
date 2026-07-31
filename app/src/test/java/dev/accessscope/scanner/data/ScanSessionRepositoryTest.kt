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
        repository.registerUniqueScreen(
            "fp-home",
            "Home aggiornata",
            ScreenProtectionReason.PIN_OR_PASSWORD,
        )

        val state = repository.state.value
        assertEquals(2, state.uniqueScreens)
        assertEquals(2, state.visitedScreens.size)
        assertEquals(listOf("Home aggiornata", "Impostazioni"), state.visitedScreenTitles)
        assertEquals("Home aggiornata", state.visitedScreens.first().title)
        assertEquals(ScreenProtectionReason.PIN_OR_PASSWORD, state.visitedScreens.first().protectionReason)
        assertEquals(ScreenProtectionReason.NONE, state.visitedScreens[1].protectionReason)
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
    fun addViolations_keepsLowConfidenceFindings_forLaterReportFiltering() {
        // Bug: se addViolations applicasse la soglia di confidenza in scrittura, i finding
        // sotto soglia sparirebbero per sempre e il toggle "findings a bassa confidenza"
        // (ReportHelper.filterViolations) non potrebbe mai recuperarli.
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        repository.registerUniqueScreen("fp-home", "Home")

        val lowConfidence = AccessibilityViolation(
            type = ViolationType.MISSING_LABEL,
            viewClassName = "android.widget.Button",
            screenTitle = "Home",
            packageName = "com.example.app",
            details = "test",
            viewId = "com.example:id/btn",
            screenFingerprint = "fp-home",
            confidence = 0.10f,
        )
        repository.addViolations(listOf(lowConfidence))

        val state = repository.state.value
        assertEquals(1, state.violations.size)
        assertEquals(0.10f, state.violations.first().confidence)
    }

    @Test
    fun addScreenReaderFindings_sameTitleDifferentFingerprint_bothKept() {
        // Bug: la chiave ignorava screenFingerprint — due schermate DIVERSE con lo stesso
        // titolo perdevano silenziosamente i finding della seconda.
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        val findingA = ScreenReaderFinding(
            packageName = "com.example.app",
            screenTitle = "Dettaglio",
            nodeClassName = "android.view.View",
            announcedText = null,
            issue = "TalkBack non avrebbe testo da annunciare su questo elemento.",
            screenFingerprint = "fp-A",
            boundsLabel = "10x10 px @(0,0)",
        )
        val findingB = findingA.copy(screenFingerprint = "fp-B")
        repository.addScreenReaderFindings(listOf(findingA))
        repository.addScreenReaderFindings(listOf(findingB))

        assertEquals(2, repository.state.value.screenReaderFindings.size)
    }

    @Test
    fun addScreenReaderFindings_multipleUnlabeledNodes_notCollapsedIntoOne() {
        // Bug: senza viewId/etichetta la chiave usava il token letterale "no-id" per tutti —
        // 10 immagini mute sulla stessa schermata collassavano in un solo finding.
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        val first = ScreenReaderFinding(
            packageName = "com.example.app",
            screenTitle = "Home",
            nodeClassName = "android.widget.ImageView",
            announcedText = null,
            issue = "TalkBack non avrebbe testo da annunciare su questo elemento.",
            screenFingerprint = "fp-home",
            boundsLabel = "40x40 px @(0,0)",
        )
        val second = first.copy(boundsLabel = "40x40 px @(400,800)")
        repository.addScreenReaderFindings(listOf(first, second))

        assertEquals(2, repository.state.value.screenReaderFindings.size)
    }

    @Test
    fun addScreenReaderFindings_aggregateFinding_stableAcrossChangingCount() {
        // Bug: il finding aggregato ">50% silenzioso" aveva il conteggio corrente nel testo
        // usato come identità — chiave diversa a ogni passata di scroll, righe duplicate.
        repository.startScan(setOf("com.example.app"), ScanScope.FULL)
        val pass1 = ScreenReaderFinding(
            packageName = "com.example.app",
            screenTitle = "Lista",
            nodeClassName = "—",
            announcedText = "3 / 5 elementi",
            issue = "Oltre il 50% degli elementi focalizzabili non ha un annuncio utile.",
            screenFingerprint = "fp-lista",
        )
        val pass2 = pass1.copy(announcedText = "6 / 9 elementi")
        repository.addScreenReaderFindings(listOf(pass1))
        repository.addScreenReaderFindings(listOf(pass2))

        assertEquals(1, repository.state.value.screenReaderFindings.size)
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
