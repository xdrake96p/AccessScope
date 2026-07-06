/**
 * Riga ottimizzata dell'elenco app — icone async e layer hardware per scroll fluido.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.ControlShape
import dev.accessscope.scanner.ui.theme.contentSecondary

private val AppRowHeight = 64.dp

/**
 * Riga compatta per la lista app installate.
 *
 * @param app Metadati app.
 * @param selected Se inclusa nel monitoraggio.
 * @param onToggle Inverte la selezione.
 * @param onToggleFavorite Aggiunge/rimuove dai preferiti.
 */
@Composable
fun AppListRow(
    app: InstalledAppInfo,
    selected: Boolean,
    onToggle: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppRowHeight)
            .graphicsLayer { clip = true }
            .clip(ControlShape)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (app.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (app.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                tint = if (app.isFavorite) Color(0xFFFFB300) else contentSecondary(),
                modifier = Modifier.size(22.dp),
            )
        }
        AppIconAsync(
            packageName = app.packageName,
            label = app.label,
            packageManager = packageManager,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                app.packageName,
                style = CodeTextStyle,
                color = contentSecondary(),
                maxLines = 1,
            )
        }
        Switch(
            checked = selected,
            onCheckedChange = { onToggle() },
        )
    }
}
