package dev.accessscope.scanner.analyzer

import android.graphics.Rect

object PrecisionRules {

    fun viewIdShort(snap: NodeSnapshot): String =
        snap.viewId?.substringAfterLast('/')?.lowercase().orEmpty()

    fun estimateViewport(snapshots: List<NodeSnapshot>): Rect {
        if (snapshots.isEmpty()) return Rect()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = 0
        var bottom = 0
        snapshots.forEach { snap ->
            left = minOf(left, snap.bounds.left)
            top = minOf(top, snap.bounds.top)
            right = maxOf(right, snap.bounds.right)
            bottom = maxOf(bottom, snap.bounds.bottom)
        }
        return Rect(left, top, right, bottom)
    }

    /** Nodo fuori viewport o micro-testo in fondo schermo (layout nascosto). */
    fun isOffScreenOrMarginalNode(snap: NodeSnapshot, viewport: Rect, packageName: String = ""): Boolean {
        if (viewport.isEmpty) return false
        if (!Rect.intersects(snap.bounds, viewport)) return true
        val belowFold = snap.bounds.top > viewport.top + (viewport.height() * 0.90f).toInt()
        val tiny = snap.bounds.height() < snap.minTextHeightPx * 0.75f
        // Micro-testo sotto fold: sempre rumore (anche field label), salvo recall mirato in viewport
        if (belowFold && tiny) return true
        if (isKnownContrastFieldLabel(snap, packageName) && !belowFold) return false
        if (snap.bounds.left < viewport.left - snap.minTouchTargetPx) return true
        if (snap.bounds.right > viewport.right + snap.minTouchTargetPx) return true
        return false
    }

    /** Bounds anomali: striscia verticale/orizzontale non tappabile (es. 79×698 px). */
    fun isAnomalousTouchBounds(snap: NodeSnapshot): Boolean {
        val w = snap.bounds.width()
        val h = snap.bounds.height()
        val min = snap.minTouchTargetPx
        val thinVertical = w < (min * 0.75f).toInt() && h > min * 4
        val thinHorizontal = h < min / 3 && w > min * 4
        return thinVertical || thinHorizontal
    }

    /** Riga selezione lista a tutta larghezza — overlap intenzionale col container. */
    fun isFullWidthListRow(snap: NodeSnapshot, screenWidth: Int): Boolean {
        if (isCarouselSelectionRow(snap)) return true
        if (screenWidth <= 0) return false
        if (snap.bounds.width() < screenWidth * 0.80f) return false
        val id = viewIdShort(snap)
        return snap.isCheckable ||
            id.contains("select") ||
            id.contains("slection") ||
            id.contains("check") ||
            id.contains("selection") ||
            id.contains("checkbox")
    }

    /** Checkbox/riga selezione carousel distinte — overlap col FrameLayout parent è intenzionale. */
    fun isCarouselSelectionRow(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        return id in setOf("multiple_slection", "check_multiple_selection", "checkbox_all") ||
            (id.contains("slection") && snap.bounds.width() >= snap.minTouchTargetPx * 5)
    }

    fun shouldSkipStructuralNoise(
        snap: NodeSnapshot,
        viewport: Rect,
        screenWidth: Int,
        packageName: String = "",
    ): Boolean {
        if (isKnownContrastFieldLabel(snap, packageName)) return false
        if (isOffScreenOrMarginalNode(snap, viewport, packageName)) return true
        if (isAnomalousTouchBounds(snap)) return true
        if (isFullWidthListRow(snap, screenWidth)) return true
        return false
    }

    /** Icona toolbar in fascia alta — contrasto UI spesso FP se c'è CD o parent etichettato. */
    fun shouldSkipTopBarIconContrast(snap: NodeSnapshot, all: List<NodeSnapshot>, viewport: Rect): Boolean {
        if (!snap.isImageClass()) return false
        if (viewport.isEmpty) return isTopBarControl(snap)
        val topBand = viewport.top + (viewport.height() * 0.20f).toInt()
        if (snap.bounds.bottom > topBand) return false
        if (isTopBarControl(snap)) return true
        val id = viewIdShort(snap)
        if (id.contains("topbar") || id.contains("toolbar") || id.contains("action")) return true
        return hasLabeledClickableAncestor(snap, all) || !snap.contentDescription.isNullOrBlank()
    }

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

    /** Voce menu laterale (`nav_*`, `menu_*`, `drawer_*`). */
    fun isDrawerNavItem(snap: NodeSnapshot): Boolean {
        val id = viewIdShort(snap)
        return AppPrecisionProfiles.drawerNavPrefixes.any { id.startsWith(it) }
    }

    /** Scroll stretto del drawer (`scroll` ~13px) — non area principale. */
    fun isDrawerScroll(snap: NodeSnapshot): Boolean {
        if (!snap.isScrollable) return false
        return viewIdShort(snap) == "scroll" && snap.bounds.width() < snap.minTouchTargetPx
    }

    /** Bounds impossibili (es. nav_insoluti 1080×12) da layout drawer parzialmente esposto. */
    fun isPhantomClickableBounds(snap: NodeSnapshot): Boolean {
        if (!snap.isInteractiveClickable() && !snap.hasVisibleText()) return false
        return snap.bounds.height() < snap.minTouchTargetPx / 3 &&
            snap.bounds.width() > snap.minTouchTargetPx * 4
    }

    fun shouldSkipDrawerNode(snap: NodeSnapshot): Boolean =
        isDrawerNavItem(snap) || isDrawerScroll(snap) || isPhantomClickableBounds(snap)

    /** Container `content` ripetuto nel carousel distinte/effetti. */
    fun isCarouselContentContainer(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        val id = viewIdShort(snap)
        if (id != "content" && id != "layout_content") return false
        if (all.count { viewIdShort(it) == id } >= 2) return true
        return isRecyclerListItem(snap, all)
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
            val iconId = if (id.contains("left")) "topbar_icon_left" else "topbar_icon_right"
            val icon = all.firstOrNull { viewIdShort(it) == iconId && snap.bounds.contains(it.bounds) }
            if (icon?.hasAccessibleName() == true) return false
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

    private val HOME_SCREEN_MARKER_IDS = emptySet<String>() // use AppPrecisionProfiles

    fun isHomeScreenContext(all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        val markers = AppPrecisionProfiles.homeScreenMarkers(packageName)
        if (markers.isEmpty()) return false
        return all.any { viewIdShort(it) in markers }
    }

    fun isPinPadKey(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
        if (id in AppPrecisionProfiles.pinPadKeyIds(packageName)) return true
        val digit = snap.text?.trim()
        return snap.isInteractiveClickable() &&
            digit?.length == 1 &&
            digit[0].isDigit()
    }

    fun shouldSkipPinPadWhenNotPinScreen(snap: NodeSnapshot, screenTitle: String, packageName: String = ""): Boolean {
        if (screenTitle.contains("PIN", ignoreCase = true)) return false
        return isPinPadKey(snap, packageName)
    }

    fun shouldSkipHomeWidgetAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        if (!isHomeScreenContext(all, packageName)) return false
        val id = viewIdShort(snap)
        val chartContainers = AppPrecisionProfiles.homeChartContainerIds(packageName)
        val chartText = AppPrecisionProfiles.homeChartTextIds(packageName)
        val carouselWidgets = AppPrecisionProfiles.homeCarouselWidgetIds(packageName)
        if (id in chartContainers || isHomeChartOrCtaWidget(snap, packageName) || isCtaContainer(snap, packageName)) {
            return true
        }
        if (id in chartText || id in carouselWidgets) return true
        if (isHomeEffettiCarouselNode(snap, all, packageName)) return true
        return false
    }

    /** Tab o item carousel effetti in home — non schermata «Paga effetti». */
    fun isHomeEffettiCarouselNode(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        if (!isHomeScreenContext(all, packageName)) return false
        val id = viewIdShort(snap)
        if (id == "tv_tab") return true
        if (id !in setOf("numero", "amount_effetti", "scadenza", "beneficiario", "desc_breve")) return false
        return all.any { viewIdShort(it) in setOf("card_home", "tab_home", "card_effetti") }
    }

    /** Carousel distinte/effetti: swipe aggiorna contenuto senza live region — rumore su schermate lista. */
    fun shouldSkipSilentDynamicContent(
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
        packageName: String,
    ): Boolean {
        if (isHomeScreenContext(snapshots, packageName)) return true
        val title = screenTitle.uppercase()
        if (title.contains("DISTINTE") || title.contains("AUTORIZZA") ||
            title.contains("EFFETTI") || title.contains("PAGA")
        ) {
            return true
        }
        val ids = snapshots.map { viewIdShort(it) }.toSet()
        if (ids.contains("recycler_distinte") || ids.contains("recycler_effetti")) return true
        if (ids.contains("vop_info") && (ids.contains("multiple_slection") || ids.contains("amount_dist"))) {
            return true
        }
        if (isScrollableListScreen(snapshots)) return true
        return false
    }

    /** Liste con RecyclerView / ricerca: aggiornamenti scroll senza live region sono attesi. */
    fun isScrollableListScreen(snapshots: List<NodeSnapshot>): Boolean {
        val hasRecycler = snapshots.any {
            it.className.contains("RecyclerView", true) || it.className.contains("ListView", true)
        }
        if (!hasRecycler) return false
        val hasSearch = snapshots.any { snap ->
            snap.isEditable && (
                viewIdShort(snap).contains("search") ||
                    viewIdShort(snap).contains("edt_") ||
                    viewIdShort(snap).contains("input") ||
                    snap.hintText?.contains("cerca", ignoreCase = true) == true ||
                    snap.hintText?.contains("search", ignoreCase = true) == true
                )
        }
        val scrollables = snapshots.count { it.isScrollable }
        return hasSearch || scrollables >= 2
    }

    fun shouldSkipOverlapBetween(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenWidth: Int = 0,
    ): Boolean {
        if (isCarouselSelectionRow(a) || isCarouselSelectionRow(b)) return true
        if (shouldSkipTouchSpacingBetween(a, b)) return true
        if (screenWidth > 0 && (isFullWidthListRow(a, screenWidth) || isFullWidthListRow(b, screenWidth))) {
            return true
        }
        if (!isHomeScreenContext(all, packageName)) return false
        return shouldSkipHomeWidgetAnalysis(a, all, packageName) ||
            shouldSkipHomeWidgetAnalysis(b, all, packageName)
    }

    fun shouldSkipCarouselListItemAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean {
        if (isCarouselContentContainer(snap, all, packageName)) return true
        if (isCarouselSelectionRow(snap)) return true
        val id = viewIdShort(snap)
        if ((id.contains("select") || id.contains("selection")) &&
            all.count { viewIdShort(it) == id } >= 1 &&
            snap.bounds.width() > estimateViewport(all).width() * 0.75f
        ) {
            return true
        }
        return false
    }

    fun isMainContentScroll(snap: NodeSnapshot, screenArea: Int, packageName: String = ""): Boolean {
        if (!snap.isScrollable) return false
        if (viewIdShort(snap) !in AppPrecisionProfiles.mainContentScrollIds(packageName)) return false
        if (screenArea <= 0) return true
        val snapArea = snap.bounds.width() * snap.bounds.height()
        return snapArea > screenArea * 0.35f
    }

    fun isCtaContainer(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
        if (id in AppPrecisionProfiles.ctaContainerIds(packageName)) return true
        return snap.className.contains("CustomViewButtonCta", true) ||
            snap.className.contains("ButtonCta", true)
    }

    fun hasTvCustomDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                viewIdShort(other) == "tv_custom" &&
                snap.bounds.contains(other.bounds) &&
                other.hasVisibleText()
        }

    /** Container cliccabile il cui figlio espone già il nome (es. CustomViewButtonCta). */
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (shouldSkipHomeWidgetAnalysis(snap, all, packageName)) return true
        if (isCarouselContentContainer(snap, all, packageName)) return true
        if (isCtaContainer(snap, packageName) && hasTvCustomDescendant(snap, all)) return true
        if (!snap.hasAccessibleName()) {
            if (hasLabeledDescendant(snap, all) || hasLabeledDescendantInScroll(snap, all)) return true
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

    /** Label di campo in card/lista — pattern generici + profilo app. */
    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
        if (id.isEmpty()) return false
        if (id in AppPrecisionProfiles.fieldLabelIds(packageName)) return true
        return isGenericFieldLabelPattern(id)
    }

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

    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean =
        isListFieldLabel(snap, packageName)

    fun isCurrencyOrAmountText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.matches(Regex("""^[\d\s.,€$+-]+$"""))) return true
        return t.matches(Regex("""^\d{1,3}(\.\d{3})*,\d{2}\s*€?$"""))
    }

    fun isKnownListTemplateId(viewId: String?, packageName: String = ""): Boolean {
        if (viewId.isNullOrBlank()) return false
        return viewId.substringAfterLast('/').lowercase() in AppPrecisionProfiles.listTemplateIds(packageName)
    }

    fun isHomeChartOrCtaWidget(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = viewIdShort(snap)
        return id in AppPrecisionProfiles.homeChartTextIds(packageName) ||
            id in AppPrecisionProfiles.homeChartContainerIds(packageName) ||
            id in AppPrecisionProfiles.ctaContainerIds(packageName)
    }

    private val HOME_CHART_TEXT_IDS = emptySet<String>()
    private val HOME_CHART_CONTAINER_IDS = emptySet<String>()

    fun isHomeChartDecorativeText(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (!snap.hasVisibleText()) return false
        if (snap.isFocusable || snap.isInteractiveClickable()) return false
        val id = viewIdShort(snap)
        val chartText = AppPrecisionProfiles.homeChartTextIds(packageName)
        if (id == "last_30" || id == "last_30_negative") {
            if (!snap.contentDescription.isNullOrBlank()) return true
            if (isHomeScreenContext(all, packageName)) return true
        }
        if (id !in chartText) return false
        if (isInsideHomeChartContainer(snap, all, packageName)) return true
        return isHomeScreenContext(all, packageName)
    }

    fun isInsideHomeChartContainer(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        val containers = AppPrecisionProfiles.homeChartContainerIds(packageName)
        return all.any { other ->
            other != snap &&
                viewIdShort(other) in containers &&
                other.bounds.contains(snap.bounds)
        }
    }

    fun isBrandedCtaText(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (viewIdShort(snap) != "tv_custom") return false
        if (!snap.hasVisibleText()) return false
        return all.any { other ->
            other != snap &&
                (isCtaContainer(other, packageName) || viewIdShort(other) == "ll_custom") &&
                other.bounds.contains(snap.bounds)
        }
    }

    /** TextView in item RecyclerView/carousel — non heading strutturale di pagina. */
    fun isInsideCarouselOrListItem(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (snap.className.contains("RecyclerView", true)) return false
        return all.any { other ->
            other != snap &&
                other.bounds.contains(snap.bounds) &&
                other.bounds.height() > snap.bounds.height() * 1.5 &&
                (
                    other.className.contains("RecyclerView", true) ||
                        other.className.contains("ViewPager", true) ||
                        viewIdShort(other) in setOf(
                            "recycler_distinte", "recycler_effetti", "recycler",
                            "content", "layout_content",
                        ) ||
                        (isKnownListTemplateId(other.viewId, packageName) && other.bounds.area() > snap.bounds.area() * 2)
                    )
        }
    }

    private fun Rect.area(): Int = width() * height()

    fun shouldSkipTouchSpacingBetween(a: NodeSnapshot, b: NodeSnapshot): Boolean {
        if (shouldSkipDrawerNode(a) || shouldSkipDrawerNode(b)) return true
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

    fun shouldSkipScrollWithoutLabel(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int, packageName: String = ""): Boolean {
        if (isDrawerScroll(snap)) return true
        if (!snap.isScrollable) return false
        if (isMainContentScroll(snap, screenArea, packageName)) return true
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

    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (snap.unlabeledActionCount <= 0) return false
        if (snap.hasAccessibleName()) return false
        if (!snap.isInteractiveClickable() && !snap.isFocusable) return false
        if (isRecyclerListItem(snap, all)) return false
        if (isCarouselContentContainer(snap, all, packageName)) return false
        if (isHomeChartOrCtaWidget(snap, packageName)) return false
        val id = viewIdShort(snap)
        if (id.contains("select") || id.contains("selection")) return false
        if (id in setOf("multiple_slection", "checkbox_all") && snap.hasAccessibleName()) return false
        if (isCtaContainer(snap, packageName) && (hasTvCustomDescendant(snap, all) || hasLabeledDescendant(snap, all))) {
            return false
        }
        val cls = snap.className.lowercase()
        if (isScrollContainer(snap)) return false
        if (cls.contains("recyclerview") || cls.contains("scrollview") || cls.contains("viewpager")) {
            return false
        }
        if (viewIdShort(snap) in setOf("scrollview_port", "scroll", "card_home")) return false
        if (viewIdShort(snap) == "tv_custom") return false
        if (isBrandedCtaText(snap, all, packageName)) return false
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

    fun shouldSkipTouchTargetCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean =
        shouldSkipDrawerNode(snap) ||
            shouldSkipHomeWidgetAnalysis(snap, all, packageName) ||
            isInlineTextLink(snap) ||
            isIconInsideLabeledButton(snap, all) ||
            isWideTapTarget(snap) ||
            isCtaContainer(snap, packageName)

    fun shouldSkipSmallTextCheck(snap: NodeSnapshot, viewport: Rect = android.graphics.Rect(), packageName: String = ""): Boolean {
        if (shouldSkipDrawerNode(snap)) return true
        if (!viewport.isEmpty && isOffScreenOrMarginalNode(snap, viewport, packageName)) return true
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
