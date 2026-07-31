package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import dev.accessscope.scanner.analyzer.contrast.WcagContrastMath
import dev.accessscope.scanner.analyzer.contrast.WcagContrastSampling
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressione generica per il calcolo di contrasto WCAG e per lo skip di overlap strutturali
 * (contenuto/scroll, tab strip), indipendente da qualunque app specifica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WcagContrastRegressionTest {

  private val packageName = "com.example.app"
  private val density = 3f

  @Test
  fun compositeOverWhite_greenTint_matchesExpectedRatio() {
    val tinted = Color.parseColor("#1A058943")
    val effective = WcagContrast.compositeOverWhite(tinted)
    val fg = Color.parseColor("#058943")
    val ratio = WcagContrast.contrastRatio(fg, effective)
    assertTrue("Composited bg ratio ~3.94:1", ratio in 3.7..4.2)
  }

  @Test
  fun semiTransparentDarkText_onDarkBackground_compositesOverRealBackground() {
    // Bug: il foreground semi-trasparente veniva sempre compositato su bianco fisso, anche su
    // sfondi scuri — un testo scuro semi-trasparente su sfondo scuro (basso contrasto reale)
    // risultava erroneamente ad alto contrasto perché "schiarito" dal bianco fittizio.
    val darkBackground = Color.rgb(32, 32, 32)
    val semiTransparentDarkText = Color.argb(128, 51, 51, 51)

    val resolvedBg = WcagContrastSampling.resolveEffectiveBackground(darkBackground)
    val resolvedFg = WcagContrastSampling.resolveEffectiveForeground(semiTransparentDarkText, resolvedBg)
    val ratio = WcagContrastMath.contrastRatio(resolvedFg, resolvedBg)

    assertTrue("Testo scuro semi-trasparente su sfondo scuro deve restare basso contrasto, era $ratio", ratio < 2.0)
  }

  @Test
  fun darkTextOnWhite_highContrast() {
    val ratio = WcagContrast.contrastRatio(Color.parseColor("#333333"), Color.WHITE)
    assertTrue(ratio >= 12.0)
  }

  @Test
  fun whiteOnBrandButton_highContrast() {
    val ratio = WcagContrast.contrastRatio(Color.WHITE, Color.parseColor("#99042F"))
    assertTrue(ratio >= 8.0)
  }

  @Test
  fun contentAndScroll_overlapSkipped() {
    val content = snap(
      id = "content",
      bounds = Rect(0, 0, 1080, 2400),
      className = "android.widget.RelativeLayout",
      clickable = true,
    )
    val scroll = scrollPort(clickable = true)
    val all = listOf(content, scroll)
    assertTrue(
      PrecisionRules.shouldSkipOverlapBetween(content, scroll, all, packageName, 1080),
    )
  }

  @Test
  fun tabStrip_overlapSkipped() {
    val tabA = snap(id = "tv_tab", text = "TAB UNO", bounds = Rect(0, 800, 540, 900), clickable = true)
    val tabB = snap(id = "tv_tab", text = "TAB DUE", bounds = Rect(540, 800, 1080, 900), clickable = true)
    val all = listOf(tabA, tabB)
    assertTrue(PrecisionRules.shouldSkipOverlapBetween(tabA, tabB, all, packageName, 1080))
  }

  @Test
  fun measureTextContrast_onSyntheticBitmap() {
    val bitmap = solidBitmap(400, 200, Color.WHITE)
    fillRect(bitmap, Rect(50, 50, 350, 150), Color.parseColor("#333333"))
    val bounds = Rect(60, 60, 340, 140)
    val result = WcagContrast.measureTextContrast(bitmap, bounds, isLargeText = false)
    requireNotNull(result)
    assertTrue(result.ratio >= 10.0)
  }

  private fun scrollPort(clickable: Boolean = false) = snap(
    id = "scrollview_port",
    bounds = Rect(0, 0, 1080, 2200),
    className = "androidx.core.widget.NestedScrollView",
    scrollable = true,
    clickable = clickable,
  )

  private fun snap(
    id: String,
    text: String? = null,
    bounds: Rect = Rect(0, 0, 100, 100),
    className: String = "android.widget.TextView",
    clickable: Boolean = false,
    scrollable: Boolean = false,
  ) = NodeSnapshot(
    className = className,
    bounds = bounds,
    viewId = "$packageName:id/$id",
    text = text,
    contentDescription = null,
    hintText = null,
    tooltipText = null,
    isClickable = clickable,
    isLongClickable = false,
    isFocusable = clickable,
    isEditable = false,
    isCheckable = false,
    isChecked = false,
    isScrollable = scrollable,
    isEnabled = true,
    isPassword = false,
    isHeading = false,
    headingLevel = 0,
    hasLabeledBy = false,
    hasLabelFor = false,
    errorText = null,
    stateDescription = null,
    isExpanded = null,
    collectionRow = -1,
    collectionColumn = -1,
    childCount = 0,
    isAccessibilityExcluded = false,
    isLikelyDecorative = false,
    traversalIndex = 0,
    rangeCurrent = null,
    rangeMin = null,
    rangeMax = null,
    unlabeledActionCount = 0,
    minTextHeightPx = 36,
    minTouchTargetPx = 144,
    textSizeSp = if (className.contains("TextView")) bounds.height() / density else null,
  )

  private fun solidBitmap(w: Int, h: Int, color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(color)
    return bmp
  }

  private fun fillRect(bitmap: Bitmap, rect: Rect, color: Int) {
    for (y in rect.top until rect.bottom) {
      for (x in rect.left until rect.right) {
        bitmap.setPixel(x, y, color)
      }
    }
  }
}
