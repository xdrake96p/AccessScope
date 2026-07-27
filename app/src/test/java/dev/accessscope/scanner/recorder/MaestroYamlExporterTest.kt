package dev.accessscope.scanner.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressione export YAML Maestro (Beta).
 */
class MaestroYamlExporterTest {

    @Test
    fun export_includesBetaHeaderAndLaunchApp() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example.app",
            flowName = "Demo",
            actions = listOf(
                RecordedAction.Tap(
                    packageName = "com.example.app",
                    viewId = "com.example.app:id/btn_login",
                    text = "Accedi",
                ),
                RecordedAction.InputText(
                    packageName = "com.example.app",
                    text = "user@test.com",
                ),
            ),
        )
        assertTrue(yaml.contains("AccessScope Maestro (Beta)"))
        assertTrue(yaml.contains("appId: \"com.example.app\""))
        assertTrue(yaml.contains("- launchApp"))
        assertTrue(yaml.contains("id: \"btn_login\""))
        assertTrue(yaml.contains("- inputText: \"user@test.com\""))
        assertTrue(yaml.contains("tags:"))
        assertTrue(yaml.contains("- beta"))
    }

    @Test
    fun export_masksPasswordInput() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Pwd",
            actions = listOf(
                RecordedAction.InputText(
                    packageName = "com.example",
                    text = "****",
                    isPassword = true,
                ),
            ),
        )
        assertTrue(yaml.contains("****"))
        // launchApp + wait (synthetic) + inputText = 3 senza LaunchApp in lista
        assertEquals(3, MaestroYamlExporter.countSteps(listOf(
            RecordedAction.InputText("com.example", "****", isPassword = true),
        )))
    }

    @Test
    fun export_includesHideKeyboardAndWait() {
        val actions = FlowOptimizer.optimize(
            listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.InputText("com.example", "hello"),
                RecordedAction.HideKeyboard("com.example"),
                RecordedAction.Tap("com.example", text = "Go"),
            ),
        )
        val yaml = MaestroYamlExporter.export("com.example", "Flow", actions)
        assertTrue(yaml.contains("- hideKeyboard"))
        assertTrue(yaml.contains("waitForAnimationToEnd"))
    }

    @Test
    fun export_usesPointFallback() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Point",
            actions = listOf(
                RecordedAction.Tap(
                    packageName = "com.example",
                    pointPercentX = 50f,
                    pointPercentY = 25f,
                ),
            ),
        )
        assertTrue(yaml.contains("point: \"50.0%,25.0%\""))
    }

    @Test
    fun export_newActionTypes() {
        val yaml = MaestroYamlExporter.export(
            appId = "com.example",
            flowName = "Extras",
            actions = listOf(
                RecordedAction.LaunchApp("com.example"),
                RecordedAction.DoubleTap("com.example", text = "Zoom"),
                RecordedAction.EraseText("com.example", viewId = "com.example:id/username"),
                RecordedAction.Swipe("com.example", 50f, 80f, 50f, 20f),
                RecordedAction.PressKey("com.example", "Enter"),
                RecordedAction.AssertVisible("com.example", viewId = "com.example:id/home"),
                RecordedAction.AssertNotVisible("com.example", text = "Loading"),
                RecordedAction.ScrollUntilVisible("com.example", visibleText = "POLIZZA"),
                RecordedAction.OpenLink("com.example", "https://example.com"),
                RecordedAction.StopApp("com.example"),
                RecordedAction.RawMaestroYaml("com.example", "copyTextFrom: \"src\""),
            ),
        )
        assertTrue(yaml.contains("- doubleTapOn: \"Zoom\""))
        assertTrue(yaml.contains("- eraseText:"))
        assertTrue(yaml.contains("id: \"username\""))
        assertTrue(yaml.contains("- swipe:"))
        assertTrue(yaml.contains("start: \"50.0%,80.0%\""))
        assertTrue(yaml.contains("- pressKey: Enter"))
        assertTrue(yaml.contains("- assertVisible:"))
        assertTrue(yaml.contains("- assertNotVisible: \"Loading\""))
        assertTrue(yaml.contains("- scrollUntilVisible:"))
        assertTrue(yaml.contains("- openLink: \"https://example.com\""))
        assertTrue(yaml.contains("- stopApp"))
        assertTrue(yaml.contains("- copyTextFrom: \"src\""))
    }
}
