/**
 * Disegna evidenze visive (crop annotato o schermata con highlight) per le violazioni.
 */
package dev.accessscope.scanner.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import dev.accessscope.scanner.data.ViolationSeverity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ViolationEvidenceAnnotator {

  /** Lato massimo output: leggibile su mobile e PDF senza file enormi. */
  private const val TARGET_MAX_SIDE = 560

  /** Upscale minimo per icone/aree piccole (evita crop illeggibili). */
  private const val TARGET_MIN_SIDE = 280

  /** Finestra massima attorno all'elemento prima del crop (evita scroll interi). */
  private const val FOCUS_MAX_SIDE = 420

  private const val PADDING_PX = 20
  private const val STROKE_PX = 3f

  /**
   * Ritaglia l'area del problema con bordo colorato per gravità.
   */
  fun annotateCrop(
      screenshot: Bitmap,
      bounds: Rect,
      severity: ViolationSeverity,
  ): Bitmap {
    val focus = focalCropRegion(screenshot, bounds)
    val padded = Rect(
        max(0, focus.left - PADDING_PX),
        max(0, focus.top - PADDING_PX),
        min(screenshot.width, focus.right + PADDING_PX),
        min(screenshot.height, focus.bottom + PADDING_PX),
    )
    val cropW = padded.width().coerceAtLeast(1)
    val cropH = padded.height().coerceAtLeast(1)
    val output = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(screenshot, padded, Rect(0, 0, cropW, cropH), null)
    val localBounds = Rect(
        bounds.left - padded.left,
        bounds.top - padded.top,
        bounds.right - padded.left,
        bounds.bottom - padded.top,
    )
    drawHighlight(canvas, localBounds, severity)
    return normalizeOutput(output)
  }

  /**
   * Disegna highlight su screenshot intero (problemi a livello schermata).
   */
  fun annotateFullScreen(
      screenshot: Bitmap,
      bounds: Rect?,
      severity: ViolationSeverity,
  ): Bitmap {
    if (bounds != null && isNearFullScreen(bounds, screenshot)) {
      val focus = focalCropRegion(screenshot, bounds)
      val working = cropBitmap(screenshot, focus)
      val canvas = Canvas(working)
      val localBounds = Rect(
          bounds.left - focus.left,
          bounds.top - focus.top,
          bounds.right - focus.left,
          bounds.bottom - focus.top,
      )
      drawHighlight(canvas, localBounds, severity)
      return normalizeOutput(working)
    }
    val output = screenshot.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    bounds?.let { drawHighlight(canvas, it, severity) }
    return normalizeOutput(output)
  }

  fun severityColor(severity: ViolationSeverity): Int = when (severity) {
    ViolationSeverity.CRITICAL -> Color.parseColor("#C62828")
    ViolationSeverity.SERIOUS -> Color.parseColor("#E65100")
    ViolationSeverity.MODERATE -> Color.parseColor("#F9A825")
    ViolationSeverity.MINOR -> Color.parseColor("#757575")
  }

  private fun focalCropRegion(screenshot: Bitmap, bounds: Rect): Rect {
    val maxW = min(screenshot.width, FOCUS_MAX_SIDE * 2)
    val maxH = min(screenshot.height, FOCUS_MAX_SIDE * 2)
    if (bounds.width() <= maxW && bounds.height() <= maxH) return bounds

    val padW = (bounds.width() * 0.35f).toInt().coerceIn(PADDING_PX, 80)
    val padH = (bounds.height() * 0.35f).toInt().coerceIn(PADDING_PX, 80)
    val wantW = (bounds.width() + padW * 2).coerceAtMost(maxW)
    val wantH = (bounds.height() + padH * 2).coerceAtMost(maxH)
    val cx = bounds.centerX()
    val cy = bounds.centerY()
    var left = cx - wantW / 2
    var top = cy - wantH / 2
    if (left < 0) left = 0
    if (top < 0) top = 0
    var right = left + wantW
    var bottom = top + wantH
    if (right > screenshot.width) {
      left = (screenshot.width - wantW).coerceAtLeast(0)
      right = screenshot.width
    }
    if (bottom > screenshot.height) {
      top = (screenshot.height - wantH).coerceAtLeast(0)
      bottom = screenshot.height
    }
    return Rect(left, top, right, bottom)
  }

  private fun cropBitmap(source: Bitmap, region: Rect): Bitmap {
    val w = region.width().coerceAtLeast(1)
    val h = region.height().coerceAtLeast(1)
    return Bitmap.createBitmap(source, region.left, region.top, w, h)
  }

  private fun isNearFullScreen(rect: Rect, screenshot: Bitmap): Boolean {
    val area = rect.width().toLong() * rect.height()
    val screenArea = screenshot.width.toLong() * screenshot.height
    return screenArea > 0 && area >= screenArea * 0.75
  }

  private fun drawHighlight(
      canvas: Canvas,
      bounds: Rect,
      severity: ViolationSeverity,
  ) {
    val color = severityColor(severity)
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = STROKE_PX
      this.color = color
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      this.color = Color.argb(36, Color.red(color), Color.green(color), Color.blue(color))
    }
    canvas.drawRect(bounds, fillPaint)
    canvas.drawRect(bounds, strokePaint)
  }

  private fun normalizeOutput(bitmap: Bitmap): Bitmap {
    val maxSide = max(bitmap.width, bitmap.height)
    val minSide = min(bitmap.width, bitmap.height)
    return when {
      maxSide > TARGET_MAX_SIDE -> scaleBitmap(bitmap, TARGET_MAX_SIDE.toFloat() / maxSide)
      minSide < TARGET_MIN_SIDE && maxSide < TARGET_MAX_SIDE -> {
        val upscale = min(
            TARGET_MIN_SIDE.toFloat() / minSide,
            TARGET_MAX_SIDE.toFloat() / maxSide,
        )
        scaleBitmap(bitmap, upscale)
      }
      else -> bitmap
    }
  }

  private fun scaleBitmap(bitmap: Bitmap, scale: Float): Bitmap {
    if (scale == 1f) return bitmap
    val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val matrix = Matrix().apply { setScale(scale, scale) }
    canvas.drawBitmap(bitmap, matrix, paint)
    if (result !== bitmap) bitmap.recycle()
    return result
  }
}
