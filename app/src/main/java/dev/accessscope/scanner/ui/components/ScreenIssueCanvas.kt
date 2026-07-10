/**
 * Canvas interattivo con screenshot e overlay dei problemi di accessibilità.
 */
package dev.accessscope.scanner.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.severityColor
import dev.accessscope.scanner.util.ViolationBoundsParser

/**
 * Mostra lo screenshot della schermata con marker numerati sui problemi.
 *
 * @param bitmap Screenshot originale della schermata.
 * @param violations Violazioni da evidenziare (già filtrate).
 * @param selectedDedupeKey Chiave della violazione attualmente selezionata.
 * @param onViolationClick Callback al tap su un marker.
 */
@Composable
fun ScreenIssueCanvas(
    bitmap: Bitmap?,
    violations: List<AccessibilityViolation>,
    selectedDedupeKey: String?,
    onViolationClick: (AccessibilityViolation) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Screenshot non disponibile per questa schermata.",
                style = MaterialTheme.typography.bodyMedium,
                color = contentSecondary(),
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CardShape,
            ),
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val scale = containerWidthPx / bitmap.width.toFloat()
        val displayHeightDp = with(density) { (bitmap.height * scale).toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(displayHeightDp),
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Screenshot schermata analizzata",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )

            violations.forEachIndexed { index, violation ->
                val rect = ViolationBoundsParser.rect(violation) ?: return@forEachIndexed
                val isSelected = violation.dedupeKey == selectedDedupeKey
                val severityTint = severityColor(violation.type.severity)
                val leftDp = with(density) { (rect.left * scale).toDp() }
                val topDp = with(density) { (rect.top * scale).toDp() }
                val widthDp = with(density) { (rect.width() * scale).toDp().coerceAtLeast(24.dp) }
                val heightDp = with(density) { (rect.height() * scale).toDp().coerceAtLeast(24.dp) }

                Box(
                    modifier = Modifier
                        .offset(x = leftDp, y = topDp)
                        .size(widthDp, heightDp)
                        .border(
                            width = if (isSelected) 3.dp else 2.dp,
                            color = severityTint.copy(alpha = if (isSelected) 1f else 0.85f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .background(severityTint.copy(alpha = if (isSelected) 0.22f else 0.12f))
                        .clickable { onViolationClick(violation) },
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset((-6).dp, (-6).dp)
                            .size(22.dp)
                            .background(severityTint, RoundedCornerShape(50))
                            .border(1.dp, Color.White, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
