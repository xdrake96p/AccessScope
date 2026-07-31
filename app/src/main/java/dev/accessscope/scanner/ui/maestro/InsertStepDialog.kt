/**
 * Dialog categorizzato per scegliere quale step Maestro inserire.
 */
package dev.accessscope.scanner.ui.maestro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Picker tipi step (sostituisce il DropdownMenu lungo e il `+` che inseriva Wait).
 *
 * @param packageName Package del flusso (per factory azioni).
 * @param onDismiss Chiude senza inserire.
 * @param onPick Azione scelta da inserire sotto lo step selezionato.
 */
@Composable
fun InsertStepDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onPick: (RecordedAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inserisci step") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Scegli il tipo di passo da aggiungere sotto lo step selezionato.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
                InsertStepCatalog.byCategory().forEach { (category, options) ->
                    Text(
                        category.labelIt,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    options.forEach { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(option.factory(packageName))
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentSecondary(),
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
