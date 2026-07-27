package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.StepExecutionMode
import org.junit.Assert.assertTrue
import org.junit.Test

class MaestroYamlExporterOptionalTest {

    @Test
    fun exportsOptionalTap() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap(
                packageName = "com.app",
                text = "Non ora",
                executionMode = StepExecutionMode.Optional,
            ),
        )
        val yaml = MaestroYamlExporter.export("com.app", "T", actions)
        assertTrue(yaml.contains("optional: true"))
    }
}
