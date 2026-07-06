/**
 * Anello di focus ad alto contrasto per elementi interattivi (WCAG 2.4.7).
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Disegna un anello di focus (2dp) quando [interactionSource] segnala focus.
 *
 * Usare insieme a [Modifier.clickable] con la stessa [interactionSource].
 */
@Composable
fun Modifier.accessScopeFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    interactionSource: MutableInteractionSource,
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val ringColor = MaterialTheme.colorScheme.primary
    return border(
        width = if (isFocused) 2.dp else 0.dp,
        color = if (isFocused) ringColor else Color.Transparent,
        shape = shape,
    )
}
