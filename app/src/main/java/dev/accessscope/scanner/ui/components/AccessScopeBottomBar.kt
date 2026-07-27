/**
 * Bottom bar contestuali del design "Scanner & HUD": zona principale e zona sessione.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Zona dell'app che determina quale bottom bar mostrare. */
enum class BottomZone { MAIN, SESSION, NONE }

/** Tab della zona sessione (report di una scansione). */
enum class SessionTab(val label: String, val icon: ImageVector) {
    SCANSIONE("Scansione", Icons.Outlined.Radar),
    DETTAGLI("Dettagli", Icons.Outlined.Description),
    REPORT("Report", Icons.Outlined.Analytics),
    STORICO("Storico", Icons.Outlined.History),
}

/** Voce della zona principale. */
private data class MainTabItem(
    val route: String,
    val label: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
)

private val mainTabs = listOf(
    MainTabItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    MainTabItem("favorites", "Preferiti", Icons.Filled.Star, Icons.Outlined.Star),
    MainTabItem("maestro", "Maestro", Icons.Filled.Movie, Icons.Outlined.Movie),
    MainTabItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * Ricava la zona di navigazione dalla route corrente.
 *
 * @param route Route corrente del NavHost (può includere pattern con argomenti).
 */
fun zoneForRoute(route: String?): BottomZone = when {
    route == null -> BottomZone.NONE
    route == "home" || route == "favorites" || route == "maestro" ||
        route.startsWith("maestro/") || route == "settings" -> BottomZone.MAIN
    route == "report" || route == "dynamic_report" ||
        route.startsWith("violation_detail") || route.startsWith("history") -> BottomZone.SESSION
    else -> BottomZone.NONE
}

/** Ricava il tab sessione attivo dalla route corrente. */
fun sessionTabForRoute(route: String?): SessionTab = when {
    route == null -> SessionTab.SCANSIONE
    route == "dynamic_report" -> SessionTab.REPORT
    route.startsWith("violation_detail") -> SessionTab.DETTAGLI
    route.startsWith("history") -> SessionTab.STORICO
    else -> SessionTab.SCANSIONE
}

/**
 * Bottom bar della zona principale: Home, Preferiti, Maestro, Settings.
 *
 * @param currentRoute Route attiva per evidenziare il tab selezionato.
 * @param onSelect Callback con la route della tab scelta.
 */
@Composable
fun MainBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        mainTabs.forEach { tab ->
            val selected = when (tab.route) {
                "maestro" -> currentRoute == "maestro" || currentRoute?.startsWith("maestro/") == true
                else -> currentRoute == tab.route
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        if (selected) tab.iconFilled else tab.iconOutlined,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * Bottom bar della zona sessione: Scansione, Dettagli, Report, Storico.
 *
 * @param currentRoute Route attiva per evidenziare il tab selezionato.
 * @param storicoEnabled Se false, il tab Storico è disabilitato (nessuna sessione nota).
 * @param onSelect Callback con il [SessionTab] scelto.
 */
@Composable
fun SessionBottomBar(
    currentRoute: String?,
    storicoEnabled: Boolean,
    onSelect: (SessionTab) -> Unit,
) {
    val activeTab = sessionTabForRoute(currentRoute)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        SessionTab.entries.forEach { tab ->
            val selected = tab == activeTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                enabled = tab != SessionTab.STORICO || storicoEnabled,
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Spacer minimo riservato (usato per allineamenti puntuali nelle barre). */
@Composable
private fun BottomBarDot() {
    Box(Modifier.size(6.dp))
}
