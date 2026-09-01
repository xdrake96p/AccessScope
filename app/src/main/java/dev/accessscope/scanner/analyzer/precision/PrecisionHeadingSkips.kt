/**
 * Skip euristiche per heading e field label.
 */
package dev.accessscope.scanner.analyzer.precision

import dev.accessscope.scanner.analyzer.NodeSnapshot

internal object PrecisionHeadingSkips {
    fun shouldSkipHeadingCheck(snap: NodeSnapshot): Boolean {
        if (PrecisionTouch.isLikelyStatusBadge(snap)) return true
        val viewIdShort = PrecisionGeometry.viewIdShort(snap)
        if (viewIdShort == "state" || viewIdShort.contains("badge") || viewIdShort.contains("status")) {
            return true
        }
        if (isListFieldLabel(snap)) return true
        // Etichetta di campo obbligatorio (es. "Ragione sociale (Obbligatorio)"): non è un
        // heading, è testo di form — riusa lo stesso helper già usato per il contrasto, prima
        // mai collegato a questo path (test su it.nexi.bff/MPS: veniva segnalata come salto di
        // livello heading).
        if (PrecisionLabels.isRequiredFieldHint(snap.hintText, snap.text, snap.contentDescription)) {
            return true
        }
        val text = snap.text?.trim().orEmpty()
        if (text.isNotEmpty() && text == text.uppercase() && text.length <= 24 &&
            snap.bounds.height() <= snap.minTouchTargetPx
        ) {
            return true
        }
        return false
    }

    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id.isEmpty()) return false
        return isGenericFieldLabelPattern(id)
    }

    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean =
        isListFieldLabel(snap, packageName)

    private fun isGenericFieldLabelPattern(id: String): Boolean =
        id.startsWith("txt_data_") ||
            id.startsWith("data_") ||
            id.contains("label") ||
            id.contains("iban") ||
            id.contains("amount") ||
            id.contains("email") ||
            id.contains("phone") ||
            id.contains("description") ||
            id.contains("subtitle") ||
            id.contains("hint")
}
