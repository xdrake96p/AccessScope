package dev.accessscope.scanner.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.accessibility.asSectionHeading
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.components.AppSearchField
import dev.accessscope.scanner.ui.components.AppSelectionInfoBanner
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.AppListUiState
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel

@Composable
internal fun AppSelectionPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    appListState: AppListUiState,
    viewModel: ScanViewModel,
    isScanning: Boolean,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Text(
            "Seleziona app da analizzare",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier.asSectionHeading(),
        )
        AppSearchField(query = query, onQueryChange = onQueryChange)
        AppSelectionInfoBanner(
            autoLaunchEnabled = appListState.autoLaunchEnabled,
        )
        Text(
            "La stella aggiunge ai preferiti e attiva subito il monitoraggio (una sola app per sessione).",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mostra app di sistema", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Switch(
                checked = appListState.includeSystemApps,
                onCheckedChange = { viewModel.toggleIncludeSystemApps() },
                enabled = !isScanning,
            )
        }
        Row {
            TextButton(onClick = viewModel::selectAllVisible, enabled = !isScanning) {
                Icon(Icons.Outlined.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Prima visibile")
            }
            TextButton(onClick = viewModel::clearSelection, enabled = !isScanning) {
                Text("Nessuna")
            }
        }
    }
}
