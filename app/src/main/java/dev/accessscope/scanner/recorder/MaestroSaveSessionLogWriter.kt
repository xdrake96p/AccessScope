/**
 * Log di sessione per ogni salvataggio flusso Maestro — diagnostica temporanea post-REC.
 *
 * Scrive `{flowId}.session.log` accanto a YAML/review.json per analisi offline.
 */
package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.RecordingVisualContext
import dev.accessscope.scanner.recorder.quality.ZeroEditReport
import dev.accessscope.scanner.recorder.review.FlowReviewResult
import dev.accessscope.scanner.recorder.review.YamlReconcileResult
import dev.accessscope.scanner.util.AppFileLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Snapshot strutturato del save Maestro per persistenza su disco.
 */
data class MaestroSaveSessionSnapshot(
    val flowId: String,
    val flowName: String,
    val appId: String,
    val appLabel: String,
    val sessionStartedMs: Long,
    val sessionEndedMs: Long = System.currentTimeMillis(),
    val rawActions: List<RecordedAction>,
    val optimizedActions: List<RecordedAction>,
    val reviewInputActions: List<RecordedAction>,
    val appActions: List<RecordedAction>,
    val geminiActions: List<RecordedAction>,
    val presentedActions: List<RecordedAction>,
    val visualContext: RecordingVisualContext?,
    val reviewResult: FlowReviewResult?,
    val reconcile: YamlReconcileResult?,
    val zeroEditReport: ZeroEditReport,
    val optimize: Boolean,
)

/**
 * Scrive log testuale leggibile per analisi post-creazione flusso.
 */
object MaestroSaveSessionLogWriter {

    /** Abilita scrittura `{id}.session.log` (disabilitare quando non serve più). */
    const val ENABLED = true

    private val MAESTRO_TAGS = setOf(
        "FlowStore",
        "GeminiReview",
        "RecordingSession",
        "AccessScopeApp",
        "RecordingOverlay",
        "ActionRecorder",
    )

    /**
     * Persiste il log di sessione accanto agli altri artefatti del flusso.
     *
     * @param flowsRoot Directory `files/flows`.
     * @param snapshot Dati raccolti durante [FlowStore.saveFlow].
     */
    fun write(flowsRoot: File, snapshot: MaestroSaveSessionSnapshot) {
        if (!ENABLED) return
        val out = File(flowsRoot, "${snapshot.flowId}.session.log")
        val durationMs = snapshot.sessionEndedMs - snapshot.sessionStartedMs
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ITALY)
            .format(Date(snapshot.sessionEndedMs))
        val sb = StringBuilder()
        sb.appendLine("=== Maestro Save Session ===")
        sb.appendLine("timestamp=$ts")
        sb.appendLine("flowId=${snapshot.flowId}")
        sb.appendLine("flowName=${snapshot.flowName}")
        sb.appendLine("appId=${snapshot.appId}")
        sb.appendLine("appLabel=${snapshot.appLabel}")
        sb.appendLine("optimize=${snapshot.optimize}")
        sb.appendLine("durationMs=$durationMs")
        sb.appendLine()

        sb.appendLine("--- Action counts ---")
        sb.appendLine("raw ${actionSummary(snapshot.rawActions)}")
        sb.appendLine("optimized ${actionSummary(snapshot.optimizedActions)}")
        sb.appendLine("reviewInput ${actionSummary(snapshot.reviewInputActions)}")
        sb.appendLine("app ${actionSummary(snapshot.appActions)}")
        sb.appendLine("gemini ${actionSummary(snapshot.geminiActions)}")
        sb.appendLine("presented ${actionSummary(snapshot.presentedActions)}")
        snapshot.reconcile?.let { r ->
            sb.appendLine("presentedSource=${r.presentedSource.name} reason=${r.reason} appScore=${r.appScore} geminiScore=${r.geminiScore} mergedFix=${r.mergedFixCount}")
        }
        sb.appendLine()

        snapshot.visualContext?.let { visual ->
            val snaps = visual.snapshots
            val jpeg = snaps.count { it.jpegBytes != null }
            val wire = snaps.count { it.wireframeJpeg != null }
            val secure = snaps.count { it.secureWindow }
            sb.appendLine("--- Visual capture ---")
            sb.appendLine("snapshots=${snaps.size} jpeg=$jpeg wireframe=$wire secure=$secure")
            snaps.filter { it.secureWindow || it.wireframeJpeg != null }.forEach { s ->
                sb.appendLine(
                    "  step=${s.actionIndex} secure=${s.secureWindow} reason=${s.protectionReason.name} " +
                        "transcript=${s.semanticTranscript.take(120).replace('\n', ' ')}",
                )
            }
            sb.appendLine()
        }

        snapshot.reviewResult?.let { review ->
            sb.appendLine("--- Gemini review ---")
            sb.appendLine("usedFallback=${review.usedFallback}")
            sb.appendLine("source=${review.source.name}")
            sb.appendLine("model=${review.modelUsed.orEmpty()}")
            sb.appendLine("apiCalls=${review.apiCalls} chunks=${review.chunkCount}")
            sb.appendLine("imagesSent=${review.imagesSent} estTokens=${review.estimatedInputTokens}")
            review.errorMessage?.let { sb.appendLine("error=$it") }
            sb.appendLine("changes=${review.changes.size}")
            review.changes.forEach { c ->
                sb.appendLine("  [${c.stepIndex}] ${c.code}: ${c.message}")
            }
            sb.appendLine()
        } ?: sb.appendLine("--- Gemini review --- skipped (no reviewer or optimize=false)\n")

        sb.appendLine("--- ZeroEdit ---")
        sb.appendLine("errors=${snapshot.zeroEditReport.errorCount} warnings=${snapshot.zeroEditReport.warningCount}")
        snapshot.zeroEditReport.issues.take(20).forEach { issue ->
            sb.appendLine("  [${issue.stepIndex}] ${issue.severity.name}/${issue.code}: ${issue.message}")
        }
        if (snapshot.zeroEditReport.issues.size > 20) {
            sb.appendLine("  ... +${snapshot.zeroEditReport.issues.size - 20} issues")
        }
        sb.appendLine()

        sb.appendLine("--- Live log (Maestro tags, since session start) ---")
        AppFileLogger.entriesSince(snapshot.sessionStartedMs, MAESTRO_TAGS).forEach { entry ->
            sb.appendLine(entry.formatLine())
        }
        sb.appendLine()
        sb.appendLine("=== end ===")

        runCatching {
            out.writeText(sb.toString(), Charsets.UTF_8)
            AppFileLogger.info("FlowStore", "session_log id=${snapshot.flowId} path=${out.name} bytes=${out.length()}")
        }.onFailure {
            AppFileLogger.error("FlowStore", "session_log_fail id=${snapshot.flowId} ${it.message}")
        }
    }

    private fun actionSummary(actions: List<RecordedAction>): String {
        val taps = actions.count { it is RecordedAction.Tap }
        val inputs = actions.count { it is RecordedAction.InputText }
        val waits = actions.count { it is RecordedAction.Wait || it is RecordedAction.WaitForAnimation }
        val scrolls = actions.count { it is RecordedAction.Scroll || it is RecordedAction.ScrollUntilVisible }
        return "total=${actions.size} tap=$taps input=$inputs wait=$waits scroll=$scrolls"
    }
}
