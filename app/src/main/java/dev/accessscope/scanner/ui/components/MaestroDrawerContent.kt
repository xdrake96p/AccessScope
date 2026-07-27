/**
 * Drawer laterale dedicato al tab Maestro (importa / crea YAML / feedback).
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Contenuto drawer Maestro · Beta (separato da [AccessScopeDrawerContent]).
 *
 * @param onImportYaml Apre il file picker per import YAML.
 * @param onCreateYaml Apre il flusso «nuovo YAML» (nome + app).
 * @param onMaestroBug Apre GitHub Issues bug Maestro.
 * @param onMaestroSuggestion Apre GitHub Issues miglioramento Maestro.
 */
@Composable
fun MaestroDrawerContent(
    onImportYaml: () -> Unit,
    onCreateYaml: () -> Unit,
    onMaestroBug: () -> Unit = {},
    onMaestroSuggestion: () -> Unit = {},
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            "Maestro · Beta",
            fontFamily = HankenGroteskFamily,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Importa, crea o segnala su GitHub",
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.labelSmall,
            color = contentSecondary(),
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        NavigationDrawerItem(
            label = { Text("Importa YAML", style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
            selected = false,
            onClick = onImportYaml,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        NavigationDrawerItem(
            label = { Text("Nuovo flusso YAML", style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null) },
            selected = false,
            onClick = onCreateYaml,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        NavigationDrawerItem(
            label = { Text("Segnala bug Maestro", style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
            selected = false,
            onClick = onMaestroBug,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Suggerisci miglioramento", style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) },
            selected = false,
            onClick = onMaestroSuggestion,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
