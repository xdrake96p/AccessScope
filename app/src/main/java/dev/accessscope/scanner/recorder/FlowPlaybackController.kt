/**
 * Coordinatore playback flussi Maestro in-app (Beta).
 */
package dev.accessscope.scanner.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stato globale del playback: mutex con la registrazione (gestito dal chiamante).
 */
class FlowPlaybackController {

    private val _state = MutableStateFlow(FlowPlaybackState())
    val state: StateFlow<FlowPlaybackState> = _state.asStateFlow()

    val isPlaying: Boolean get() = _state.value.isPlaying

    /**
     * Marca inizio playback.
     *
     * @return `false` se già in play.
     */
    fun begin(flowId: String, totalSteps: Int, withScan: Boolean): Boolean {
        if (_state.value.isPlaying) return false
        _state.value = FlowPlaybackState(
            isPlaying = true,
            flowId = flowId,
            currentStep = 0,
            totalSteps = totalSteps,
            withScan = withScan,
            statusMessage = null,
        )
        return true
    }

    /** Aggiorna step corrente (0-based). */
    fun onStep(index: Int, total: Int) {
        _state.update {
            it.copy(currentStep = index, totalSteps = total)
        }
    }

    /** Termina con messaggio opzionale. */
    fun end(message: String?) {
        _state.value = FlowPlaybackState(
            isPlaying = false,
            statusMessage = message,
        )
    }

    fun clearStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}
