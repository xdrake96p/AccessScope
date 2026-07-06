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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
 */
@Composable
fun AppIconAsync(
    packageName: String,
    label: String,
    packageManager: PackageManager,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val icon by produceState<ImageBitmap?>(initialValue = AppIconCache.peek(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                AppIconCache.getOrLoad(packageManager, packageName)
            }
        }
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
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
