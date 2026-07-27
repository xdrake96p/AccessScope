package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.StepExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test round-trip export→import→export idempotente (piano M1-A3).
 *
 * Garantisce che un flusso esportato, reimportato e riesportato produca
 * lo stesso YAML (stabilità per edit esterni e CI).
 */
class MaestroYamlRoundTripTest {

    private fun sampleActions(): List<RecordedAction> = listOf(
        RecordedAction.LaunchApp("com.example.app"),
        RecordedAction.WaitForAnimation("com.example.app"),
        RecordedAction.Tap(
            packageName = "com.example.app",
            viewId = "com.example.app:id/username",
            timestampMs = 1_000,
        ),
        RecordedAction.InputText("com.example.app", text = "user@example.com", viewId = "com.example.app:id/username"),
        RecordedAction.HideKeyboard("com.example.app"),
        RecordedAction.Tap(
            packageName = "com.example.app",
            text = "CONTINUA",
            timestampMs = 2_000,
            executionMode = StepExecutionMode.Optional,
        ),
        RecordedAction.ScrollUntilVisible(
            packageName = "com.example.app",
            visibleId = "com.example.app:id/btn_pay",
        ),
        RecordedAction.DoubleTap(
            packageName = "com.example.app",
            viewId = "com.example.app:id/icon",
        ),
        RecordedAction.EraseText(
            packageName = "com.example.app",
            viewId = "com.example.app:id/username",
        ),
        RecordedAction.Swipe(
            packageName = "com.example.app",
            startPercentX = 50f,
            startPercentY = 80f,
            endPercentX = 50f,
            endPercentY = 20f,
        ),
        RecordedAction.PressKey("com.example.app", key = "Enter"),
        RecordedAction.AssertNotVisible(
            packageName = "com.example.app",
            text = "Loading",
        ),
        RecordedAction.OpenLink("com.example.app", url = "https://example.com"),
        RecordedAction.Tap(
            packageName = "com.example.app",
            viewId = "com.example.app:id/btn_pay",
            timestampMs = 3_000,
        ),
        RecordedAction.Wait(
            packageName = "com.example.app",
            timeoutMs = 5_000L,
            visibleId = "com.example.app:id/success",
        ),
        RecordedAction.AssertVisible(
            packageName = "com.example.app",
            viewId = "com.example.app:id/success",
        ),
        RecordedAction.Back("com.example.app"),
        RecordedAction.StopApp("com.example.app"),
    )

    @Test
    fun exportImportExport_isIdempotent() {
        val firstYaml = MaestroYamlExporter.export(
            appId = "com.example.app",
            flowName = "RoundTrip",
            actions = sampleActions(),
        )

        val imported = MaestroYamlImporter.parse(firstYaml)
        assertTrue(imported is MaestroImportResult.Success)
        val success = imported as MaestroImportResult.Success

        val secondYaml = MaestroYamlExporter.export(
            appId = success.appId,
            flowName = success.name,
            actions = success.actions,
        )

        assertEquals(firstYaml, secondYaml)
    }

    @Test
    fun passwordInput_isMaskedInYaml() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example.app",
            flowName = "Secret",
            actions = listOf(
                RecordedAction.LaunchApp("com.example.app"),
                RecordedAction.InputText(
                    packageName = "com.example.app",
                    text = "superSecret123",
                    viewId = "com.example.app:id/password",
                    isPassword = true,
                ),
            ),
        )
        assertTrue("la password non deve apparire in chiaro", !yaml.contains("superSecret123"))
        assertTrue(yaml.contains("password masked"))
    }

    @Test
    fun importThenExport_preservesStepCount() {
        val actions = sampleActions()
        val yaml = MaestroYamlExporter.export("com.example.app", "Count", actions)
        val imported = MaestroYamlImporter.parse(yaml) as MaestroImportResult.Success
        assertEquals(actions.size, imported.actions.size)
    }
}
