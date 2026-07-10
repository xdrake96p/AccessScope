/**
 * Skip contrasto, calendario Material e superfici.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionHome
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
        if (PrecisionHome.isBrandedOrPrimaryCtaText(snap, all, packageName)) return true
        if (isEmptyTextSurfaceWithoutContent(snap)) return true
        val area = screenAreaPx.takeIf { it > 0 } ?: PrecisionGeometry.estimateViewport(all).let { it.width() * it.height() }
        if (isTextOverIllustratedBackground(snap, all, area)) return true
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.carouselDecorativeContrastIds(packageName) &&
            PrecisionStructural.isInsideCarouselOrListItem(snap, all, packageName)
        ) {
            return true
        }
        if (id == "causale" && PrecisionStructural.isInsideCarouselOrListItem(snap, all, packageName)) return true
        if (id == "tv_title_second_section" && PrecisionHome.isHomeScreenContext(all, packageName)) return true
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
        // vop_info: l'icona è in realtà ad altissimo contrasto (drawable stroke scuro su bianco),
        // ma il campionamento screenshot può generare falsi positivi. Manteniamo solo il check
        // "manca contentDescription" tramite label/azioni, non il contrasto icona.
        if (PrecisionGeometry.viewIdShort(snap) == "vop_info") return true
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
     * Riconosce un contesto di calendario Material (DatePicker) che genera rumore massivo.
     *
     * In Nexi/BFF la schermata COMUNICAZIONI usa un calendario con celle ripetute (`material_calendar_day`)
     * e griglia tappabile; applicare i controlli touch/spacing/focus a ciascuna cella produce falsi positivi.
     */
    fun isMaterialCalendarContext(screenTitle: String, snapshots: List<NodeSnapshot>): Boolean {
        if (!screenTitle.contains("COMUNICAZIONI", ignoreCase = true)) return false
        val dayCells = snapshots.count { PrecisionGeometry.viewIdShort(it) == "material_calendar_day" }
        // Se ci sono molte celle giorno, è quasi certamente il DatePicker Material.
        return dayCells >= 12
    }

    /**
     * Identifica una singola cella giorno del calendario Material.
     *
     * Oltre all'ID, usa le coordinate di collection (grid) come fallback quando l'ID non è esposto.
     */
    fun isMaterialCalendarDayCell(
        snap: NodeSnapshot,
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
    ): Boolean {
        if (!isMaterialCalendarContext(screenTitle, snapshots)) return false
        if (PrecisionGeometry.viewIdShort(snap) == "material_calendar_day") return true
        // Fallback: celle in griglia piccole e ripetute.
        val isGridCell = snap.collectionRow >= 0 && snap.collectionColumn >= 0
        val smallish = snap.bounds.width() <= snap.minTouchTargetPx * 2 &&
            snap.bounds.height() <= snap.minTouchTargetPx * 2
        return isGridCell && smallish && snap.isInteractiveClickable()
    }

    /**
     * Determina se un nodo appartiene al cluster del calendario Material (griglia + header + controlli mese).
     *
     * Serve per escludere i controlli che generano falsi positivi sistematici (label/role/custom action/touch)
     * su componenti Material complessi.
     */
    fun isMaterialCalendarRelatedNode(
        snap: NodeSnapshot,
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
    ): Boolean {
        if (!isMaterialCalendarContext(screenTitle, snapshots)) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in setOf(
                "material_calendar_day",
                "gv_calendario",
                "ll_mese_precedente",
                "ll_mese_successivo",
                "iv_previous_month",
                "iv_next_month",
                "periodo_temp",
            )
        ) {
            return true
        }
        if (isMaterialCalendarDayCell(snap, screenTitle, snapshots)) return true

        // Fallback per nodi senza viewId (—) ma dentro la griglia calendario.
        val calendar = snapshots.firstOrNull { PrecisionGeometry.viewIdShort(it) == "gv_calendario" } ?: return false
        if (!Rect.intersects(calendar.bounds, snap.bounds)) return false
        val centerInside = calendar.bounds.contains(snap.bounds.centerX(), snap.bounds.centerY())
        if (!centerInside) return false
        val smallish = snap.bounds.width() <= snap.minTouchTargetPx * 2 &&
            snap.bounds.height() <= snap.minTouchTargetPx * 2
        return smallish
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
