package dev.accessscope.scanner.recorder.optimization.noise

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le fasi di [NoiseActionFilter] elencate qui girano **due volte** nella pipeline Maestro: una
 * volta in `FlowOptimizationPipeline.optimize()` (su azioni grezze), una seconda volta in
 * `sanitizeForPlay()` (sull'`actions.json` già ottimizzato — o modificato a mano nell'editor —
 * quindi già arricchito di `Wait`/`WaitForAnimation` da `WaitPlanner`/`BlockingOverlayWaitPlanner`).
 *
 * Due bug reali stanotte (`dropNoiseScrolls`, e prima ancora `ScrollCoalescer.coalesce`) sono
 * nati esattamente da una fase condivisa che assumeva silenziosamente "sono la prima passata" e
 * si comportava diversamente alla seconda. Questo test verifica meccanicamente, su fixture
 * modellate sui flussi reali analizzati stanotte (MPS/AXA), che rieseguire ciascuna fase
 * condivisa non cambi il risultato — la proprietà che avrebbe scoperto entrambi i bug prima che
 * arrivassero su un flusso reale, senza dover indovinare quale funzione ispezionare a mano.
 */
class SharedNoiseStageIdempotencyTest {

    private val pkg = "com.axa.app.myaxa.it.develop"

    /** Fixture stile AXA: login, PIN doppio, popup "NON ORA", 4 scroll con wait intrecciati. */
    private fun axaLikeFixture(): List<RecordedAction> = listOf(
        RecordedAction.LaunchApp(pkg, timestampMs = 0),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 2000, timestampMs = 100),
        RecordedAction.Tap(pkg, viewId = "$pkg:id/signInButton", text = "ACCEDI", timestampMs = 200),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 8000, timestampMs = 300),
        RecordedAction.InputText(pkg, "user@test.it", viewId = "$pkg:id/username", timestampMs = 400),
        RecordedAction.InputText(pkg, "pw", viewId = "$pkg:id/password", isPassword = true, timestampMs = 500),
        RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 600),
        RecordedAction.Wait(pkg, timeoutMs = 5000, timestampMs = 700),
        RecordedAction.Tap(pkg, text = "NON ORA", timestampMs = 800),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 8000, timestampMs = 900),
        RecordedAction.InputText(pkg, "121212", viewId = "$pkg:id/pincode", timestampMs = 1000),
        RecordedAction.Scroll(pkg, direction = ScrollDirection.DOWN, timestampMs = 1100),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 800, timestampMs = 1200),
        RecordedAction.Scroll(pkg, direction = ScrollDirection.DOWN, timestampMs = 1300),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 800, timestampMs = 1400),
        RecordedAction.Scroll(pkg, direction = ScrollDirection.DOWN, timestampMs = 1500),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 800, timestampMs = 1600),
        RecordedAction.Scroll(pkg, direction = ScrollDirection.DOWN, timestampMs = 1700),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 800, timestampMs = 1800),
        RecordedAction.Tap(pkg, text = "POLIZZA N.  404347818", timestampMs = 1900),
        RecordedAction.WaitForAnimation(pkg, timeoutMs = 1000, timestampMs = 2000),
        RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 2100),
    )

    /** Fixture con azioni di package esterni (tastiera, SystemUI) — bersaglio di dropForeignUiActions. */
    private fun foreignUiFixture(): List<RecordedAction> = listOf(
        RecordedAction.LaunchApp(pkg, timestampMs = 0),
        RecordedAction.Tap(pkg, viewId = "$pkg:id/search", timestampMs = 100),
        RecordedAction.Tap("com.google.android.inputmethod.latin", viewId = "key_pos_ime", timestampMs = 150),
        RecordedAction.InputText(pkg, "milano", viewId = "$pkg:id/search", timestampMs = 200),
        RecordedAction.Tap("com.android.systemui", viewId = "back", timestampMs = 250),
        RecordedAction.Tap(pkg, text = "Cerca", timestampMs = 300),
    )

    private val fixtures = listOf(axaLikeFixture(), foreignUiFixture())

    private fun assertIdempotent(name: String, fn: (List<RecordedAction>) -> List<RecordedAction>) {
        for ((index, fixture) in fixtures.withIndex()) {
            val once = fn(fixture)
            val twice = fn(once)
            assertEquals("$name non è idempotente sulla fixture #$index", once, twice)
        }
    }

    @Test
    fun dropForeignUiActions_isIdempotent() =
        assertIdempotent("dropForeignUiActions") { NoiseActionFilter.dropForeignUiActions(it, pkg) }

    @Test
    fun normalizePinOrOtpSlotInputs_isIdempotent() =
        assertIdempotent("normalizePinOrOtpSlotInputs") { NoiseActionFilter.normalizePinOrOtpSlotInputs(it) }

    @Test
    fun dropSpuriousRatingAsserts_isIdempotent() =
        assertIdempotent("dropSpuriousRatingAsserts") { NoiseActionFilter.dropSpuriousRatingAsserts(it) }

    @Test
    fun dropGhostTapsAfterScrollOrIme_isIdempotent() =
        assertIdempotent("dropGhostTapsAfterScrollOrIme") { NoiseActionFilter.dropGhostTapsAfterScrollOrIme(it) }

    @Test
    fun dropNoiseScrolls_isIdempotent() =
        assertIdempotent("dropNoiseScrolls") { NoiseActionFilter.dropNoiseScrolls(it) }

    @Test
    fun dropNoiseWaits_isIdempotent() =
        assertIdempotent("dropNoiseWaits") { NoiseActionFilter.dropNoiseWaits(it) }

    @Test
    fun dropNoiseScrolls_realAxaPattern_neverEatsScrollsBeyondFirstPass() {
        // Regressione esplicita del bug di stanotte: la seconda passata (quella che vede i
        // WaitForAnimation già inseriti da WaitPlanner) non deve mangiare scroll che la prima
        // passata aveva già lasciato stare.
        val onceOptimized = NoiseActionFilter.dropNoiseScrolls(axaLikeFixture())
        val scrollsAfterFirstPass = onceOptimized.count { it is RecordedAction.Scroll }
        val scrollsAfterSecondPass = NoiseActionFilter.dropNoiseScrolls(onceOptimized)
            .count { it is RecordedAction.Scroll }
        assertEquals(scrollsAfterFirstPass, scrollsAfterSecondPass)
    }
}
