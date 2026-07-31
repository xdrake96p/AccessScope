/**
 * Persistenza JPEG degli screenshot di schermata e crop annotati per sessione di scansione.
 */
package dev.accessscope.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.EvidenceKind
import dev.accessscope.scanner.export.ViolationEvidenceAnnotator
import dev.accessscope.scanner.export.ViolationTreeEvidenceAnnotator
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class ScanEvidenceStore(private val context: Context) {

  private val lastScreenSaveMs = ConcurrentHashMap<String, Long>()

  fun sessionDir(sessionId: String): File =
      File(context.cacheDir, "$ROOT_DIR/$sessionId").apply { mkdirs() }

  fun screenEvidenceId(screenFingerprint: String): String =
      screenFingerprint.replace(Regex("""[^\w.-]"""), "_").take(120)

  /**
   * Salva screenshot schermata con debounce (max 1 ogni 2s per fingerprint).
   *
   * @return path assoluto del file JPEG o null se saltato / errore.
   */
  fun saveScreenScreenshot(
      sessionId: String,
      screenFingerprint: String,
      bitmap: Bitmap,
  ): String? {
    val evidenceId = screenEvidenceId(screenFingerprint)
    val debounceKey = "$sessionId|$evidenceId"
    val now = System.currentTimeMillis()
    val last = lastScreenSaveMs[debounceKey] ?: 0L
    if (now - last < SCREEN_DEBOUNCE_MS) {
      return screenFile(sessionId, evidenceId).takeIf { it.exists() }?.absolutePath
    }
    lastScreenSaveMs[debounceKey] = now
    return saveJpeg(bitmap, screenFile(sessionId, evidenceId), SCREEN_JPEG_QUALITY)
  }

  fun screenFilePath(sessionId: String, screenEvidenceId: String): String? =
      screenFile(sessionId, screenEvidenceId).takeIf { it.exists() }?.absolutePath

  fun saveViolationEvidence(
      sessionId: String,
      dedupeKey: String,
      bitmap: Bitmap,
  ): String? {
    val safeKey = dedupeKey.replace(Regex("""[^\w.-]"""), "_").take(160)
    return saveJpeg(bitmap, violationFile(sessionId, safeKey))
  }

  /**
   * Arricchisce le violazioni con path evidenza e coordinate strutturate.
   */
  fun enrichViolations(
      sessionId: String,
      screenFingerprint: String,
      screenshot: Bitmap,
      violations: List<AccessibilityViolation>,
  ): List<AccessibilityViolation> {
    val evidenceId = screenEvidenceId(screenFingerprint)
    saveScreenScreenshot(sessionId, screenFingerprint, screenshot)
    return violations.map { violation ->
      enrichOne(sessionId, evidenceId, screenshot, violation)
    }
  }

  private fun enrichOne(
      sessionId: String,
      screenEvidenceId: String,
      screenshot: Bitmap,
      violation: AccessibilityViolation,
  ): AccessibilityViolation {
    val rect = ViolationBoundsParser.rect(violation) ?: return violation.copy(
        screenEvidenceId = screenEvidenceId,
    )
    val structured = violation.withStructuredBounds(rect)
    val annotated = ViolationEvidenceAnnotator.annotateCrop(
        screenshot = screenshot,
        bounds = rect,
        severity = violation.type.severity,
    )
    val path = saveViolationEvidence(sessionId, structured.dedupeKey, annotated)
    annotated.recycle()
    return structured.copy(
        screenEvidenceId = screenEvidenceId,
        evidenceImagePath = path,
        evidenceKind = EvidenceKind.SCREENSHOT,
    )
  }

  /**
   * Evidenze wireframe per schermate protette (nessuno screenshot reale disponibile).
   */
  fun enrichViolationsSecure(
      sessionId: String,
      screenFingerprint: String,
      violations: List<AccessibilityViolation>,
      viewport: Rect,
      nearbyBoundsFor: (AccessibilityViolation) -> List<Rect>,
  ): List<AccessibilityViolation> {
    val evidenceId = screenEvidenceId(screenFingerprint)
    return violations.map { violation ->
      enrichOneSecure(sessionId, evidenceId, violation, viewport, nearbyBoundsFor(violation))
    }
  }

  private fun enrichOneSecure(
      sessionId: String,
      screenEvidenceId: String,
      violation: AccessibilityViolation,
      viewport: Rect,
      nearbyBounds: List<Rect>,
  ): AccessibilityViolation {
    val rect = ViolationBoundsParser.rect(violation) ?: return violation.copy(
        screenEvidenceId = screenEvidenceId,
        evidenceKind = EvidenceKind.SYNTHETIC_SECURE,
    )
    val structured = violation.withStructuredBounds(rect)
    val wireframe = ViolationTreeEvidenceAnnotator.annotateWireframe(
        violation = structured,
        viewport = viewport,
        focusBounds = rect,
        nearbyBounds = nearbyBounds,
    )
    val path = saveWireframeEvidence(sessionId, structured.dedupeKey, wireframe)
    wireframe.recycle()
    return structured.copy(
        screenEvidenceId = screenEvidenceId,
        evidenceImagePath = path,
        evidenceKind = EvidenceKind.SYNTHETIC_SECURE,
    )
  }

  fun regenerateWireframeOnDemand(
      sessionId: String,
      violation: AccessibilityViolation,
      viewport: Rect,
      nearbyBounds: List<Rect>,
  ): String? {
    val focus = ViolationBoundsParser.rect(violation) ?: return null
    val wireframe = ViolationTreeEvidenceAnnotator.annotateWireframe(
        violation = violation,
        viewport = viewport,
        focusBounds = focus,
        nearbyBounds = nearbyBounds,
    )
    return saveWireframeEvidence(sessionId, violation.dedupeKey, wireframe).also {
      wireframe.recycle()
    }
  }

  /**
   * Carica lo screenshot di schermata, opzionalmente downsampled.
   *
   * @param maxDimPx Lato massimo desiderato in px; `<= 0` = full-res. Il decode usa
   *   `inSampleSize` (potenze di 2): un full-res 1080×2400 ≈ 10MB in RAM, con
   *   downsampling a ~1080 di lato lungo scende a ~2.5MB — indispensabile per non
   *   accumulare centinaia di MB nel report dinamico multi-schermata.
   */
  fun loadScreenBitmap(sessionId: String, screenEvidenceId: String, maxDimPx: Int = 0): Bitmap? {
    val file = screenFile(sessionId, screenEvidenceId)
    if (!file.exists()) return null
    return runCatching { decodeDownsampled(file.absolutePath, maxDimPx) }.getOrNull()
  }

  private fun decodeDownsampled(path: String, maxDimPx: Int): Bitmap? {
    if (maxDimPx <= 0) return android.graphics.BitmapFactory.decodeFile(path)
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimPx) {
      sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return android.graphics.BitmapFactory.decodeFile(path, opts)
  }

  fun annotateOnDemand(
      sessionId: String,
      violation: AccessibilityViolation,
  ): String? {
    violation.evidenceImagePath?.let { path ->
      if (File(path).exists()) return path
    }
    if (violation.evidenceKind == EvidenceKind.SYNTHETIC_SECURE) {
      val rect = ViolationBoundsParser.rect(violation) ?: return null
      val viewport = expandViewport(rect)
      return regenerateWireframeOnDemand(sessionId, violation, viewport, emptyList())
    }
    val screenId = resolveScreenEvidenceId(violation) ?: return null
    val screenshot = loadScreenBitmap(sessionId, screenId) ?: return null
    return try {
      val rect = ViolationBoundsParser.rect(violation)
      val annotated = when {
        rect != null && isNearFullScreen(rect, screenshot) ->
            ViolationEvidenceAnnotator.annotateFullScreen(
                screenshot, rect, violation.type.severity,
            )
        rect != null ->
            ViolationEvidenceAnnotator.annotateCrop(
                screenshot, rect, violation.type.severity,
            )
        else ->
            ViolationEvidenceAnnotator.annotateFullScreen(
                screenshot, null, violation.type.severity,
            )
      }
      saveViolationEvidence(sessionId, violation.dedupeKey, annotated).also {
        annotated.recycle()
      }
    } finally {
      screenshot.recycle()
    }
  }

  /**
   * Arricchisce violazioni usando uno screenshot schermata già in cache (pass senza nuovo screenshot).
   */
  fun backfillViolations(
      sessionId: String,
      screenFingerprint: String,
      violations: List<AccessibilityViolation>,
  ): List<AccessibilityViolation> {
    val screenId = screenEvidenceId(screenFingerprint)
    val screenshot = loadScreenBitmap(sessionId, screenId) ?: return violations
    return try {
      violations.map { violation ->
        if (!violation.evidenceImagePath.isNullOrBlank() &&
            File(violation.evidenceImagePath!!).exists()
        ) {
          violation
        } else {
          enrichOne(sessionId, screenId, screenshot, violation)
        }
      }
    } finally {
      screenshot.recycle()
    }
  }

  /** ID file schermata da [screenEvidenceId] o [AccessibilityViolation.screenFingerprint]. */
  fun resolveScreenEvidenceId(violation: AccessibilityViolation): String? {
    violation.screenEvidenceId?.let { return it }
    val fingerprint = violation.screenFingerprint ?: return null
    return screenEvidenceId(fingerprint)
  }

  private fun isNearFullScreen(rect: Rect, screenshot: Bitmap): Boolean {
    val area = rect.width().toLong() * rect.height()
    val screenArea = screenshot.width.toLong() * screenshot.height
    return screenArea > 0 && area >= screenArea * 0.85
  }

  fun cleanupSession(sessionId: String) {
    sessionDir(sessionId).deleteRecursively()
    lastScreenSaveMs.keys.removeIf { it.startsWith("$sessionId|") }
  }

  fun cleanupExcept(keepSessionIds: Set<String>) {
    val root = File(context.cacheDir, ROOT_DIR)
    if (!root.exists()) return
    root.listFiles()?.forEach { dir ->
      if (dir.isDirectory && dir.name !in keepSessionIds) {
        dir.deleteRecursively()
      }
    }
  }

  private fun AccessibilityViolation.withStructuredBounds(rect: Rect): AccessibilityViolation =
      copy(
          boundsLeft = rect.left,
          boundsTop = rect.top,
          boundsRight = rect.right,
          boundsBottom = rect.bottom,
      )

  private fun screenFile(sessionId: String, evidenceId: String): File =
      File(sessionDir(sessionId), "screen_$evidenceId.jpg")

  private fun violationFile(sessionId: String, safeKey: String): File =
      File(sessionDir(sessionId), "violation_$safeKey.jpg")

  private fun wireframeFile(sessionId: String, safeKey: String): File =
      File(sessionDir(sessionId), "violation_wireframe_$safeKey.jpg")

  private fun saveWireframeEvidence(sessionId: String, dedupeKey: String, bitmap: Bitmap): String? {
    val safeKey = dedupeKey.replace(Regex("""[^\w.-]"""), "_").take(160)
    return saveJpeg(bitmap, wireframeFile(sessionId, safeKey))
  }

  private fun expandViewport(focus: Rect): Rect {
    val padX = (focus.width() * 1.5f).toInt().coerceAtLeast(120)
    val padY = (focus.height() * 2f).toInt().coerceAtLeast(200)
    return Rect(
        (focus.left - padX).coerceAtLeast(0),
        (focus.top - padY).coerceAtLeast(0),
        focus.right + padX,
        focus.bottom + padY,
    )
  }

  private fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = VIOLATION_JPEG_QUALITY): String? = runCatching {
    file.parentFile?.mkdirs()
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    file.absolutePath
  }.getOrNull()

  companion object {
    private const val ROOT_DIR = "scan_evidence"
    private const val SCREEN_DEBOUNCE_MS = 2_000L
    private const val SCREEN_JPEG_QUALITY = 82
    private const val VIOLATION_JPEG_QUALITY = 90
  }
}
