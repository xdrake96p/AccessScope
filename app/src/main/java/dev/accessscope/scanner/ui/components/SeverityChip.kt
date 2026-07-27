/**
 * Chip pill di gravità violazione, stile "Scanner & HUD" (mono uppercase).
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.ViolationSeverity
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape

/**
 * Colori (container, contenuto) della chip per gravità, fedeli ai mockup Stitch:
 * CRITICO pieno, GRAVE soft, MEDIO ambra, LIEVE neutro.
 */
@Composable
fun severityChipColors(severity: ViolationSeverity): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (severity) {
        ViolationSeverity.CRITICAL -> scheme.error to scheme.onError
        ViolationSeverity.SERIOUS -> scheme.errorContainer to scheme.onErrorContainer
        ViolationSeverity.MODERATE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ViolationSeverity.MINOR -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

/**
 * Chip pill con etichetta di gravità in JetBrains Mono.
 *
 * @param severity Gravità da rappresentare.
 * @param modifier Modifier esterno.
 */
@Composable
fun SeverityChip(severity: ViolationSeverity, modifier: Modifier = Modifier) {
    val (container, content) = severityChipColors(severity)
    Text(
        text = severity.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
