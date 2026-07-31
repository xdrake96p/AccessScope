/**
 * Skip contrasto, calendario Material e superfici.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionLabels
import dev.accessscope.scanner.analyzer.precision.PrecisionStructural
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionContrast {
    /**
     * TextView/AppCompatTextView con solo sfondo/drawable e nessun contenuto testuale accessibile.
     *
     * Non va misurato per contrasto: non c'è testo reale da valutare.
     */
    fun isEmptyTextSurfaceWithoutContent(snap: NodeSnapshot): Boolean {
        if (!snap.className.contains("TextView", ignoreCase = true)) return false
        if (snap.isHeading) return false
        if (snap.hasVisibleText()) return false
        if (!snap.hintText.isNullOrBlank()) return false
        if (!snap.contentDescription.isNullOrBlank()) return false
        if (snap.isEditable && !snap.hintText.isNullOrBlank()) return false
        return true
    }

    /**
     * Determina se il controllo contrasto testo va saltato per questo nodo.
     */
    fun shouldSkipContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ): Boolean {
        if (isEmptyTextSurfaceWithoutContent(snap)) return true
        val area = screenAreaPx.takeIf { it > 0 } ?: PrecisionGeometry.estimateViewport(all).let { it.width() * it.height() }
        if (isTextOverIllustratedBackground(snap, all, area)) return true
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.carouselDecorativeContrastIds(packageName) &&
            PrecisionStructural.isInsideCarouselOrListItem(snap, all, packageName)
        ) {
            return true
        }
        if (snap.isEditable && snap.text.isNullOrBlank() && !snap.hintText.isNullOrBlank()) {
            // Hint contrast handled separately; skip empty-value sampling
            return false
        }
        if (PrecisionRulesPlatform.isSkeletonPlaceholder(snap) || PrecisionRulesPlatform.isLottieAnimation(snap)) return true
        if (PrecisionRulesPlatform.isInsideMapOrMediaSurface(snap, all) && !snap.isMediaControl()) return true
        if (PrecisionRulesPlatform.shouldSkipComposeContrast(snap)) return true
        return false
    }

    /**
     * Determina se il controllo contrasto UI (icona) va saltato.
     */
    fun shouldSkipUiContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ): Boolean {
        if (snap.isLikelyDecorative) return true
        if (PrecisionRulesPlatform.isLottieAnimation(snap)) return true
        if (snap.isImageClass()) {
            if (!snap.isInteractiveClickable() && !snap.isFocusable) return true
            if (PrecisionLabels.isIconInsideLabeledButton(snap, all)) return true
            val maxDim = maxOf(snap.bounds.width(), snap.bounds.height())
            val maxIconPx = (snap.minTouchTargetPx * 1.5f).toInt()
            if (maxDim > maxIconPx) return true
            if (screenAreaPx > 0) {
                val nodeArea = snap.bounds.width().toLong() * snap.bounds.height()
                if (nodeArea >= screenAreaPx * 0.03) return true
            }
        }
        return false
    }

    /**
     * Testo caption/metadati sovrapposto a illustrazioni o foto di card (non controlli UI).
     *
     * Es. "POLIZZA N. …" su card assicurativa con grafica decorativa: il campionamento
     * screenshot non riflette il contrasto percepito e genera falsi positivi.
     */
    fun isTextOverIllustratedBackground(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        screenAreaPx: Int,
    ): Boolean {
        if (!snap.hasVisibleText()) return false
        if (snap.isInteractiveClickable() || snap.isFocusable) return false
        if (snap.isHeading) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id.contains("policy", ignoreCase = true) ||
            id.contains("card_", ignoreCase = true) ||
            id.endsWith("_number", ignoreCase = true)
        ) {
            if (overlapsLargeIllustration(snap, all, screenAreaPx)) return true
        }
        if (snap.bounds.height() > snap.minTextHeightPx) return false
        return overlapsLargeIllustration(snap, all, screenAreaPx)
    }

    private fun overlapsLargeIllustration(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        screenAreaPx: Int,
    ): Boolean {
        if (screenAreaPx <= 0) return false
        val minImageArea = screenAreaPx * 0.035
        return all.any { other ->
            other != snap &&
                other.isImageClass() &&
                !other.isInteractiveClickable() &&
                other.bounds.width().toLong() * other.bounds.height() >= minImageArea.toLong() &&
                (
                    Rect.intersects(snap.bounds, other.bounds) ||
                        other.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
                    )
        }
    }

    /**
     * Riconosce una griglia densa tipo calendario/DatePicker che genererebbe rumore massivo.
     *
     * Rilevamento puramente strutturale (numero di nodi con posizione di collection valorizzata,
     * `collectionRow`/`collectionColumn` standard Android), non legato a id o titoli di una
     * singola app: applicare i controlli touch/spacing/focus a ciascuna cella di una griglia
     * densa produrrebbe falsi positivi ripetuti su qualunque app.
     */
    fun isMaterialCalendarContext(snapshots: List<NodeSnapshot>): Boolean {
        val gridCells = snapshots.count { it.collectionRow >= 0 && it.collectionColumn >= 0 }
        return gridCells >= 12
    }

    /**
     * Identifica una singola cella di una griglia densa (calendario/DatePicker).
     *
     * @param snap Snapshot del nodo da valutare.
     * @param snapshots Elenco completo degli snapshot dei nodi nella schermata.
     */
    fun isMaterialCalendarDayCell(
        snap: NodeSnapshot,
        snapshots: List<NodeSnapshot>,
    ): Boolean {
        if (!isMaterialCalendarContext(snapshots)) return false
        val isGridCell = snap.collectionRow >= 0 && snap.collectionColumn >= 0
        val smallish = snap.bounds.width() <= snap.minTouchTargetPx * 2 &&
            snap.bounds.height() <= snap.minTouchTargetPx * 2
        return isGridCell && smallish && snap.isInteractiveClickable()
    }

    /**
     * Determina se un nodo appartiene al cluster di una griglia densa (calendario/DatePicker).
     *
     * Serve per escludere i controlli che generano falsi positivi sistematici (label/role/custom
     * action/touch) su componenti a griglia con molte celle ripetute.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param snapshots Elenco completo degli snapshot dei nodi nella schermata.
     */
    fun isMaterialCalendarRelatedNode(
        snap: NodeSnapshot,
        snapshots: List<NodeSnapshot>,
    ): Boolean {
        if (!isMaterialCalendarContext(snapshots)) return false
        return isMaterialCalendarDayCell(snap, snapshots)
    }

    /**
     * Evita FP su "Non raggiungibile con TalkBack" quando esiste un discendente/overlay focusabile.
     */
    fun hasFocusableOrEditableDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        return all.any { other ->
            other != snap &&
                snap.bounds.contains(other.bounds) &&
                (other.isFocusable || other.isEditable || other.isInteractiveClickable()) &&
                other.hasAccessibleName()
        }
    }
}
