package dev.accessscope.scanner.recorder.capture

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `InferredSelectionDetector.snapshot`: separa i campi valore dai testi visibili, app-agnostico
 * per design (nessun filtro su id — vedi bug reale su it.nexi.bff/Banca MPS, campo beneficiario
 * `edt_ragione_sociale` che non somiglia a nessun pattern id "beneficiar"/"iban" noto).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PickerBackedFieldValuesTest {

    @Test
    fun editableFieldWithValue_goesToFieldValues() {
        val field = AccessibilityNodeInfo.obtain().apply {
            isEditable = true
            className = "android.widget.EditText"
            viewIdResourceName = "it.nexi.bff:id/edt_ragione_sociale"
            text = "Fornitore Demo Srl"
        }
        val snapshot = InferredSelectionDetector.snapshot(listOf(field))
        assertEquals("Fornitore Demo Srl", snapshot.fieldValues["it.nexi.bff:id/edt_ragione_sociale"])
        field.recycle()
    }

    @Test
    fun editTextClassNotEditableFlag_stillCountsAsField() {
        // Bug reale su it.nexi.bff/Banca MPS: edt_ragione_sociale/edt_iban sono EditText ma
        // isEditable=false (focusable="false" nel dump) — resi non digitabili apposta per forzare
        // la selezione da rubrica, ma sono comunque i campi valore da osservare.
        val field = AccessibilityNodeInfo.obtain().apply {
            isEditable = false
            className = "android.widget.EditText"
            viewIdResourceName = "it.nexi.bff:id/edt_iban"
            text = "IT20A0000000000000000000000"
        }
        val snapshot = InferredSelectionDetector.snapshot(listOf(field))
        assertEquals("IT20A0000000000000000000000", snapshot.fieldValues["it.nexi.bff:id/edt_iban"])
        field.recycle()
    }

    @Test
    fun hintOnlyField_hasNoValue() {
        val field = AccessibilityNodeInfo.obtain().apply {
            isEditable = true
            className = "android.widget.EditText"
            viewIdResourceName = "it.nexi.bff:id/edt_ragione_sociale"
            text = "Inserisci dati beneficiario (obbligatorio)"
            hintText = "Inserisci dati beneficiario (obbligatorio)"
        }
        val snapshot = InferredSelectionDetector.snapshot(listOf(field))
        assertTrue(snapshot.fieldValues.isEmpty())
        field.recycle()
    }

    @Test
    fun listRowTextView_goesToVisibleTexts_notFields() {
        // La riga della rubrica: TextView non editabile — è il testo da cui inferire la scelta.
        val row = AccessibilityNodeInfo.obtain().apply {
            isEditable = false
            className = "android.widget.TextView"
            viewIdResourceName = "it.nexi.bff:id/name_account"
            text = "Fornitore Demo Srl"
        }
        val snapshot = InferredSelectionDetector.snapshot(listOf(row))
        assertTrue(snapshot.fieldValues.isEmpty())
        assertTrue(snapshot.visibleTexts.contains("Fornitore Demo Srl"))
        row.recycle()
    }
}
