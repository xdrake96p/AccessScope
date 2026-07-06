/**
 * Icona app caricata in modo asincrono per non bloccare lo scroll della lista.
 */
package dev.accessscope.scanner.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.util.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mostra l'icona dell'app decodificando in background; se assente, iniziale del nome.
 *
 * @param packageName Package Android dell'app.
 * @param label Etichetta per il segnaposto con iniziale.
 * @param packageManager [PackageManager] per il caricamento icona.
 * @param size Dimensione visiva dell'icona.
 */
@Composable
fun AppIconAsync(
    packageName: String,
    label: String,
    packageManager: PackageManager,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        val loaded = withContext(Dispatchers.Default) {
            AppIconCache.getOrLoad(packageManager, packageName)
        }
        icon = loaded
    }
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = "Icona $label",
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = BrandPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
