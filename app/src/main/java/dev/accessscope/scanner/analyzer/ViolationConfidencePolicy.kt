/**
 * Politica di confidenza per ridurre rumore senza eliminare TP certi.
 *
 * Demota o esclude pattern tipici di falso positivo (container layout, overlap
 * enormi, ID strutturali) prima del filtro [dev.accessscope.scanner.report.ReportHelper].
 */
package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType

/**
 * Calcola confidenza effettiva e riconosce ID strutturali rumorosi.
 */
object ViolationConfidencePolicy {

    /** ViewId short noti come shell di layout (overlap/custom action spesso FP). */
    private val STRUCTURAL_NOISE_IDS = setOf(
        "content", "container", "root", "coordinator", "main", "main_content",
        "scrollview_port", "scroll_view", "scrollview", "recycler_view", "recyclerview",
        "tv_tab", "tab_layout", "view_pager", "viewpager", "nav_host", "fragment_container",
    )

    /**
     * Confidenza per overlap touch: demota container e aree enormi sotto soglia report.
     *
     * @param snap Nodo segnalato.
     * @param overlapPx Area di sovrapposizione in px².
     * @return Confidenza 0–1 da assegnare alla violazione.
     */
    fun overlappingTouchConfidence(snap: NodeSnapshot, overlapPx: Int): Float {
        if (isStructuralNoiseId(snap.viewId)) return 0.55f
        if (overlapPx > 100_000) return 0.58f
        return 0.88f
    }

    /**
     * Confidenza per custom action non etichettata su shell strutturali.
     */
    fun customActionConfidence(snap: NodeSnapshot): Float =
        if (isStructuralNoiseId(snap.viewId)) 0.55f else 0.88f

    /**
     * `true` se il viewId (short) è tipicamente un contenitore di layout, non un controllo.
     */
    fun isStructuralNoiseId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return false
        val short = viewId.substringAfterLast('/').lowercase()
        if (short in STRUCTURAL_NOISE_IDS) return true
        return short.startsWith("scroll") || short.endsWith("_container") || short.endsWith("_root")
    }

    /**
     * Applica demotion post-hoc su violazioni già emesse (es. sessioni storiche).
     *
     * @param violation Violazione originale.
     * @return Copia con confidenza ridotta se pattern rumoroso, altrimenti invariata.
     */
    fun demoteIfNoisy(violation: AccessibilityViolation): AccessibilityViolation {
        val short = violation.viewId?.substringAfterLast('/')?.lowercase()
        val demoted = when {
            violation.type == ViolationType.OVERLAPPING_TOUCH_TARGETS &&
                isStructuralNoiseId(violation.viewId) -> 0.55f
            violation.type == ViolationType.OVERLAPPING_TOUCH_TARGETS &&
                violation.details.contains("px") &&
                extractOverlapArea(violation.details)?.let { it > 100_000 } == true -> 0.58f
            violation.type == ViolationType.CUSTOM_ACTION_UNLABELED &&
                isStructuralNoiseId(violation.viewId) -> 0.55f
            violation.type == ViolationType.SCROLLABLE_WITHOUT_LABEL &&
                (short == "content" || short?.startsWith("scroll") == true) -> 0.60f
            else -> null
        } ?: return violation
        return if (demoted < violation.confidence) violation.copy(confidence = demoted) else violation
    }

    private fun extractOverlapArea(details: String): Int? {
        val digits = details.substringBefore("px").filter { it.isDigit() }
        return digits.toIntOrNull()
    }
}
