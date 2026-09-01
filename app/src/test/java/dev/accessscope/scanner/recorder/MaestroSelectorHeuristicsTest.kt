package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isNoiseTap`: un tap con solo `contentDescription` (righe di liste composite, es. sheet
 * "Rubrica"/"Seleziona IBAN") non deve essere trattato come rumore — vedi bug reale su
 * it.nexi.bff/Banca MPS dove la selezione dalla lista spariva dal flusso esportato.
 */
class MaestroSelectorHeuristicsTest {

    @Test
    fun tapWithOnlyContentDescription_isNotNoise() {
        val tap = RecordedAction.Tap(
            packageName = "it.nexi.bff",
            contentDescription = "Fornitore Demo Srl, IBAN IT20A0000000000000000000000",
        )
        assertFalse(MaestroSelectorHeuristics.isNoiseTap(tap))
    }

    @Test
    fun tapWithNoSelectorAtAll_isStillNoise() {
        val tap = RecordedAction.Tap(packageName = "it.nexi.bff")
        assertTrue(MaestroSelectorHeuristics.isNoiseTap(tap))
    }

    @Test
    fun dropNoiseTaps_keepsPickerListRowWithOnlyContentDescription() {
        val actions = listOf(
            RecordedAction.AssertVisible(packageName = "it.nexi.bff", text = "RUBRICA"),
            RecordedAction.Tap(
                packageName = "it.nexi.bff",
                contentDescription = "Fornitore Demo Srl, IBAN IT20A0000000000000000000000",
            ),
        )
        val kept = NoiseActionFilter.dropNoiseTaps(actions)
        assertTrue(kept.any { it is RecordedAction.Tap })
    }
}
