package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.optimization.conditional.PopupClassifier
import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cattura popup di permesso / dismiss (piano Maestro): non devono essere droppati.
 */
class PopupCapturePolicyTest {

    @Test
    fun permissionController_isCaptureDialog_notForeign() {
        val pkg = "com.android.permissioncontroller"
        assertTrue(MaestroSelectorHeuristics.isCaptureDialogPackage(pkg))
        assertFalse(MaestroSelectorHeuristics.isForeignUiPackage(pkg))
    }

    @Test
    fun systemUi_stillForeign_notDialog() {
        val pkg = "com.android.systemui"
        assertFalse(MaestroSelectorHeuristics.isCaptureDialogPackage(pkg))
        assertTrue(MaestroSelectorHeuristics.isForeignUiPackage(pkg))
    }

    @Test
    fun dropForeignUiActions_keepsAllowOnPermissionController() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.axa.app"),
            RecordedAction.Tap(
                packageName = "com.android.permissioncontroller",
                text = "Consenti",
            ),
            RecordedAction.Tap(packageName = "com.axa.app", text = "Continua"),
        )
        val kept = NoiseActionFilter.dropForeignUiActions(actions, "com.axa.app")
        assertEquals(3, kept.size)
        assertTrue(kept.any { it is RecordedAction.Tap && it.text == "Consenti" })
    }

    @Test
    fun dropForeignUiActions_stillDropsSystemUi() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.axa.app"),
            RecordedAction.Tap(packageName = "com.android.systemui", text = "Indietro"),
        )
        val kept = NoiseActionFilter.dropForeignUiActions(actions, "com.axa.app")
        assertEquals(1, kept.size)
        assertTrue(kept.single() is RecordedAction.LaunchApp)
    }

    @Test
    fun permissionAllow_isOptionalWithoutTelemetry() {
        val tap = RecordedAction.Tap(
            packageName = "com.android.permissioncontroller",
            text = "Allow",
        )
        val meta = PopupClassifier.classifyTap(tap, 1, telemetry = null, intel = null)
        assertEquals(StepExecutionMode.Optional, meta.executionMode)
    }

    @Test
    fun strongDismissOnAppPackage_isOptional() {
        val tap = RecordedAction.Tap(packageName = "com.axa.app", text = "Non ora")
        val meta = PopupClassifier.classifyTap(tap, 0, telemetry = null, intel = null)
        assertEquals(StepExecutionMode.Optional, meta.executionMode)
    }
}
