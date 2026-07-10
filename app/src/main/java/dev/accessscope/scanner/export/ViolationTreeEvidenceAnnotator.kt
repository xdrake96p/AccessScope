/**
 * Evidenza wireframe sintetica per schermate protette (FLAG_SECURE).
 */
package dev.accessscope.scanner.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.accessscope.scanner.data.AccessibilityViolation
import kotlin.math.min

object ViolationTreeEvidenceAnnotator {

    private const val OUTPUT_WIDTH = 560
    private const val OUTPUT_HEIGHT = 400
    private const val CAPTION_HEIGHT = 36f
    private const val STROKE_PX = 3f

    fun annotateWireframe(
        violation: AccessibilityViolation,
        viewport: Rect,
        focusBounds: Rect,
        nearbyBounds: List<Rect> = emptyList(),
    ): Bitmap {
        val output = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.parseColor("#F5F5F5"))

        val scale = minOf(
            (OUTPUT_WIDTH - 24f) / viewport.width().coerceAtLeast(1),
            (OUTPUT_HEIGHT - CAPTION_HEIGHT - 24f) / viewport.height().coerceAtLeast(1),
        )
        val offsetX = (OUTPUT_WIDTH - viewport.width() * scale) / 2f
        val offsetY = 12f

        fun mapRect(src: Rect): RectF = RectF(
            offsetX + (src.left - viewport.left) * scale,
            offsetY + (src.top - viewport.top) * scale,
            offsetX + (src.right - viewport.left) * scale,
            offsetY + (src.bottom - viewport.top) * scale,
        )

        val contextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#BDBDBD")
        }
        nearbyBounds.take(4).forEach { canvas.drawRect(mapRect(it), contextPaint) }

        val severity = violation.type.severity
        val highlight = mapRect(focusBounds)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            val c = ViolationEvidenceAnnotator.severityColor(severity)
            color = Color.argb(40, Color.red(c), Color.green(c), Color.blue(c))
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE_PX
            color = ViolationEvidenceAnnotator.severityColor(severity)
        }
        canvas.drawRect(highlight, fillPaint)
        canvas.drawRect(highlight, strokePaint)

        val caption = buildCaption(violation)
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#616161")
            textSize = 22f
        }
        canvas.drawText(caption, 12f, OUTPUT_HEIGHT - 10f, captionPaint)
        return output
    }

    private fun buildCaption(violation: AccessibilityViolation): String {
        val cls = violation.viewClassName.substringAfterLast('.')
        val id = violation.viewId?.substringAfterLast('/')?.let { "@id/$it" }.orEmpty()
        return buildString {
            append(cls)
            if (id.isNotBlank()) append(" $id")
        }.take(72)
    }
}
