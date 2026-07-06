/**
 * Selettore visivo della preferenza tema (chiaro / scuro / sistema).
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.AppThemeMode
import dev.accessscope.scanner.ui.theme.accessScopeFocusRing
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Gruppo di card selezionabili per la preferenza tema.
 *
 * @param selected Modalità attualmente attiva.
 * @param onSelect Callback invocata quando l'utente sceglie una modalità.
 * @param modifier Modifier esterno alla colonna.
 */
@Composable
fun ThemeModeSelector(
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppThemeMode.entries.forEach { mode ->
            ThemeModeOptionCard(
                mode = mode,
                selected = mode == selected,
                onSelect = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun ThemeModeOptionCard(
    mode: AppThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .accessScopeFocusRing(shape = shape, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(mode.emoji, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f)) {
                Text(
                    mode.label,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    themeModeDescription(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
    }
}

private fun themeModeDescription(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.LIGHT -> "Sfondo chiaro, testo antracite, massimo contrasto."
    AppThemeMode.DARK -> "Grigio notte e card lavagna, riposante di notte."
    AppThemeMode.SYSTEM -> "Segue automaticamente le impostazioni del telefono."
}
