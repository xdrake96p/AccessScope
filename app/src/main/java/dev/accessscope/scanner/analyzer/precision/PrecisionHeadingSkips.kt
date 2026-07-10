/**
 * Skip euristiche per heading e field label.
 */
package dev.accessscope.scanner.analyzer.precision

import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot

internal object PrecisionHeadingSkips {
    fun shouldSkipHeadingCheck(snap: NodeSnapshot): Boolean {
        if (PrecisionTouch.isLikelyStatusBadge(snap)) return true
        val viewIdShort = PrecisionGeometry.viewIdShort(snap)
        if (viewIdShort == "state" || viewIdShort.contains("badge") || viewIdShort.contains("status")) {
            return true
        }
        if (viewIdShort in setOf(
                "last_access", "name_account", "labelcontacts", "enroll_user",
                "tv_custom", "topbar_title", "no_result", "filtri_attivi",
                "totale_distinte", "total_amount_ins", "user_type", "currency",
                "multiple_slection", "checkbox_all", "rotate_display", "logo",
                "tv_title_second_section", "show_more",
            )
        ) {
            return true
        }
        if (isListFieldLabel(snap)) return true
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
        if (id in AppPrecisionProfiles.fieldLabelIds(packageName)) return true
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
            id.contains("causale") ||
            id.contains("description") ||
            id.contains("subtitle") ||
            id.contains("hint")
}
