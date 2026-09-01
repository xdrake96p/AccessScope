/**
 * Transcript testuale per step Maestro (alternativa a screenshot su schermate protette).
 */
package dev.accessscope.scanner.recorder.capture

import dev.accessscope.scanner.data.ScreenProtectionReason
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.CompactA11yNode
import dev.accessscope.scanner.util.SecureScreenAssessment

/**
 * Genera narrativa strutturata per prompt Gemini quando JPEG non è disponibile.
 */
object StepSemanticTranscriptBuilder {

    /**
     * @param action Azione registrata allo step.
     * @param tree Albero a11y compatto.
     * @param assessment Valutazione schermata protetta.
     * @param windowTitle Titolo finestra.
     */
    fun build(
        action: RecordedAction,
        tree: List<CompactA11yNode>,
        assessment: SecureScreenAssessment,
        windowTitle: String?,
    ): String = buildString {
        append("azione=${action::class.simpleName} pkg=${action.packageName}")
        appendActionDetail(action)
        append(" | title=${windowTitle ?: "-"}")
        append(" | protection=${assessment.reason.name}")
        if (assessment.useSecureEvidence) {
            append(" | EVIDENZA_VISIVA=N/A usa albero a11y e wireframe")
        }
        val clickables = tree.filter { it.clickable }.take(12)
        val editables = tree.filter { it.editable }.take(8)
        if (clickables.isNotEmpty()) {
            append(" | clickable=[")
            append(clickables.joinToString("; ") { formatNode(it) })
            append("]")
        }
        if (editables.isNotEmpty()) {
            append(" | editable=[")
            append(editables.joinToString("; ") { formatNode(it) })
            append("]")
        }
        if (assessment.reason == ScreenProtectionReason.PIN_OR_PASSWORD) {
            append(" | hint=usa \${PIN} o \${PASSWORD} in inputText")
        }
    }

    private fun StringBuilder.appendActionDetail(action: RecordedAction) {
        when (action) {
            is RecordedAction.Tap -> append(
                " tap id=${action.viewId} text=${action.text} cd=${action.contentDescription} " +
                    "point=${action.pointPercentX},${action.pointPercentY} optional=${action.executionMode.name}",
            )
            is RecordedAction.InputText -> append(
                " input id=${action.viewId} pwd=${action.isPassword} len=${action.text.length}",
            )
            is RecordedAction.Scroll -> append(" scroll ${action.direction}")
            else -> Unit
        }
    }

    private fun formatNode(node: CompactA11yNode): String =
        "id=${node.viewId ?: "-"} text=${node.text ?: "-"} cd=${node.contentDescription ?: "-"} pwd=${node.password}"
}
