package dev.accessscope.scanner.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Matrice di regressione derivata da mps-accessibility-verification.md (Nexi BFF).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MpsVerificationRegressionTest {

  private val packageName = "it.nexi.bff"
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
  fun largeTextImportPositive_skipsContrastOnHome() {
    val importPositive = snap(
      id = "import_positive",
      text = "12.345,67",
      bounds = Rect(100, 200, 400, 350),
      className = "it.utils.AutoResizeTextView",
    )
    val container = snap(
      id = "entrate_home",
      bounds = Rect(50, 150, 500, 400),
      className = "android.widget.LinearLayout",
    )
    val home = snap(id = "card_home", bounds = Rect(0, 0, 1080, 2400))
    val all = listOf(importPositive, container, home, scrollPort())
    assertTrue(PrecisionRules.isHomeChartDecorativeText(importPositive, all, packageName))
  }

  @Test
  fun last30_onHome_isNotSkippedAsDecorative() {
    val last30 = snap(
      id = "last_30",
      text = "ultimi 30 gg",
      bounds = Rect(100, 200, 400, 250),
    )
    val container = snap(id = "entrate_home", bounds = Rect(50, 150, 500, 400))
    val home = snap(id = "card_home", bounds = Rect(0, 0, 1080, 2400))
    val all = listOf(last30, container, home, scrollPort())
    assertFalse(PrecisionRules.isHomeChartDecorativeText(last30, all, packageName))
  }

  @Test
  fun carouselCurrency_skipsContrast() {
    val currency = snap(id = "currency", text = "€", bounds = Rect(10, 10, 40, 40))
    val item = snap(
      id = "content",
      bounds = Rect(0, 0, 900, 200),
      className = "android.widget.RelativeLayout",
    )
    val recycler = snap(
      id = "recycler_effetti",
      bounds = Rect(0, 500, 1080, 900),
      className = "androidx.recyclerview.widget.RecyclerView",
    )
    val all = listOf(currency, item, recycler)
    assertTrue(PrecisionRules.shouldSkipContrastCheck(currency, all, packageName))
  }

  @Test
  fun vopInfo_withCausale_skipsUiContrastOnly() {
    val vop = snap(
      id = "vop_info",
      bounds = Rect(800, 100, 860, 160),
      className = "android.widget.ImageView",
    )
    val causale = snap(id = "causale", text = "Pagamento fornitore", bounds = Rect(100, 80, 900, 180))
    val all = listOf(vop, causale)
    assertTrue(PrecisionRules.shouldSkipUiContrastCheck(vop, all, packageName))
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
    val tabA = snap(id = "tv_tab", text = "DISTINTE (3)", bounds = Rect(0, 800, 540, 900), clickable = true)
    val tabB = snap(id = "tv_tab", text = "BONIFICI (2)", bounds = Rect(540, 800, 1080, 900), clickable = true)
    val card = snap(id = "card_effetti", bounds = Rect(0, 750, 1080, 950))
    val all = listOf(tabA, tabB, card)
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

  @Test
  fun isLargeText_usesKnownIds() {
    val snap = snap(id = "import_positive", bounds = Rect(0, 0, 100, 80))
    assertTrue(WcagContrast.isLargeText(snap, density, AppPrecisionProfiles.largeTextViewIds(packageName)))
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
    viewId = "it.nexi.bff:id/$id",
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
