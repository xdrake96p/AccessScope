/**
 * Facade per regole etichette, heading e decorative.
 */
package dev.accessscope.scanner.analyzer.precision

import dev.accessscope.scanner.analyzer.NodeSnapshot

internal object PrecisionLabels {
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionLabelHierarchy.isIconInsideLabeledButton(snap, all)
    fun isIconWithLabeledSibling(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionLabelHierarchy.isIconWithLabeledSibling(snap, all)
    fun hasLabeledClickableAncestor(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionLabelHierarchy.hasLabeledClickableAncestor(snap, all)
    fun hasLabeledDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionLabelHierarchy.hasLabeledDescendant(snap, all)
    fun hasLabeledDescendantInScroll(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionLabelHierarchy.hasLabeledDescendantInScroll(snap, all)
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = "") =
        PrecisionLabelHierarchy.shouldSkipContainerLabelCheck(snap, all, packageName)
    fun shouldSkipHeadingCheck(snap: NodeSnapshot) = PrecisionHeadingSkips.shouldSkipHeadingCheck(snap)
    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = "") =
        PrecisionHeadingSkips.isListFieldLabel(snap, packageName)
    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = "") =
        PrecisionHeadingSkips.isKnownContrastFieldLabel(snap, packageName)
    fun isCurrencyOrAmountText(text: String) = isCurrencyOrAmountTextImpl(text)
    fun isKnownListTemplateId(viewId: String?, packageName: String = "") =
        isKnownListTemplateIdImpl(viewId, packageName)
    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = "") =
        shouldReportCustomActionImpl(snap, all, packageName)
    fun isDecorative(snap: NodeSnapshot) = PrecisionDecorativeLabels.isDecorative(snap)
    fun shouldSkipDecorativeLabeledCheck(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionDecorativeLabels.shouldSkipDecorativeLabeledCheck(snap, all)
    fun isLikelyNavigationImage(snap: NodeSnapshot, all: List<NodeSnapshot>) =
        PrecisionDecorativeLabels.isLikelyNavigationImage(snap, all)
    fun isPoorAltText(text: String) = PrecisionDecorativeLabels.isPoorAltText(text)
    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?) =
        isRequiredFieldHintImpl(hint, text, contentDescription)
    fun isLayoutContainer(className: String) = isLayoutContainerImpl(className)
}

private fun isCurrencyOrAmountTextImpl(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    if (t.matches(Regex("""^[\d\s.,€$+-]+$"""))) return true
    return t.matches(Regex("""^\d{1,3}(\.\d{3})*,\d{2}\s*€?$"""))
}

private fun isKnownListTemplateIdImpl(viewId: String?, packageName: String = ""): Boolean {
    if (viewId.isNullOrBlank()) return false
    return viewId.substringAfterLast('/').lowercase() in dev.accessscope.scanner.analyzer.AppPrecisionProfiles.listTemplateIds(packageName)
}

private fun shouldReportCustomActionImpl(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
    if (snap.unlabeledActionCount <= 0) return false
    if (snap.hasAccessibleName()) return false
    if (!snap.isInteractiveClickable() && !snap.isFocusable) return false
    if (PrecisionStructural.isRecyclerListItem(snap, all)) return false
    if (PrecisionNavigation.isCarouselContentContainer(snap, all, packageName)) return false
    if (PrecisionHome.isHomeChartOrCtaWidget(snap, packageName)) return false
    val id = PrecisionGeometry.viewIdShort(snap)
    if (id.contains("select") || id.contains("selection")) return false
    if (id in setOf("multiple_slection", "checkbox_all") && snap.hasAccessibleName()) return false
    if (PrecisionHome.isCtaContainer(snap, packageName) && (PrecisionHome.hasTvCustomDescendant(snap, all) || PrecisionLabelHierarchy.hasLabeledDescendant(snap, all))) {
        return false
    }
    val cls = snap.className.lowercase()
    if (PrecisionStructural.isScrollContainer(snap)) return false
    if (cls.contains("recyclerview") || cls.contains("scrollview") || cls.contains("viewpager")) {
        return false
    }
    if (PrecisionGeometry.viewIdShort(snap) in setOf("scrollview_port", "scroll", "card_home")) return false
    if (PrecisionGeometry.viewIdShort(snap) == "tv_custom") return false
    if (PrecisionHome.isBrandedCtaText(snap, all, packageName)) return false
    if (snap.isScrollable && PrecisionLabelHierarchy.hasLabeledDescendant(snap, all)) return false
    if (isLayoutContainerImpl(snap.className) && PrecisionLabelHierarchy.hasLabeledDescendant(snap, all)) return false
    return true
}

private fun isRequiredFieldHintImpl(hint: String?, text: String?, contentDescription: String?): Boolean {
    val combined = listOfNotNull(hint, text, contentDescription).joinToString(" ").lowercase()
    return combined.contains("obbligatorio") || combined.contains("required") || combined.contains("*")
}

private fun isLayoutContainerImpl(className: String): Boolean {
    val lower = className.lowercase()
    return lower.contains("layout") ||
        lower.contains("viewgroup") ||
        lower.contains("constraint") ||
        lower.contains("coordinator") ||
        lower.contains("drawer")
}
