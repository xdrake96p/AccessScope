/**
 * Bus anteprima YAML live durante la registrazione Maestro.
 */
package dev.accessscope.scanner.recorder

import android.content.Context
import android.content.Intent

/**
 * Notifica l’overlay REC con riepilogo step leggibile (id/text/point).
 */
object RecordingLivePreview {
    const val ACTION_PREVIEW = "dev.accessscope.scanner.MAESTRO_REC_PREVIEW"
    const val EXTRA_YAML_SNIPPET = "yaml_snippet"
    const val EXTRA_STEP_COUNT = "step_count"
    const val EXTRA_PICK_MODE = "pick_mode"
    const val EXTRA_PAUSED = "paused"

    /**
     * Pubblica anteprima per l’overlay.
     *
     * @param context Context app.
     * @param actions Azioni correnti.
     * @param appId Package target (riservato per export completo futuro).
     * @param pickMode Se true, overlay mostra stato PICK.
     * @param paused Se true, registrazione in pausa.
     */
    @Suppress("UNUSED_PARAMETER")
    fun publish(
        context: Context,
        actions: List<RecordedAction>,
        appId: String,
        pickMode: Boolean = false,
        paused: Boolean = false,
    ) {
        val real = actions.filter { it !is RecordedAction.LaunchApp }
        val snippet = real.takeLast(12).joinToString("\n") { summarize(it) }
        context.sendBroadcast(
            Intent(ACTION_PREVIEW).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_YAML_SNIPPET, snippet.ifBlank { "# in attesa di step…" })
                putExtra(EXTRA_STEP_COUNT, real.size)
                putExtra(EXTRA_PICK_MODE, pickMode)
                putExtra(EXTRA_PAUSED, paused)
            },
        )
    }

    /**
     * Riepilogo one-line per overlay (selettore visibile).
     */
    fun summarize(action: RecordedAction): String = when (action) {
        is RecordedAction.LaunchApp -> "launchApp"
        is RecordedAction.Tap -> "tapOn ${selectorLabel(action.viewId, action.text, action.contentDescription, action.pointPercentX, action.pointPercentY)}"
        is RecordedAction.DoubleTap -> "doubleTap ${selectorLabel(action.viewId, action.text, action.contentDescription, action.pointPercentX, action.pointPercentY)}"
        is RecordedAction.LongPress -> "longPress ${selectorLabel(action.viewId, action.text, action.contentDescription, action.pointPercentX, action.pointPercentY)}"
        is RecordedAction.InputText -> {
            val id = MaestroSelectorHeuristics.shortViewId(action.viewId)?.let { "id=$it " }.orEmpty()
            val body = if (action.isPassword || action.text == "****") "****" else "\"${action.text.take(20)}\""
            "inputText ${id}$body".trim()
        }
        is RecordedAction.EraseText -> "eraseText ${MaestroSelectorHeuristics.shortViewId(action.viewId) ?: ""}".trim()
        is RecordedAction.Scroll -> "scroll ${action.direction.name.lowercase()}"
        is RecordedAction.ScrollUntilVisible ->
            "scrollUntil ${selectorLabel(action.visibleId, action.visibleText, null, null, null)}"
        is RecordedAction.Swipe ->
            "swipe ${action.startPercentX.toInt()}%,${action.startPercentY.toInt()}%→${action.endPercentX.toInt()}%,${action.endPercentY.toInt()}%"
        is RecordedAction.Back -> "back"
        is RecordedAction.PressKey -> "pressKey ${action.key}"
        is RecordedAction.AssertVisible ->
            "assertVisible ${selectorLabel(action.viewId, action.text, null, null, null)}"
        is RecordedAction.AssertNotVisible ->
            "assertNotVisible ${selectorLabel(action.viewId, action.text, null, null, null)}"
        is RecordedAction.OpenLink -> "openLink ${action.url.take(32)}"
        is RecordedAction.StopApp -> "stopApp"
        is RecordedAction.Wait -> when {
            !action.visibleId.isNullOrBlank() ->
                "waitUntil id=${MaestroSelectorHeuristics.shortViewId(action.visibleId)}"
            !action.visibleText.isNullOrBlank() -> "waitUntil \"${action.visibleText}\""
            else -> "wait ${action.timeoutMs}ms"
        }
        is RecordedAction.WaitForAnimation -> "waitAnim ${action.timeoutMs ?: ""}".trim()
        is RecordedAction.HideKeyboard -> "hideKeyboard"
        is RecordedAction.RawMaestroYaml -> "raw YAML"
    }

    private fun selectorLabel(
        viewId: String?,
        text: String?,
        cd: String?,
        px: Float?,
        py: Float?,
    ): String {
        val short = MaestroSelectorHeuristics.shortViewId(viewId)
        return when {
            !short.isNullOrBlank() && !MaestroSelectorHeuristics.isStructuralContainerViewId(short) ->
                "id=$short"
            !text.isNullOrBlank() -> "\"${text.take(28)}\""
            !cd.isNullOrBlank() -> "cd=\"${cd.take(28)}\""
            px != null && py != null -> "@${px.toInt()}%,${py.toInt()}%"
            !short.isNullOrBlank() -> "id=$short"
            else -> "(?)"
        }
    }
}
