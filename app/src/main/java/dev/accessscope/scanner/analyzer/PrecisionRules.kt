package dev.accessscope.scanner.analyzer

import android.graphics.Rect

object PrecisionRules {

    fun viewIdShort(snap: NodeSnapshot): String =
        snap.viewId?.substringAfterLast('/')?.lowercase().orEmpty()

    /** Link inline in un blocco di testo: esentato da touch target 48dp se il testo è leggibile. */
    fun isInlineTextLink(snap: NodeSnapshot): Boolean {
        if (!snap.isClickable && !snap.isLongClickable) return false
        val text = snap.text?.trim().orEmpty()
        if (text.isEmpty() || text.length > 40) return false
        return snap.className.contains("TextView", true) &&
            !snap.className.contains("Button", true) &&
            snap.bounds.height() >= snap.minTextHeightPx
    }

    fun isTopBarControl(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        return id in TOPBAR_CONTROL_IDS || id.startsWith("layout_topbar_icon")
    }

    private val TOPBAR_CONTROL_IDS = setOf(
        "topbar_icon_left",
        "topbar_icon_right",
        "layout_topbar_icon_left",
        "layout_topbar_icon_right",
        "topbar",
    )

    /** Topbar Nexi: parent clickable senza nome o icona @null. */
    fun shouldReportMissingTopBarLabel(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        val id = viewIdShort(snap)
        if (id == "layout_topbar_icon_left" || id == "layout_topbar_icon_right") {
            if (!snap.isInteractiveClickable()) return false
            if (snap.hasAccessibleName()) return false
            return !hasLabeledDescendant(snap, all)
        }
        if (id == "topbar_icon_left" || id == "topbar_icon_right") {
            if (!snap.contentDescription.isNullOrBlank()) return false
            val parentClickable = all.any { other ->
                other.traversalIndex < snap.traversalIndex &&
                    other.isInteractiveClickable() &&
                    other.bounds.contains(snap.bounds) &&
                    viewIdShort(other).startsWith("layout_topbar_icon")
            }
            if (!snap.isInteractiveClickable() && !parentClickable) return false
            return true
        }
        return false
    }

    /** Icona dentro un pulsante che ha già etichetta testuale nel parent — spesso falso positivo. */
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        if (hasLabeledClickableAncestor(snap, all)) return true
        if (isIconWithLabeledSibling(snap, all)) return true
        return all.any { other ->
            other != snap &&
                other.bounds.contains(snap.bounds) &&
                other.isInteractiveClickable() &&
                other.hasAccessibleName() &&
                !other.isImageClass()
        }
    }

    /** Icona con fratello etichettato (swipe tick/cestino, empty state). */
    fun isIconWithLabeledSibling(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        val container = findSmallestContainer(snap, all) ?: return false
        return all.any { sibling ->
            sibling != snap &&
                sibling != container &&
                container.bounds.contains(sibling.bounds) &&
                !sibling.isImageClass() &&
                sibling.hasAccessibleName()
        }
    }

    private fun findSmallestContainer(snap: NodeSnapshot, all: List<NodeSnapshot>): NodeSnapshot? =
        all.filter { it != snap && it.bounds.contains(snap.bounds) }
            .minByOrNull { it.bounds.width() * it.bounds.height() }

    fun hasLabeledClickableAncestor(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        return all.any { candidate ->
            candidate.traversalIndex < snap.traversalIndex &&
                candidate.isInteractiveClickable() &&
                candidate.hasAccessibleName() &&
                candidate.bounds.contains(snap.bounds)
        }
    }

    fun hasLabeledDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                snap.bounds.contains(other.bounds) &&
                other.hasAccessibleName()
        }

    /** Figli etichettati che intersecano il container (ScrollView spesso non contiene bounds stretti). */
    fun hasLabeledDescendantInScroll(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                Rect.intersects(snap.bounds, other.bounds) &&
                other.hasAccessibleName()
        }

    fun isMainContentScroll(snap: NodeSnapshot, screenArea: Int): Boolean {
        if (!snap.isScrollable) return false
        if (viewIdShort(snap) !in MAIN_CONTENT_SCROLL_IDS) return false
        if (screenArea <= 0) return true
        val snapArea = snap.bounds.width() * snap.bounds.height()
        return snapArea > screenArea * 0.35f
    }

    fun isCtaContainer(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        return id in setOf(
            "see_all_insolved",
            "show_more",
            "tv_see_account_movements",
            "container_custom_cta",
            "ll_custom",
        ) || snap.className.contains("CustomViewButtonCta", true)
    }

    fun hasTvCustomDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                viewIdShort(other) == "tv_custom" &&
                snap.bounds.contains(other.bounds) &&
                other.hasVisibleText()
        }

    /** Container cliccabile il cui figlio espone già il nome (es. CustomViewButtonCta). */
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (isCtaContainer(snap) && hasTvCustomDescendant(snap, all)) return true
        if (!snap.hasAccessibleName()) {
            if (hasLabeledDescendant(snap, all)) return true
            if (isLayoutContainer(snap.className) || snap.isCustomView()) {
                return all.any { other ->
                    other != snap &&
                        snap.bounds.contains(other.bounds) &&
                        other.hasAccessibleName() &&
                        other.hasVisibleText()
                }
            }
        }
        return false
    }

    /** CTA full-width: larghezza ok anche se l'altezza è sotto 48dp. */
    fun isWideTapTarget(snap: NodeSnapshot): Boolean =
        snap.bounds.width() >= snap.minTouchTargetPx * 3 &&
            snap.bounds.height() >= (snap.minTouchTargetPx * 0.55f).toInt()

    fun isLikelyStatusBadge(snap: NodeSnapshot): Boolean =
        snap.hasVisibleText() &&
            (snap.text?.length ?: 0) <= 24 &&
            snap.bounds.height() <= snap.minTouchTargetPx &&
            snap.bounds.width() <= snap.minTouchTargetPx * 3

    /** Badge stato, pill o etichetta campo — non è un heading strutturale. */
    fun shouldSkipHeadingCheck(snap: NodeSnapshot): Boolean {
        if (isLikelyStatusBadge(snap)) return true
        val viewIdShort = viewIdShort(snap)
        if (viewIdShort == "state" || viewIdShort.contains("badge") || viewIdShort.contains("status")) {
            return true
        }
        if (viewIdShort in setOf(
                "last_access", "name_account", "labelcontacts", "enroll_user",
                "tv_custom", "topbar_title", "no_result", "filtri_attivi",
                "totale_distinte", "total_amount_ins", "user_type", "currency",
                "multiple_slection", "checkbox_all",
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

    /** Label di campo in card/lista (causale, date, importi) — non heading. */
    fun isListFieldLabel(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        if (id.isEmpty()) return false
        val fieldIds = setOf(
            "causale", "nome_banca", "data_creazione", "data_esecuzione",
            "txt_data_creazione", "txt_data_esecuzione", "amount_dist", "amount_effetti",
            "beneficiario", "numero", "desc_breve", "scadenza", "iban", "ragione_sociale",
            "currency", "currency_symbol", "data_scadenza", "banca", "iban_account",
        )
        return id in fieldIds || id.startsWith("txt_data_") || id.startsWith("data_")
    }

    fun isKnownContrastFieldLabel(snap: NodeSnapshot): Boolean = isListFieldLabel(snap)

    fun isCurrencyOrAmountText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.matches(Regex("""^[\d\s.,€$+-]+$"""))) return true
        return t.matches(Regex("""^\d{1,3}(\.\d{3})*,\d{2}\s*€?$"""))
    }

    /** ViewId tipici di item template Nexi (carousel distinte/effetti, RecyclerView). */
    private val KNOWN_LIST_TEMPLATE_IDS = setOf(
        "content", "layout_content", "amount_dist", "amount_effetti", "causale",
        "vop_info", "data_creazione", "data_esecuzione", "txt_data_creazione",
        "txt_data_esecuzione", "nome_banca", "state",
        "check_multiple_selection", "multiple_slection", "beneficiario", "numero",
        "scadenza", "recycler_distinte", "currency_symbol", "layout_content",
    )

    private val MAIN_CONTENT_SCROLL_IDS = setOf("scrollview_port", "scroll", "card_home")

    fun isKnownListTemplateId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return false
        return viewId.substringAfterLast('/').lowercase() in KNOWN_LIST_TEMPLATE_IDS
    }

    fun isHomeChartOrCtaWidget(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        return id in setOf(
            "entrate_home", "uscite_home", "tv_see_account_movements",
            "see_all_insolved", "show_more", "last_30", "last_30_negative",
            "import_positive", "currency_incom", "currency_symbol",
        )
    }

    fun shouldSkipTouchSpacingBetween(a: NodeSnapshot, b: NodeSnapshot): Boolean {
        if (isTopBarControl(a) || isTopBarControl(b)) return true
        val ids = setOf(viewIdShort(a), viewIdShort(b))
        if ("topbar_title" in ids && ids.any { it.startsWith("topbar") || it.startsWith("layout_topbar") }) {
            return true
        }
        // Fascia topbar Nexi: elementi affiancati per design, spacing intenzionale
        val inTopBand = a.bounds.top < 400 && b.bounds.top < 400
        val topBarRelated = ids.any { it.startsWith("topbar") || it.startsWith("layout_topbar") }
        if (inTopBand && topBarRelated) return true
        return false
    }

    /** Stesso viewId ripetuto in item di lista (RecyclerView template). */
    fun isRecyclerListItem(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        val id = snap.viewId ?: return false
        val siblings = all.filter { it.viewId == id }
        if (siblings.size < 2) return false
        val sameClass = siblings.map { it.className }.distinct().size == 1
        if (!sameClass) return false
        val heights = siblings.map { it.bounds.height() }
        val avg = heights.average()
        val heightsSimilar = heights.all { kotlin.math.abs(it - avg) <= avg * 0.15 + 2 }
        return heightsSimilar
    }

    fun isScrollContainer(snap: NodeSnapshot): Boolean {
        val cls = snap.className.lowercase()
        val id = viewIdShort(snap)
        return snap.isScrollable && (
            cls.contains("scrollview") ||
                cls.contains("recyclerview") ||
                cls.contains("viewpager") ||
                cls.contains("horizontalscroll") ||
                id in setOf("scrollview_port", "scroll", "card_home", "content")
            )
    }

    fun shouldSkipScrollWithoutLabel(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int): Boolean {
        if (!snap.isScrollable) return false
        if (isMainContentScroll(snap, screenArea)) return true
        val cls = snap.className.lowercase()
        val isKnownContainer = isScrollContainer(snap) ||
            cls.contains("recyclerview") ||
            cls.contains("viewpager")
        if (!isKnownContainer) return false
        if (hasLabeledDescendant(snap, all) || hasLabeledDescendantInScroll(snap, all)) return true
        val snapArea = snap.bounds.width() * snap.bounds.height()
        if (screenArea > 0 && snapArea > screenArea * 0.5f) {
            val labeledChildren = all.count { other ->
                other != snap &&
                    Rect.intersects(snap.bounds, other.bounds) &&
                    other.hasAccessibleName()
            }
            if (labeledChildren >= 3) return true
        }
        return false
    }

    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (snap.unlabeledActionCount <= 0) return false
        if (snap.hasAccessibleName()) return false
        if (!snap.isInteractiveClickable() && !snap.isFocusable) return false
        if (isRecyclerListItem(snap, all)) return false
        if (isHomeChartOrCtaWidget(snap)) return false
        val id = viewIdShort(snap)
        if (id in setOf("multiple_slection", "checkbox_all") && snap.hasAccessibleName()) return false
        if (isCtaContainer(snap) && (hasTvCustomDescendant(snap, all) || hasLabeledDescendant(snap, all))) {
            return false
        }
        val cls = snap.className.lowercase()
        if (isScrollContainer(snap)) return false
        if (cls.contains("recyclerview") || cls.contains("scrollview") || cls.contains("viewpager")) {
            return false
        }
        if (viewIdShort(snap) in setOf("scrollview_port", "scroll", "card_home")) return false
        if (snap.isScrollable && hasLabeledDescendant(snap, all)) return false
        if (isLayoutContainer(snap.className) && hasLabeledDescendant(snap, all)) return false
        return true
    }

    /** Immagine probabilmente decorativa (non interattiva). */
    fun isDecorative(snap: NodeSnapshot): Boolean {
        if (isTopBarControl(snap)) return false
        val id = viewIdShort(snap)
        if (id in setOf("vop_info", "dot_filter")) return false
        return snap.isLikelyDecorative
    }

    fun shouldSkipDecorativeLabeledCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        if (isIconWithLabeledSibling(snap, all)) return true
        if (isTopBarControl(snap)) {
            val cd = snap.contentDescription?.trim().orEmpty()
            return cd.isNotBlank() && !isPoorAltText(cd)
        }
        return false
    }

    fun shouldSkipTouchTargetCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        isInlineTextLink(snap) || isIconInsideLabeledButton(snap, all) || isWideTapTarget(snap)

    fun shouldSkipSmallTextCheck(snap: NodeSnapshot): Boolean {
        if (snap.className.contains("Toolbar", true)) return true
        if (snap.text?.length == 1) return true
        return snap.bounds.height() >= snap.minTextHeightPx * 0.85
    }

    fun isPoorAltText(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.length < 2) return true
        val bad = setOf(
            "image", "img", "icon", "logo", "photo", "picture", "bitmap",
            "immagine", "foto", "icona", "logo",
        )
        if (bad.contains(t)) return true
        if (t.matches(Regex("""^(img|image|photo|icon)[-_]?\d*\.?(png|jpg|jpeg|webp|gif)?$"""))) return true
        if (t.matches(Regex("""^[a-z0-9_-]+\.(png|jpg|jpeg|webp)$"""))) return true
        return false
    }

    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?): Boolean {
        val combined = listOfNotNull(hint, text, contentDescription).joinToString(" ").lowercase()
        return combined.contains("obbligatorio") || combined.contains("required") || combined.contains("*")
    }

    fun isLayoutContainer(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("layout") ||
            lower.contains("viewgroup") ||
            lower.contains("constraint") ||
            lower.contains("coordinator") ||
            lower.contains("drawer")
    }
}
