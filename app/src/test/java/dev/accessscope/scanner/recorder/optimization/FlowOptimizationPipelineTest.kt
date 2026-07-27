package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.MaestroYamlExporter
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.OptimizationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrazione pipeline su fixture MyAXA-like.
 */
class FlowOptimizationPipelineTest {

    @Test
    fun myAxaLike_removesNoiseAndPasswordFocus() {
        val raw = listOf(
            RecordedAction.LaunchApp("com.axa.app"),
            RecordedAction.Tap("com.axa.app", viewId = "com.axa.app:id/signInButton", timestampMs = 1_000L),
            RecordedAction.Tap("com.axa.app", viewId = "com.axa.app:id/progressBarContent", timestampMs = 1_100L),
            RecordedAction.Tap("com.axa.app", viewId = "com.axa.app:id/password", timestampMs = 1_200L),
            RecordedAction.InputText("com.axa.app", "****", viewId = "com.axa.app:id/password", isPassword = true, timestampMs = 1_500L),
            RecordedAction.InputText("com.axa.app", "user", viewId = "com.axa.app:id/username", timestampMs = 2_000L),
        )
        val optimized = FlowOptimizationPipeline.optimize(
            raw,
            OptimizationContext(appId = "com.axa.app"),
        )
        assertTrue(optimized.none {
            it is RecordedAction.Tap && MaestroSelectorHeuristics.isNoiseViewId(it.viewId)
        })
        assertTrue(optimized.none {
            it is RecordedAction.Tap && MaestroSelectorHeuristics.isEditableFieldViewId(it.viewId)
        })
        assertTrue(optimized.count { it is RecordedAction.InputText } >= 1)
    }

    @Test
    fun myAxaLike_dropsSystemUiBackBetweenInputs() {
        val pkg = "com.axa.app.myaxa.it.develop"
        val raw = listOf(
            RecordedAction.LaunchApp(pkg),
            RecordedAction.Tap(pkg, text = "ACCEDI", timestampMs = 1_000L),
            RecordedAction.InputText(pkg, "testo", viewId = "$pkg:id/username", timestampMs = 2_000L),
            RecordedAction.Tap(
                packageName = "com.android.systemui",
                viewId = "com.android.systemui:id/back",
                text = "Indietro",
                contentDescription = "Indietro",
                timestampMs = 2_500L,
            ),
            RecordedAction.InputText(pkg, "****", viewId = "$pkg:id/password", isPassword = true, timestampMs = 3_000L),
            RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 3_500L),
        )
        val optimized = FlowOptimizationPipeline.optimize(raw, OptimizationContext(appId = pkg))
        assertTrue(
            optimized.none {
                it is RecordedAction.Tap &&
                    MaestroSelectorHeuristics.isSystemChromeTap(it.packageName, it.viewId, it.text, it.contentDescription)
            },
        )
        val yaml = MaestroYamlExporter.export(pkg, "MyAXA", optimized)
        assertFalse(yaml.contains("id: \"back\""))
        assertFalse(yaml.contains("systemui"))

        val sanitized = FlowOptimizationPipeline.sanitizeForPlay(raw, pkg)
        assertEquals(5, sanitized.size) // launch + ACCEDI + username + password + CONTINUA (no systemui)
        assertTrue(sanitized.none { it.packageName.contains("systemui") })
        assertTrue(sanitized.any { it is RecordedAction.HideKeyboard }.not())
        assertTrue(sanitized.count { it is RecordedAction.InputText } == 2)
    }
}
