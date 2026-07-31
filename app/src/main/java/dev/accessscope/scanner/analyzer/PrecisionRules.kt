/**
 * Facade pubblica delle regole di precisione.
 */
package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.precision.*

object PrecisionRules {
    fun viewIdShort(snap: NodeSnapshot): String = PrecisionRulesApp.viewIdShort(snap)
    fun estimateViewport(snapshots: List<NodeSnapshot>): Rect = PrecisionRulesApp.estimateViewport(snapshots)
    fun isOffScreenOrMarginalNode(snap: NodeSnapshot, viewport: Rect, packageName: String = ""): Boolean = PrecisionRulesApp.isOffScreenOrMarginalNode(snap, viewport, packageName)
    fun isAnomalousTouchBounds(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isAnomalousTouchBounds(snap)
    fun isFullWidthListRow(snap: NodeSnapshot, screenWidth: Int): Boolean = PrecisionRulesApp.isFullWidthListRow(snap, screenWidth)
    fun isCarouselSelectionRow(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isCarouselSelectionRow(snap)
    fun shouldSkipStructuralNoise(
        snap: NodeSnapshot,
        viewport: Rect,
        screenWidth: Int,
        packageName: String = "",
    ): Boolean = PrecisionRulesApp.shouldSkipStructuralNoise(snap, viewport, screenWidth, packageName)
    fun shouldSkipTopBarIconContrast(snap: NodeSnapshot, all: List<NodeSnapshot>, viewport: Rect): Boolean = PrecisionRulesApp.shouldSkipTopBarIconContrast(snap, all, viewport)
    fun isInlineTextLink(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isInlineTextLink(snap)
    fun isTopBarControl(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isTopBarControl(snap)
    fun isDrawerNavItem(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isDrawerNavItem(snap)
    fun isDrawerScroll(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isDrawerScroll(snap)
    fun isPhantomClickableBounds(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isPhantomClickableBounds(snap)
    fun shouldSkipDrawerNode(snap: NodeSnapshot): Boolean = PrecisionRulesApp.shouldSkipDrawerNode(snap)
    fun isCarouselContentContainer(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean = PrecisionRulesApp.isCarouselContentContainer(snap, all, packageName)
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.isIconInsideLabeledButton(snap, all)
    fun isIconWithLabeledSibling(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.isIconWithLabeledSibling(snap, all)
    fun hasLabeledClickableAncestor(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.hasLabeledClickableAncestor(snap, all)
    fun hasLabeledDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.hasLabeledDescendant(snap, all)
    fun hasLabeledDescendantInScroll(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.hasLabeledDescendantInScroll(snap, all)
    fun isPinPadKey(snap: NodeSnapshot, packageName: String = ""): Boolean = PrecisionRulesApp.isPinPadKey(snap, packageName)
    fun shouldSkipPinPadWhenNotPinScreen(snap: NodeSnapshot, screenTitle: String, packageName: String = ""): Boolean = PrecisionRulesApp.shouldSkipPinPadWhenNotPinScreen(snap, screenTitle, packageName)
    fun shouldSkipSilentDynamicContent(
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
        packageName: String,
    ): Boolean = PrecisionRulesApp.shouldSkipSilentDynamicContent(screenTitle, snapshots, packageName)
    fun isScrollableListScreen(snapshots: List<NodeSnapshot>): Boolean = PrecisionRulesApp.isScrollableListScreen(snapshots)
    fun shouldSkipOverlapBetween(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenWidth: Int = 0,
    ): Boolean = PrecisionRulesApp.shouldSkipOverlapBetween(a, b, all, packageName, screenWidth)
    fun shouldSkipCarouselListItemAnalysis(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String): Boolean = PrecisionRulesApp.shouldSkipCarouselListItemAnalysis(snap, all, packageName)
    fun isMainContentScroll(snap: NodeSnapshot, screenArea: Int, packageName: String = ""): Boolean = PrecisionRulesApp.isMainContentScroll(snap, screenArea, packageName)
    fun isCtaContainer(snap: NodeSnapshot, packageName: String = ""): Boolean = PrecisionRulesApp.isCtaContainer(snap, packageName)
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean = PrecisionRulesApp.shouldSkipContainerLabelCheck(snap, all, packageName)
    fun isWideTapTarget(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isWideTapTarget(snap)
    fun isButtonLikeTapTarget(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isButtonLikeTapTarget(snap)
    fun isLikelyStatusBadge(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isLikelyStatusBadge(snap)
    fun shouldSkipHeadingCheck(snap: NodeSnapshot): Boolean = PrecisionRulesApp.shouldSkipHeadingCheck(snap)
    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean = PrecisionRulesApp.isListFieldLabel(snap, packageName)
    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean = PrecisionRulesApp.isKnownContrastFieldLabel(snap, packageName)
    fun isCurrencyOrAmountText(text: String): Boolean = PrecisionRulesApp.isCurrencyOrAmountText(text)
    fun isKnownListTemplateId(viewId: String?, packageName: String = ""): Boolean = PrecisionRulesApp.isKnownListTemplateId(viewId, packageName)
    fun isEmptyTextSurfaceWithoutContent(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isEmptyTextSurfaceWithoutContent(snap)
    fun shouldSkipContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ): Boolean = PrecisionRulesApp.shouldSkipContrastCheck(snap, all, packageName, screenAreaPx)
    fun shouldSkipUiContrastCheck(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String = "",
        screenAreaPx: Int = 0,
    ): Boolean = PrecisionRulesApp.shouldSkipUiContrastCheck(snap, all, packageName, screenAreaPx)
    fun isTextOverIllustratedBackground(
        snap: NodeSnapshot,
        all: List<NodeSnapshot>,
        screenAreaPx: Int,
    ): Boolean = PrecisionRulesApp.isTextOverIllustratedBackground(snap, all, screenAreaPx)
    fun isMaterialCalendarContext(snapshots: List<NodeSnapshot>): Boolean = PrecisionRulesApp.isMaterialCalendarContext(snapshots)
    fun isMaterialCalendarDayCell(
        snap: NodeSnapshot,
        snapshots: List<NodeSnapshot>,
    ): Boolean = PrecisionRulesApp.isMaterialCalendarDayCell(snap, snapshots)
    fun isMaterialCalendarRelatedNode(
        snap: NodeSnapshot,
        snapshots: List<NodeSnapshot>,
    ): Boolean = PrecisionRulesApp.isMaterialCalendarRelatedNode(snap, snapshots)
    fun hasFocusableOrEditableDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.hasFocusableOrEditableDescendant(snap, all)
    fun isTabStripNode(snap: NodeSnapshot, packageName: String = ""): Boolean = PrecisionRulesApp.isTabStripNode(snap, packageName)
    fun isStructuralScrollOverlap(
        a: NodeSnapshot,
        b: NodeSnapshot,
        all: List<NodeSnapshot>,
        packageName: String,
        screenArea: Int,
    ): Boolean = PrecisionRulesApp.isStructuralScrollOverlap(a, b, all, packageName, screenArea)
    fun isInsideCarouselOrListItem(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean = PrecisionRulesApp.isInsideCarouselOrListItem(snap, all, packageName)
    fun isRecyclerListItem(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.isRecyclerListItem(snap, all)
    fun isScrollContainer(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isScrollContainer(snap)
    fun shouldSkipScrollWithoutLabel(snap: NodeSnapshot, all: List<NodeSnapshot>, screenArea: Int, packageName: String = ""): Boolean = PrecisionRulesApp.shouldSkipScrollWithoutLabel(snap, all, screenArea, packageName)
    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean = PrecisionRulesApp.shouldReportCustomAction(snap, all, packageName)
    fun isDecorative(snap: NodeSnapshot): Boolean = PrecisionRulesApp.isDecorative(snap)
    fun shouldSkipDecorativeLabeledCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.shouldSkipDecorativeLabeledCheck(snap, all)
    fun isLikelyNavigationImage(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean = PrecisionRulesApp.isLikelyNavigationImage(snap, all)
    fun shouldSkipTouchTargetCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean = PrecisionRulesApp.shouldSkipTouchTargetCheck(snap, all, packageName)
    fun isPoorAltText(text: String): Boolean = PrecisionRulesApp.isPoorAltText(text)
    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?): Boolean = PrecisionRulesApp.isRequiredFieldHint(hint, text, contentDescription)
    fun isLayoutContainer(className: String): Boolean = PrecisionRulesApp.isLayoutContainer(className)
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
    ): Boolean = PrecisionRulesApp.shouldSkipTouchSpacingBetween(a, b, all, screenArea)
    fun shouldSkipSmallTextCheck(snap: NodeSnapshot, viewport: Rect = android.graphics.Rect(), packageName: String = ""): Boolean =
        PrecisionRulesApp.shouldSkipSmallTextCheck(snap, viewport, packageName)
}
