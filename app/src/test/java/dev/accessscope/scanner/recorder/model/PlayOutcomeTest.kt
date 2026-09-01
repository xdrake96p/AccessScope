package dev.accessscope.scanner.recorder.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayOutcome.divergences] — note su rami "morbidi" del Play in-app che il `maestro` CLI non
 * ha (segreto non risolto, fallback selettore/coordinate/PIN-pad, wait soft-fail). Non devono
 * mai cambiare [PlayOutcome.isSuccess]: il playback resta verde (demo sicura), le divergenze
 * sono solo informative.
 */
class PlayOutcomeTest {

    @Test
    fun divergences_doNotAffectSuccess() {
        val outcome = PlayOutcome(
            divergences = listOf(
                "step 12: segreto \${PASSWORD} non risolto → in CI serve maestro test -e PASSWORD=...",
            ),
        )
        assertTrue(outcome.isSuccess)
    }

    @Test
    fun defaultDivergences_isEmpty() {
        assertTrue(PlayOutcome().divergences.isEmpty())
    }

    @Test
    fun errorStillFails_regardlessOfDivergences() {
        val outcome = PlayOutcome(error = "Step 3: Tap non trovato", divergences = listOf("step 1: nota"))
        assertFalse(outcome.isSuccess)
    }
}
