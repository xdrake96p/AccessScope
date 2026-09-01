package dev.accessscope.scanner.recorder.optimization.picker

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerFlowHealerTest {

    @Test
    fun ensurePickerOpenBeforeSelect_doesNotDuplicateWhenFieldInLookback() {
        val raw = listOf(
            RecordedAction.Tap("it.nexi.bff", text = "Inserisci dati beneficiario (obbligatorio)"),
            RecordedAction.Tap("it.nexi.bff", text = "Fornitore Demo Srl"),
        )
        val healed = PickerFlowHealer.ensurePickerOpenBeforeSelect(raw, "it.nexi.bff")
        assertEquals(2, healed.size)
    }

    @Test
    fun ensurePickerOpenBeforeSelect_insertsFieldTapWhenOutsideLookback() {
        val filler = (1..9).map {
            RecordedAction.Tap("it.nexi.bff", text = "Filler $it")
        }
        val raw = listOf(
            RecordedAction.Tap("it.nexi.bff", text = "Inserisci dati beneficiario (obbligatorio)"),
        ) + filler + listOf(
            RecordedAction.Tap("it.nexi.bff", text = "Fornitore Demo Srl"),
        )
        val healed = PickerFlowHealer.ensurePickerOpenBeforeSelect(raw, "it.nexi.bff")
        val fieldTaps = healed.filterIsInstance<RecordedAction.Tap>()
            .count { it.text == "Inserisci dati beneficiario (obbligatorio)" }
        assertEquals(2, fieldTaps)
    }

    @Test
    fun ensurePickerOpenBeforeSelect_skipsWhenIconTapPresent() {
        val raw = listOf(
            RecordedAction.Tap("it.nexi.bff", viewId = "it.nexi.bff:id/img_search_contact"),
            RecordedAction.Tap("it.nexi.bff", text = "Fornitore Demo Srl"),
        )
        val healed = PickerFlowHealer.ensurePickerOpenBeforeSelect(raw, "it.nexi.bff")
        assertEquals(2, healed.size)
    }
}
