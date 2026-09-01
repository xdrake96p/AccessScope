/**
 * Cattura screenshot JPEG + albero a11y compatto dopo ogni azione Maestro.
 */
package dev.accessscope.scanner.recorder.capture

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.data.ScreenProtectionReason
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.ActionVisualSnapshot
import dev.accessscope.scanner.util.SecureScreenAssessment
import dev.accessscope.scanner.util.SecureScreenDetector
import dev.accessscope.scanner.util.ScreenshotCapture
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Converte bitmap schermo in JPEG e popola [RecordingVisualBuffer].
 */
object RecordingVisualCapture {

    private const val MAX_WIDTH_PX = 512
    private const val JPEG_QUALITY = 58
    private const val SECURE_MAX_NODES = 120

    /**
     * Callback asincrona per acquisizione screenshot (fornita dal servizio a11y).
     */
    fun interface ScreenshotProvider {
        /** @param onResult Bitmap o null; il caller deve riciclare il bitmap dopo uso. */
        fun capture(onResult: (ScreenshotCapture) -> Unit)
    }

    /**
     * Cattura contesto visivo post-azione e lo registra nel buffer.
     */
    fun captureAfterAction(
        actionIndex: Int,
        action: RecordedAction,
        root: AccessibilityNodeInfo?,
        windowTitle: String?,
        deltaMs: Long,
        screenshotProvider: ScreenshotProvider,
        buffer: RecordingVisualBuffer,
        onDone: () -> Unit = {},
    ) {
        val treeCopy = root?.let { duplicateRoot(it) }
        screenshotProvider.capture { capture ->
            val assessment = if (treeCopy != null) {
                SecureScreenDetector.assess(
                    treeCopy,
                    windowTitle.orEmpty(),
                    action.packageName,
                    capture,
                )
            } else {
                SecureScreenAssessment.NONE
            }
            val maxNodes = if (assessment.useSecureEvidence) SECURE_MAX_NODES else 60
            val tree = CompactTreeExtractor.extract(treeCopy, maxNodes = maxNodes)
            val jpeg = encodeScreenshot(capture)
            capture.bitmap?.recycle()
            val wireframe = if (jpeg == null && tree.isNotEmpty()) {
                SecureStepWireframeRenderer.render(tree, action)
            } else {
                null
            }
            val transcript = StepSemanticTranscriptBuilder.build(
                action = action,
                tree = tree,
                assessment = assessment,
                windowTitle = windowTitle,
            )
            treeCopy?.recycle()
            buffer.put(
                ActionVisualSnapshot(
                    actionIndex = actionIndex,
                    jpegBytes = jpeg,
                    wireframeJpeg = wireframe,
                    treeSummary = tree,
                    windowTitle = windowTitle?.take(120),
                    secureWindow = capture.flagSecure,
                    protectionReason = assessment.reason,
                    semanticTranscript = transcript,
                    deltaMs = deltaMs,
                ),
            )
            onDone()
        }
    }

    /**
     * Registra solo albero a11y (senza screenshot) quando capture non disponibile.
     */
    fun captureTreeOnly(
        actionIndex: Int,
        action: RecordedAction,
        root: AccessibilityNodeInfo?,
        windowTitle: String?,
        deltaMs: Long,
        buffer: RecordingVisualBuffer,
    ) {
        val treeCopy = root?.let { duplicateRoot(it) }
        val assessment = if (treeCopy != null) {
            SecureScreenDetector.assess(treeCopy, windowTitle.orEmpty(), action.packageName, null)
        } else {
            SecureScreenAssessment.NONE
        }
        val maxNodes = if (assessment.useSecureEvidence) SECURE_MAX_NODES else 60
        val tree = CompactTreeExtractor.extract(treeCopy, maxNodes = maxNodes)
        val wireframe = if (tree.isNotEmpty() && assessment.useSecureEvidence) {
            SecureStepWireframeRenderer.render(tree, action)
        } else {
            null
        }
        val transcript = StepSemanticTranscriptBuilder.build(action, tree, assessment, windowTitle)
        treeCopy?.recycle()
        buffer.put(
            ActionVisualSnapshot(
                actionIndex = actionIndex,
                jpegBytes = null,
                wireframeJpeg = wireframe,
                treeSummary = tree,
                windowTitle = windowTitle?.take(120),
                secureWindow = assessment.reason == ScreenProtectionReason.FLAG_SECURE,
                protectionReason = assessment.reason,
                semanticTranscript = transcript,
                deltaMs = deltaMs,
            ),
        )
    }

    private fun encodeScreenshot(capture: ScreenshotCapture): ByteArray? {
        if (capture.flagSecure || capture.screenshotBlocked) return null
        val bitmap = capture.bitmap ?: return null
        return encodeJpeg(bitmap).also { bitmap.recycle() }
    }

    private fun duplicateRoot(root: AccessibilityNodeInfo): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain(root)

    private fun encodeJpeg(source: Bitmap): ByteArray? {
        val scaled = scaleDown(source)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (scaled !== source) scaled.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_WIDTH_PX) return bitmap
        val ratio = MAX_WIDTH_PX.toFloat() / bitmap.width
        val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, MAX_WIDTH_PX, h, true)
    }
}
