/**
 * Modello delle azioni registrate per l’export YAML Maestro (feature Maestro Beta).
 */
package dev.accessscope.scanner.recorder

import dev.accessscope.scanner.recorder.model.SelectorCandidate
import dev.accessscope.scanner.recorder.model.StepExecutionMode

/**
 * Singola azione catturata dal servizio di accessibilità durante una registrazione,
 * importata da YAML, o inserita nell’editor step / pick overlay.
 */
sealed class RecordedAction {
    abstract val timestampMs: Long
    abstract val packageName: String

    data class LaunchApp(
        override val packageName: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class Tap(
        override val packageName: String,
        val viewId: String? = null,
        val text: String? = null,
        val contentDescription: String? = null,
        val pointPercentX: Float? = null,
        val pointPercentY: Float? = null,
        val executionMode: StepExecutionMode = StepExecutionMode.Required,
        val conditionVisibleId: String? = null,
        val conditionVisibleText: String? = null,
        /**
         * Catena ordinata di selettori (id → cd → text → point).
         * Se vuota, Play usa i campi primari; la pipeline la popola in optimize.
         */
        val selectorChain: List<SelectorCandidate> = emptyList(),
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class DoubleTap(
        override val packageName: String,
        val viewId: String? = null,
        val text: String? = null,
        val contentDescription: String? = null,
        val pointPercentX: Float? = null,
        val pointPercentY: Float? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class LongPress(
        override val packageName: String,
        val viewId: String? = null,
        val text: String? = null,
        val contentDescription: String? = null,
        val pointPercentX: Float? = null,
        val pointPercentY: Float? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class InputText(
        override val packageName: String,
        val text: String,
        val viewId: String? = null,
        val isPassword: Boolean = false,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class EraseText(
        override val packageName: String,
        val viewId: String? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class Scroll(
        override val packageName: String,
        val direction: ScrollDirection = ScrollDirection.DOWN,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class ScrollUntilVisible(
        override val packageName: String,
        val visibleId: String? = null,
        val visibleText: String? = null,
        val direction: ScrollDirection = ScrollDirection.DOWN,
        val timeoutMs: Long = 20_000L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class Swipe(
        override val packageName: String,
        val startPercentX: Float,
        val startPercentY: Float,
        val endPercentX: Float,
        val endPercentY: Float,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class Back(
        override val packageName: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class PressKey(
        override val packageName: String,
        val key: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class AssertVisible(
        override val packageName: String,
        val viewId: String? = null,
        val text: String? = null,
        val timeoutMs: Long = 10_000L,
        val executionMode: StepExecutionMode = StepExecutionMode.Required,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class AssertNotVisible(
        override val packageName: String,
        val viewId: String? = null,
        val text: String? = null,
        val timeoutMs: Long = 5_000L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class OpenLink(
        override val packageName: String,
        val url: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class StopApp(
        override val packageName: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    /**
     * Attesa fissa o fino a elemento visibile (`extendedWaitUntil` in Maestro).
     */
    data class Wait(
        override val packageName: String,
        val timeoutMs: Long = 10_000L,
        val visibleId: String? = null,
        val visibleText: String? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class WaitForAnimation(
        override val packageName: String,
        val timeoutMs: Long? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    data class HideKeyboard(
        override val packageName: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()

    /**
     * Frammento YAML Maestro grezzo (export/editor; Play in-app può saltarlo).
     *
     * @param yamlLines Linee YAML dello step (senza `appId` header).
     */
    data class RawMaestroYaml(
        override val packageName: String,
        val yamlLines: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : RecordedAction()
}

/** Direzione di scroll aggregata per Maestro. */
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Metadati di un flusso salvato (Beta).
 */
data class SavedFlow(
    val id: String,
    val name: String,
    val appId: String,
    val appLabel: String,
    val createdAtMs: Long,
    val stepCount: Int,
    val yamlRelativePath: String,
    val hasActionsJson: Boolean = false,
)

/**
 * Stato runtime della registrazione Maestro (Beta).
 */
data class RecordingState(
    val isRecording: Boolean = false,
    val targetPackage: String? = null,
    val targetLabel: String? = null,
    val actions: List<RecordedAction> = emptyList(),
    val statusMessage: String? = null,
    val pickMode: Boolean = false,
    val isPaused: Boolean = false,
)

/**
 * Stato runtime del playback in-app di un flusso Maestro (Beta).
 */
data class FlowPlaybackState(
    val isPlaying: Boolean = false,
    val flowId: String? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val statusMessage: String? = null,
    val withScan: Boolean = false,
)
