/**
 * Card con opzione per aprire automaticamente la prima app alla scansione.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Card con switch per l'apertura automatica dell'app all'avvio della scansione.
 *
 * @param autoLaunchEnabled Stato corrente dell'opzione auto-launch.
 * @param onToggleAutoLaunch Callback invocato al cambio dello switch.
 * @param modifier Modifier Compose applicato alla card.
 */
@Composable
fun ScanOptionsCard(
    autoLaunchEnabled: Boolean,
    onToggleAutoLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Apri app automaticamente", fontWeight = FontWeight.SemiBold)
                Text(
                    "All'avvio della scansione apre la prima app selezionata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
            Switch(
                checked = autoLaunchEnabled,
                onCheckedChange = { onToggleAutoLaunch() },
            )
        }
    }
}
