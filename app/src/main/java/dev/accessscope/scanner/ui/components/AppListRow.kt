/**
 * Riga ottimizzata dell'elenco app — callback stabili e layer GPU per scroll fluido.
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.InstalledAppInfo
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.ControlShape

private val AppRowHeight = 64.dp
private val FavoriteTint = Color(0xFFFFB300)

/**
 * Riga compatta per la lista app installate.
 *
 * @param onTogglePackage Callback stabile (es. `viewModel::toggleApp`) invocato con il package.
 * @param onToggleFavoritePackage Callback stabile per i preferiti.
 */
@Composable
fun AppListRow(
    app: InstalledAppInfo,
    selected: Boolean,
    onTogglePackage: (String) -> Unit,
    onToggleFavoritePackage: (String) -> Unit,
    modifier: Modifier = Modifier,
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }
    val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppRowHeight)
            .graphicsLayer { clip = true }
            .clip(ControlShape)
            .background(if (selected) selectedBg else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onTogglePackage(app.packageName) },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onToggleFavoritePackage(app.packageName) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (app.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (app.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                tint = if (app.isFavorite) FavoriteTint else secondaryTextColor,
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
                color = secondaryTextColor,
                maxLines = 1,
            )
        }
        Switch(
            checked = selected,
            onCheckedChange = { onTogglePackage(app.packageName) },
        )
    }
}
