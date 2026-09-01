/**
 * Wireframe JPEG sintetico da albero a11y per step con FLAG_SECURE / PIN.
 */
package dev.accessscope.scanner.recorder.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.CompactA11yNode
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Render bounds nodi a11y in JPEG per multimodal Gemini (no screenshot reale).
 */
object SecureStepWireframeRenderer {

    private const val OUTPUT_WIDTH = 480
    private const val OUTPUT_HEIGHT = 360
    private const val JPEG_QUALITY = 72

    /**
     * @param tree Nodi schermata.
     * @param action Azione corrente (evidenzia target se match bounds/id).
     * @return JPEG o null se tree vuoto.
     */
    fun render(
        tree: List<CompactA11yNode>,
        action: RecordedAction,
    ): ByteArray? {
        if (tree.isEmpty()) return null
        val viewport = computeViewport(tree) ?: return null
        val bitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F0F0F0"))
        val scale = min(
            (OUTPUT_WIDTH - 24f) / viewport.width().coerceAtLeast(1),
            (OUTPUT_HEIGHT - 40f) / viewport.height().coerceAtLeast(1),
        )
        val offsetX = (OUTPUT_WIDTH - viewport.width() * scale) / 2f
        val offsetY = 12f
        fun mapRect(bounds: List<Int>): RectF? {
            if (bounds.size < 4) return null
            val r = Rect(bounds[0], bounds[1], bounds[2], bounds[3])
            return RectF(
                offsetX + (r.left - viewport.left) * scale,
                offsetY + (r.top - viewport.top) * scale,
                offsetX + (r.right - viewport.left) * scale,
                offsetY + (r.bottom - viewport.top) * scale,
            )
        }
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#9E9E9E")
        }
        val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#1976D2")
        }
        tree.forEach { node ->
            val rect = mapRect(node.boundsPx) ?: return@forEach
            val isFocus = isFocusNode(node, action)
            canvas.drawRect(rect, if (isFocus) focusPaint else nodePaint)
        }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#616161")
            textSize = 20f
        }
        canvas.drawText("Wireframe sintetico (schermata protetta)", 12f, OUTPUT_HEIGHT - 8f, caption)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    }

    private fun computeViewport(tree: List<CompactA11yNode>): Rect? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        tree.forEach { node ->
            if (node.boundsPx.size < 4) return@forEach
            left = min(left, node.boundsPx[0])
            top = min(top, node.boundsPx[1])
            right = max(right, node.boundsPx[2])
            bottom = max(bottom, node.boundsPx[3])
        }
        if (left >= right || top >= bottom) return null
        return Rect(left, top, right, bottom)
    }

    private fun isFocusNode(node: CompactA11yNode, action: RecordedAction): Boolean = when (action) {
        is RecordedAction.Tap ->
            (!action.viewId.isNullOrBlank() && action.viewId == node.viewId) ||
                (!action.text.isNullOrBlank() && action.text == node.text) ||
                (!action.contentDescription.isNullOrBlank() && action.contentDescription == node.contentDescription)
        is RecordedAction.InputText ->
            !action.viewId.isNullOrBlank() && action.viewId == node.viewId
        else -> false
    }
}
