package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.PlayExecutionReport
import dev.accessscope.scanner.recorder.model.PlayRunKind
import dev.accessscope.scanner.recorder.model.PlayStepResult
import dev.accessscope.scanner.recorder.model.PlayStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReportFormatterTest {

    @Test
    fun formatFull_includesStepsAndOutcome() {
        val report = PlayExecutionReport(
            runId = "abc12345",
            flowId = "flow1",
            flowName = "Login Nexi",
            appId = "it.nexi.bff",
            appLabel = "Nexi",
            kind = PlayRunKind.PLAY,
            startedAtMs = 1_000L,
            finishedAtMs = 5_000L,
            clearState = true,
            totalSteps = 2,
            passedSteps = 1,
            failedSteps = 1,
            skippedOptionalSteps = 0,
            success = false,
            errorMessage = "Step 2: Tap non trovato",
            steps = listOf(
                PlayStepResult(0, "tapOn id=login", "Tap", PlayStepStatus.PASSED, "\"Accedi\""),
                PlayStepResult(1, "tapOn id=submit", "Tap", PlayStepStatus.FAILED, error = "Tap non trovato"),
            ),
        )
        val text = PlayReportFormatter.formatFull(report)
        assertTrue(text.contains("Login Nexi"))
        assertTrue(text.contains("ESITO:      KO"))
        assertTrue(text.contains("tapOn id=login"))
        assertTrue(text.contains("[KO ]"))
        assertTrue(text.contains("cold launch"))
    }
}

class PlayReportCodecTest {

    @Test
    fun roundTrip_preservesReport() {
        val original = PlayExecutionReport(
            runId = "r1",
            flowId = "f1",
            flowName = "Test",
            appId = "com.app",
            appLabel = "App",
            kind = PlayRunKind.VALIDATE,
            startedAtMs = 100L,
            finishedAtMs = 200L,
            totalSteps = 1,
            passedSteps = 1,
            failedSteps = 0,
            skippedOptionalSteps = 0,
            success = true,
            steps = listOf(
                PlayStepResult(0, "launchApp", "LaunchApp", PlayStepStatus.PASSED),
            ),
            divergences = listOf("nota test"),
        )
        val json = PlayReportCodec.toJson(original)
        val decoded = PlayReportCodec.fromJson(json)
        assertEquals(original, decoded)
    }

    @Test
    fun jsonArray_keepsOrder() {
        val r1 = PlayExecutionReport(
            runId = "a",
            flowId = "f",
            flowName = "F",
            appId = "c",
            appLabel = "C",
            kind = PlayRunKind.PLAY,
            startedAtMs = 1L,
            finishedAtMs = 2L,
            totalSteps = 0,
            passedSteps = 0,
            failedSteps = 0,
            skippedOptionalSteps = 0,
            success = true,
            steps = emptyList(),
        )
        val r2 = r1.copy(runId = "b", finishedAtMs = 3L)
        val arr = PlayReportCodec.toJsonArray(listOf(r2, r1))
        val list = PlayReportCodec.fromJsonArray(arr)
        assertEquals(2, list.size)
        assertEquals("b", list[0].runId)
    }
}
