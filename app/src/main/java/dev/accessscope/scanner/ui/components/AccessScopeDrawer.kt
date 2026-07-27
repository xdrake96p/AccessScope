/**
 * Contenuto del navigation drawer laterale del design "Scanner & HUD".
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.R
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Drawer laterale con brand e scorciatoie di navigazione.
 *
 * @param versionName Versione app mostrata sotto il brand (es. `1.3.1`).
 * @param historyEnabled Se false, la voce "Cronologia scansioni" è disabilitata.
 * @param onCronologia Apre la cronologia delle scansioni.
 * @param onUltimaSessione Apre il report dell'ultima sessione.
 * @param onFeedback Apre la schermata suggerimenti e segnalazioni.
 */
@Composable
fun AccessScopeDrawerContent(
    versionName: String,
    historyEnabled: Boolean,
    onCronologia: () -> Unit,
    onUltimaSessione: () -> Unit,
    onFeedback: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_access_scope_logo),
                contentDescription = "Logo AccessScope",
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "AccessScope",
                    fontFamily = HankenGroteskFamily,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Accessibility Auditor",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentSecondary(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "v$versionName",
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.labelSmall,
            color = contentSecondary(),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))
    val historyAlpha = if (historyEnabled) 1f else 0.45f
    NavigationDrawerItem(
        label = {
            Text(
                "Cronologia scansioni",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = historyAlpha),
            )
        },
        icon = {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = historyAlpha),
            )
        },
        selected = false,
        onClick = { if (historyEnabled) onCronologia() },
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
    NavigationDrawerItem(
        label = { Text("Ultima sessione", style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
        selected = false,
        onClick = onUltimaSessione,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    NavigationDrawerItem(
        label = { Text("Suggerimenti e Segnalazioni", style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(Icons.Outlined.Analytics, contentDescription = null) },
        selected = false,
        onClick = onFeedback,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
