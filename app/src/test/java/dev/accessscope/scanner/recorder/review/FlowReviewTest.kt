/**
 * Test JVM per revisione flusso Maestro con Gemini Flash.
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.CompactA11yNode
import dev.accessscope.scanner.recorder.model.ActionVisualSnapshot
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordedTransition
import dev.accessscope.scanner.recorder.model.RecordingVisualContext
import dev.accessscope.scanner.recorder.model.TransitionKind
import dev.accessscope.scanner.recorder.quality.ZeroEditReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlowReviewResponseParserTest {

    @Test
    fun parse_validJson_returnsCorrectedActions() {
        val json = """
            {
              "changes": [{"stepIndex":1,"code":"INSERT_WAIT","message":"Aggiunto wait"}],
              "corrected_actions": [
                {"type":"LaunchApp","packageName":"com.app","timestampMs":1},
                {"type":"WaitForAnimation","packageName":"com.app","timestampMs":2},
                {"type":"Tap","packageName":"com.app","text":"OK","timestampMs":3}
              ]
            }
        """.trimIndent()
        val fallback = listOf(RecordedAction.LaunchApp("com.app"))
        val result = FlowReviewResponseParser.parse(json, fallback)
        assertFalse(result.usedFallback)
        assertEquals(3, result.correctedActions.size)
    }
}

@RunWith(RobolectricTestRunner::class)
class FlowReviewValidatorTest {

    @Test
    fun validate_missingTapsFromRaw_fallback() {
        val raw = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", text = "A"),
            RecordedAction.Tap("com.app", text = "B"),
        )
        val draft = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", text = "A"),
        )
        val parsed = FlowReviewResult(
            correctedActions = draft + RecordedAction.WaitForAnimation("com.app"),
            changes = listOf(FlowReviewChange(1, "INSERT_WAIT", "wait")),
            usedFallback = false,
        )
        val validated = FlowReviewValidator.validate(parsed, draft, raw)
        assertTrue(validated.usedFallback)
        assertEquals("missing_taps_from_raw", validated.errorMessage)
    }
}

@RunWith(RobolectricTestRunner::class)
class MaestroFlowReviewPromptBuilderTest {

    @Test
    fun build_includesA6TranscriptAndA0() {
        val request = FlowReviewRequest(
            appId = "com.test.app",
            flowName = "Test",
            rawActions = listOf(
                RecordedAction.LaunchApp("com.test.app"),
                RecordedAction.Tap("com.test.app", text = "Continua"),
            ),
            optimizedActions = listOf(
                RecordedAction.LaunchApp("com.test.app"),
                RecordedAction.Tap("com.test.app", text = "Continua"),
            ),
            yamlDraft = "appId: com.test.app",
            telemetry = null,
            visualContext = RecordingVisualContext(
                snapshots = listOf(
                    ActionVisualSnapshot(
                        actionIndex = 1,
                        wireframeJpeg = byteArrayOf(1, 2, 3),
                        semanticTranscript = "step tap Continua",
                        treeSummary = listOf(
                            CompactA11yNode(viewId = "btn_ok", text = "Continua", clickable = true),
                        ),
                    ),
                ),
            ),
        )
        val parts = MaestroFlowReviewPromptBuilder.build(request)
        assertTrue(parts.text.contains("[A0]"))
        assertTrue(parts.text.contains("[A6]"))
        assertTrue(parts.text.contains("step tap Continua"))
        assertEquals(listOf(1), parts.imageStepIndices)
    }
}

class FlowReviewChunkPlannerTest {

    @Test
    fun plan_longFlow_multipleChunks() {
        val chunks = FlowReviewChunkPlanner.plan(50)
        assertTrue(chunks.size >= 2)
        assertEquals(0, chunks.first().fromActionIndex)
    }
}

class FlowReviewRawRestorerTest {

    @Test
    fun restore_reinsertsMissingTap() {
        val raw = listOf(
            RecordedAction.Tap("com.app", text = "A"),
            RecordedAction.Tap("com.app", text = "B"),
        )
        val optimized = listOf(RecordedAction.Tap("com.app", text = "A"))
        val restored = FlowReviewRawRestorer.restore(raw, optimized)
        assertEquals(2, restored.filterIsInstance<RecordedAction.Tap>().size)
    }
}

class FlowYamlReconcilerTest {

    @Test
    fun reconcile_geminiWinsWhenHigherScore() {
        val raw = listOf(
            RecordedAction.Tap("com.app", text = "A"),
            RecordedAction.WaitForAnimation("com.app"),
        )
        val app = listOf(RecordedAction.Tap("com.app", text = "A"))
        val gemini = raw
        val result = FlowYamlReconciler.reconcile(
            raw = raw,
            appActions = app,
            geminiActions = gemini,
            appReport = ZeroEditReport(emptyList(), app),
            geminiReport = ZeroEditReport(emptyList(), gemini),
            geminiUsable = true,
        )
        assertEquals(PresentedYamlSource.GEMINI, result.presentedSource)
    }
}

class FlowReviewDiffAnalyzerTest {

    @Test
    fun analyze_flagsLongTransitions() {
        val raw = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", text = "A", timestampMs = 0),
            RecordedAction.Tap("com.app", text = "B", timestampMs = 3_000),
        )
        val telemetry = FlowTelemetry(
            transitions = listOf(
                RecordedTransition(0, 1, 100, "a", "b", TransitionKind.SameScreen),
                RecordedTransition(1, 2, 3_000, "b", "c", TransitionKind.ScreenTransition),
            ),
        )
        val report = FlowReviewDiffAnalyzer.analyze(raw, raw, telemetry, null)
        assertTrue(report.longTransitions.contains(1))
        assertTrue(report.lostStepsSummary.isNotBlank())
    }
}

class GeminiApiRetryPolicyTest {

    @Test
    fun isRetryable_highDemandMessage() {
        assertTrue(GeminiApiRetryPolicy.isRetryable(503, "This model is currently experiencing high demand"))
    }

    @Test
    fun isRetryable_http429() {
        assertTrue(GeminiApiRetryPolicy.isRetryable(429, null))
    }

    @Test
    fun isModelUnavailable_404AndLegacyMessage() {
        assertTrue(GeminiApiRetryPolicy.isModelUnavailable(404, "not found"))
        assertTrue(
            GeminiApiRetryPolicy.isModelUnavailable(
                404,
                "models/gemini-2.5-flash is no longer available to new users",
            ),
        )
    }

    @Test
    fun backoff_exponential() {
        assertEquals(2_000L, GeminiApiRetryPolicy.backoffMs(1))
        assertEquals(4_000L, GeminiApiRetryPolicy.backoffMs(2))
        assertEquals(8_000L, GeminiApiRetryPolicy.backoffMs(3))
    }
}

@RunWith(RobolectricTestRunner::class)
class FlowReviewApplierTest {

    @Test
    fun apply_validGemini_usesCorrectedActions() {
        val optimized = listOf(RecordedAction.Tap("com.app", text = "A"))
        val corrected = listOf(
            RecordedAction.Tap("com.app", text = "A"),
            RecordedAction.WaitForAnimation("com.app"),
        )
        val result = FlowReviewResult(
            correctedActions = corrected,
            usedFallback = false,
            changes = listOf(FlowReviewChange(0, "INSERT_WAIT", "wait")),
            source = FlowReviewSource.GEMINI,
        )
        assertEquals(corrected, FlowReviewApplier.apply(optimized, result))
    }
}
