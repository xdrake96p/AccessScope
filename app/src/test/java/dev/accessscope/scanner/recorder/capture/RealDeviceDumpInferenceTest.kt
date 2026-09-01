package dev.accessscope.scanner.recorder.capture

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Regressione end-to-end su **dump reali** catturati da dispositivo fisico (Samsung SM-G973F,
 * Banca MPS `it.nexi.bff`, 1 settembre 2026) durante il bug in cui la selezione dalla rubrica
 * spariva dal flusso Maestro registrato.
 *
 * I due dump rappresentano i due lati del confronto:
 * - `mps_rubrica_sheet.xml`: sheet RUBRICA aperto, righe beneficiario come `TextView` non
 *   editabili dentro `rv_rubrica` (il tocco su queste righe **non** genera `TYPE_VIEW_CLICKED`);
 * - `mps_sepa_form_filled.xml`: form BONIFICO SEPA dopo la selezione, con `edt_ragione_sociale`
 *   ed `edt_iban` valorizzati (popolati via `setText()`, **senza** `TYPE_VIEW_TEXT_CHANGED`).
 *
 * Il test replica le stesse regole di [InferredSelectionDetector.snapshot] sull'XML e verifica
 * che l'inferenza ricostruisca il passaggio perso. Vale come rete anti-regressione permanente:
 * se qualcuno reintroduce un filtro basato su id o su euristiche di testo, questo test si rompe.
 */
class RealDeviceDumpInferenceTest {

    @Test
    fun rubricaSelection_isRecoveredFromRealDumps() {
        val rubrica = parseDump("dumps/mps_rubrica_sheet.xml")
        val form = parseDump("dumps/mps_sepa_form_filled.xml")

        // Nello sheet la riga è solo testo visibile: nessun campo valorizzato da cui partire.
        assertTrue(
            "La riga beneficiario deve essere fra i testi visibili dello sheet",
            rubrica.visibleTexts.contains("Fornitore Demo Srl"),
        )
        // Lo sheet può avere il campo ricerca con hint ("Ricerca in rubrica") — non è ancora selezione.
        assertFalse(
            "Nel sheet la beneficiario non è ancora selezionata",
            rubrica.fieldValues.values.contains("Fornitore Demo Srl"),
        )

        // Nel form il valore è arrivato nel campo, con un id imprevedibile (`edt_ragione_sociale`).
        assertEquals(
            "Fornitore Demo Srl",
            form.fieldValues["it.nexi.bff:id/edt_ragione_sociale"],
        )

        val inferred = InferredSelectionDetector.inferSelection(
            before = rubrica.fieldValues,
            after = form.fieldValues,
            recentVisibleTexts = rubrica.visibleTexts,
        )

        assertNotNull("La selezione dalla rubrica deve essere inferita", inferred)
        assertEquals("Fornitore Demo Srl", inferred!!.matchedVisibleText)
        assertEquals("it.nexi.bff:id/edt_ragione_sociale", inferred.fieldViewId)
    }

    /**
     * Stato schermo estratto da un dump `uiautomator`, con le stesse regole dello snapshot runtime:
     * è un campo se `EditText` (per classe) — anche quando non risulta editabile — altrimenti il
     * testo confluisce fra i testi visibili.
     */
    private fun parseDump(resource: String): ScreenSnapshot {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resource)) {
            "Fixture mancante: $resource"
        }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        val fields = mutableMapOf<String, String>()
        val texts = mutableSetOf<String>()

        fun walk(node: Node) {
            if (node is Element && node.tagName == "node") {
                val text = node.getAttribute("text").trim()
                val contentDescription = node.getAttribute("content-desc").trim()
                val className = node.getAttribute("class")
                val viewId = node.getAttribute("resource-id")
                val isField = className.contains("EditText", ignoreCase = true)
                if (isField) {
                    if (viewId.isNotBlank() && text.isNotBlank()) fields[viewId] = text
                } else {
                    if (text.isNotBlank()) texts += text
                    if (contentDescription.isNotBlank()) texts += contentDescription
                }
            }
            val children = node.childNodes
            for (i in 0 until children.length) walk(children.item(i))
        }

        walk(document.documentElement)
        return ScreenSnapshot(fieldValues = fields, visibleTexts = texts)
    }
}
