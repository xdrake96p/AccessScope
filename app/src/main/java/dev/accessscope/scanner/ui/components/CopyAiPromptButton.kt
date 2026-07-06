/**
 * Pulsante per copiare negli appunti un prompt AI generato dai risultati di scansione.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.report.AiPromptBuilder
import dev.accessscope.scanner.report.AiPromptInput
import dev.accessscope.scanner.ui.theme.ControlShape
import dev.accessscope.scanner.util.ClipboardHelper

/**
 * Copia un prompt ottimizzato per assistenti AI basato sui risultati AccessScope.
 *
 * @param input Dati della sessione da serializzare nel prompt.
 * @param onCopied Invocato dopo la copia (es. per mostrare uno Snackbar).
 * @param enabled Se false, il pulsante è disabilitato (nessun problema da esportare).
 * @param modifier Modifier esterno.
 */
@Composable
fun CopyAiPromptButton(
    input: AiPromptInput,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val prompt = remember(input) { AiPromptBuilder.build(input) }
    val canCopy = enabled && prompt.isNotBlank()

    OutlinedButton(
        onClick = {
            ClipboardHelper.copyText(context, "Prompt AI AccessScope", prompt)
            onCopied()
        },
        enabled = canCopy,
        modifier = modifier.fillMaxWidth(),
        shape = ControlShape,
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Copia prompt AI")
    }
}
