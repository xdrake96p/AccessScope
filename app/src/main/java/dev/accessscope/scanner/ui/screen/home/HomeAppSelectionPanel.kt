/**
 * Pannello Home per ricerca e opzioni di selezione app (senza elenco completo).
 */
package dev.accessscope.scanner.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Card con ricerca app, banner auto-launch, toggle sistema e clear selezione.
 *
 * @param query Testo corrente della ricerca.
 * @param onQueryChange Aggiorna la query (i risultati restano in [HomeScreen]).
 * @param appListState Stato elenco/selezione dal ViewModel.
 * @param viewModel Azioni selezione e filtri.
 * @param isScanning Disabilita i controlli durante lo scan.
 */
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
            "Cerca app da analizzare",
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
            "Cerca per nome o package, poi attiva lo switch. La stella aggiunge ai preferiti " +
                "(una sola app per sessione).",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Includi app di sistema", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Switch(
                checked = appListState.includeSystemApps,
                onCheckedChange = { viewModel.toggleIncludeSystemApps() },
                enabled = !isScanning,
            )
        }
        if (appListState.selectedPackages.isNotEmpty()) {
            TextButton(
                onClick = viewModel::clearSelection,
                enabled = !isScanning,
            ) {
                Text("Deseleziona")
            }
        }
    }
}
