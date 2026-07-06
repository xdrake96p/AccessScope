/**
 * Pulsante di accesso alla cronologia scansioni, sempre visibile in Home.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Apre la cronologia per l'app attualmente selezionata nell'elenco.
 *
 * Resta visibile anche prima della prima scansione; disabilitato se nessuna app è selezionata.
 *
 * @param enabled True se almeno un package è selezionato.
 * @param appLabel Nome visualizzato dell'app target (opzionale, per sottotitolo).
 * @param onClick Callback con il package name da aprire in cronologia.
 * @param modifier Modifier Compose applicato al contenitore.
 */
@Composable
fun ScanHistoryEntryButton(
    enabled: Boolean,
    appLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cronologia scansioni")
        }
        Text(
            text = when {
                !enabled -> "Seleziona un'app per vedere le scansioni precedenti."
                appLabel != null -> "Ultimi report archiviati per $appLabel."
                else -> "Ultimi report archiviati per l'app selezionata."
            },
            style = MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
