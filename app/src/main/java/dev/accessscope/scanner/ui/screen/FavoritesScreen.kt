/**
 * Schermata Preferiti: mostra solo le app marcate con la stella.
 */
package dev.accessscope.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.components.AppIconAsync
import dev.accessscope.scanner.ui.components.AppSearchField
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.FavoriteAccent
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.delay

/**
 * Schermata dei preferiti (tab zona principale).
 *
 * Elenca esclusivamente le app con stella; la ricerca filtra solo tra i preferiti.
 * Per aggiungerne di nuove si usa la stella nella ricerca della Home.
 *
 * @param viewModel ViewModel condiviso con elenco app e azione toggle preferiti.
 */
@Composable
fun FavoritesScreen(viewModel: ScanViewModel) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }
    val appListState by viewModel.appListUiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.isBlank()) debouncedQuery = "" else {
            delay(120)
            debouncedQuery = query
        }
    }

    val favorites = remember(appListState.apps, debouncedQuery) {
        val favoriteApps = appListState.apps.filter { it.isFavorite }
        if (debouncedQuery.isBlank()) favoriteApps
        else favoriteApps.filter {
            it.label.contains(debouncedQuery, ignoreCase = true) ||
                it.packageName.contains(debouncedQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = { AccessScopeTopBar(title = "Preferiti") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                AppSearchField(query = query, onQueryChange = { query = it })
            }

            item(key = "fav_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "App Preferite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "${favorites.size} app",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (favorites.isEmpty()) {
                item(key = "fav_empty") {
                    Text(
                        when {
                            appListState.apps.none { it.isFavorite } ->
                                "Nessuna app preferita. Cerca un'app in Home e tocca la stella per aggiungerla qui."
                            else ->
                                "Nessuna app preferita corrisponde alla ricerca."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentSecondary(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
            } else {
                items(
                    items = favorites.chunked(2),
                    key = { row -> row.joinToString("+") { it.packageName } },
                ) { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowApps.forEach { app ->
                            FavoriteAppCard(
                                app = app,
                                packageManager = packageManager,
                                onToggleFavorite = viewModel::toggleFavorite,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowApps.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Card di un'app preferita con icona, nome e stella piena per rimuoverla.
 *
 * @param app App preferita da mostrare.
 * @param packageManager Usato per caricare l'icona.
 * @param onToggleFavorite Rimuove dai preferiti (stella).
 */
@Composable
private fun FavoriteAppCard(
    app: InstalledAppInfo,
    packageManager: android.content.pm.PackageManager,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIconAsync(
                packageName = app.packageName,
                label = app.label,
                packageManager = packageManager,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                app.packageName,
                style = CodeTextStyle,
                color = contentSecondary(),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = { onToggleFavorite(app.packageName) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Rimuovi dai preferiti",
                tint = FavoriteAccent,
            )
        }
    }
}
