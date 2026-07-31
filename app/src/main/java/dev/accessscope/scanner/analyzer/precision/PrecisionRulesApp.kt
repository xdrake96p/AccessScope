/**
 * Aggregatore interno delle regole di precisione per dominio.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot

internal object PrecisionRulesApp {
    fun viewIdShort(snap: NodeSnapshot) = PrecisionGeometry.viewIdShort(snap)
    fun estimateViewport(snapshots: List<NodeSnapshot>) = PrecisionGeometry.estimateViewport(snapshots)
    fun isOffScreenOrMarginalNode(snap: NodeSnapshot, viewport: Rect, packageName: String = "") = PrecisionGeometry.isOffScreenOrMarginalNode(snap, viewport, packageName)
    fun isAnomalousTouchBounds(snap: NodeSnapshot) = PrecisionGeometry.isAnomalousTouchBounds(snap)
    fun isFullWidthListRow(snap: NodeSnapshot, screenWidth: Int) = PrecisionStructural.isFullWidthListRow(snap, screenWidth)
    fun isCarouselSelectionRow(snap: NodeSnapshot) = PrecisionStructural.isCarouselSelectionRow(snap)
    fun shouldSkipStructuralNoise(
        snap: NodeSnapshot,
        viewport: Rect,
        screenWidth: Int,
        packageName: String = "",
    ) = PrecisionStructural.shouldSkipStructuralNoise(snap, viewport, screenWidth, packageName)
    fun shouldSkipTopBarIconContrast(snap: NodeSnapshot, all: List<NodeSnapshot>, viewport: Rect) = PrecisionNavigation.shouldSkipTopBarIconContrast(snap, all, viewport)
    fun isInlineTextLink(snap: NodeSnapshot) = PrecisionNavigation.isInlineTextLink(snap)
    fun isTopBarControl(snap: NodeSnapshot) = PrecisionNavigation.isTopBarControl(snap)
    fun isDrawerNavItem(snap: NodeSnapshot) = PrecisionNavigation.isDrawerNavItem(snap)
    fun isDrawerScroll(snap: NodeSnapshot) = PrecisionNavigation.isDrawerScroll(snap)
    fun isPhantomClickableBounds(snap: NodeSnapshot) = PrecisionNavigation.isPhantomClickableBounds(snap)
    fun shouldSkipDrawerNode(snap: NodeSnapshot) = PrecisionNavigation.shouldSkipDrawerNode(snap)
    fun isCarouselContentContainer(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String) = PrecisionNavigation.isCarouselContentContainer(snap, all, packageName)
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.isIconInsideLabeledButton(snap, all)
    fun isIconWithLabeledSibling(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.isIconWithLabeledSibling(snap, all)
    fun hasLabeledClickableAncestor(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.hasLabeledClickableAncestor(snap, all)
    fun hasLabeledDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.hasLabeledDescendant(snap, all)
    fun hasLabeledDescendantInScroll(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.hasLabeledDescendantInScroll(snap, all)
    fun isPinPadKey(snap: NodeSnapshot, packageName: String = "") = PrecisionHome.isPinPadKey(snap, packageName)
    fun shouldSkipPinPadWhenNotPinScreen(snap: NodeSnapshot, screenTitle: String, packageName: String = "") = PrecisionHome.shouldSkipPinPadWhenNotPinScreen(snap, screenTitle, packageName)
    fun shouldSkipSilentDynamicContent(
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
        packageName: String,
    ) = PrecisionHome.shouldSkipSilentDynamicContent(screenTitle, snapshots, packageName)
    fun isScrollableListScreen(snapshots: List<NodeSnapshot>) = PrecisionStructural.isScrollableListScreen(snapshots)
    fun shouldSkipOverlapBetween(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenWidth: Int = 0,
    ) = PrecisionStructural.shouldSkipOverlapBetween(a, b, all, packageName, screenWidth)
    fun shouldSkipCarouselListItemAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String) = PrecisionStructural.shouldSkipCarouselListItemAnalysis(snap, all, packageName)
    fun isMainContentScroll(snap: NodeSnapshot, screenArea: Int, packageName: String = "") = PrecisionStructural.isMainContentScroll(snap, screenArea, packageName)
    fun isCtaContainer(snap: NodeSnapshot, packageName: String = "") = PrecisionHome.isCtaContainer(snap, packageName)
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = "") = PrecisionLabels.shouldSkipContainerLabelCheck(snap, all, packageName)
    fun isWideTapTarget(snap: NodeSnapshot) = PrecisionTouch.isWideTapTarget(snap)
    fun isButtonLikeTapTarget(snap: NodeSnapshot) = PrecisionTouch.isButtonLikeTapTarget(snap)
    fun isLikelyStatusBadge(snap: NodeSnapshot) = PrecisionTouch.isLikelyStatusBadge(snap)
    fun shouldSkipHeadingCheck(snap: NodeSnapshot) = PrecisionLabels.shouldSkipHeadingCheck(snap)
    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = "") = PrecisionLabels.isListFieldLabel(snap, packageName)
    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = "") = PrecisionLabels.isKnownContrastFieldLabel(snap, packageName)
    fun isCurrencyOrAmountText(text: String) = PrecisionLabels.isCurrencyOrAmountText(text)
    fun isKnownListTemplateId(viewId: String?, packageName: String = "") = PrecisionLabels.isKnownListTemplateId(viewId, packageName)
    fun isEmptyTextSurfaceWithoutContent(snap: NodeSnapshot) = PrecisionContrast.isEmptyTextSurfaceWithoutContent(snap)
    fun shouldSkipContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ) = PrecisionContrast.shouldSkipContrastCheck(snap, all, packageName, screenAreaPx)
    fun shouldSkipUiContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ) = PrecisionContrast.shouldSkipUiContrastCheck(snap, all, packageName, screenAreaPx)
    fun isTextOverIllustratedBackground(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        screenAreaPx: Int,
    ) = PrecisionContrast.isTextOverIllustratedBackground(snap, all, screenAreaPx)
    fun isMaterialCalendarContext(snapshots: List<NodeSnapshot>) = PrecisionContrast.isMaterialCalendarContext(snapshots)
    fun isMaterialCalendarDayCell(
        snap: NodeSnapshot,
        snapshots: List<NodeSnapshot>,
    ) = PrecisionContrast.isMaterialCalendarDayCell(snap, snapshots)
    fun isMaterialCalendarRelatedNode(
        snap: NodeSnapshot,
        snapshots: List<NodeSnapshot>,
    ) = PrecisionContrast.isMaterialCalendarRelatedNode(snap, snapshots)
    fun hasFocusableOrEditableDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionContrast.hasFocusableOrEditableDescendant(snap, all)
    fun isTabStripNode(snap: NodeSnapshot, packageName: String = "") = PrecisionNavigation.isTabStripNode(snap, packageName)
    fun isStructuralScrollOverlap(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenArea: Int,
    ) = PrecisionStructural.isStructuralScrollOverlap(a, b, all, packageName, screenArea)
    fun isInsideCarouselOrListItem(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = "") = PrecisionStructural.isInsideCarouselOrListItem(snap, all, packageName)
    fun isRecyclerListItem(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionStructural.isRecyclerListItem(snap, all)
    fun isScrollContainer(snap: NodeSnapshot) = PrecisionStructural.isScrollContainer(snap)
    fun shouldSkipScrollWithoutLabel(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int, packageName: String = "") = PrecisionStructural.shouldSkipScrollWithoutLabel(snap, all, screenArea, packageName)
    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = "") = PrecisionLabels.shouldReportCustomAction(snap, all, packageName)
    fun isDecorative(snap: NodeSnapshot) = PrecisionLabels.isDecorative(snap)
    fun shouldSkipDecorativeLabeledCheck(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.shouldSkipDecorativeLabeledCheck(snap, all)
    fun isLikelyNavigationImage(snap: NodeSnapshot, all: List<NodeSnapshot>) = PrecisionLabels.isLikelyNavigationImage(snap, all)
    fun shouldSkipTouchTargetCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = "") = PrecisionTouch.shouldSkipTouchTargetCheck(snap, all, packageName)
    fun isPoorAltText(text: String) = PrecisionLabels.isPoorAltText(text)
    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?) = PrecisionLabels.isRequiredFieldHint(hint, text, contentDescription)
    fun isLayoutContainer(className: String) = PrecisionLabels.isLayoutContainer(className)
    fun isMapSurface(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isMapSurface(snap)
    fun isMediaPlayerSurface(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isMediaPlayerSurface(snap)
    fun isLottieAnimation(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isLottieAnimation(snap)
    fun isSkeletonPlaceholder(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isSkeletonPlaceholder(snap)
    fun isComposeHost(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isComposeHost(snap)
    fun shouldSkipComposeContrast(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.shouldSkipComposeContrast(snap)
    fun shouldSkipComposeTouch(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.shouldSkipComposeTouch(snap)
    fun findModalOverlayBounds(all: List<NodeSnapshot>): Rect? = PrecisionRulesPlatform.findModalOverlayBounds(all)
    fun isObscuredByModalOverlay(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesPlatform.isObscuredByModalOverlay(snap, all)
    fun isInsideMapOrMediaSurface(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesPlatform.isInsideMapOrMediaSurface(snap, all)
    fun isInsideWebView(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesPlatform.isInsideWebView(snap, all)
    fun isSemanticClickTarget(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isSemanticClickTarget(snap)
    fun isClickableLayoutShell(snap: NodeSnapshot): Boolean = PrecisionRulesPlatform.isClickableLayoutShell(snap)
    fun isEmptyClickableHitArea(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        PrecisionRulesPlatform.isEmptyClickableHitArea(snap, all)
    fun isLayoutShellOverlap(a: NodeSnapshot, b: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesPlatform.isLayoutShellOverlap(a, b, all)
    fun isInsideDenseScrollGrid(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int): Boolean = PrecisionRulesPlatform.isInsideDenseScrollGrid(snap, all, screenArea)
    fun shouldSkipPlatformNoiseAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean = PrecisionRulesPlatform.shouldSkipPlatformNoiseAnalysis(snap, all, packageName)
    fun shouldReportWebViewBarrier(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesPlatform.shouldReportWebViewBarrier(snap, all)
    fun shouldSkipTouchSpacingBetween(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot> = emptyList(),
        screenArea: Int = 0,
    ) = PrecisionTouch.shouldSkipTouchSpacingBetween(a, b, all, screenArea)
    fun shouldSkipSmallTextCheck(snap: NodeSnapshot, viewport: Rect = android.graphics.Rect(), packageName: String = "") = PrecisionTouch.shouldSkipSmallTextCheck(snap, viewport, packageName)
}
