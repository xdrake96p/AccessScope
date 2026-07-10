/**
 * Componenti UI riutilizzabili per il dettaglio violazioni (swatch colore, righe tecniche, sezioni).
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.theme.contentSecondary

/** Converte `#RRGGBB` o `#AARRGGBB` in [Color] Compose. */
fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> {
                val rgb = cleaned.toLong(16)
                Color(0xFF000000L or rgb)
            }
            8 -> Color(cleaned.toLong(16).toInt())
            else -> null
        }
    }.getOrNull()
}

@Composable
fun ColorSwatch(
    hex: String,
    modifier: Modifier = Modifier,
) {
    val color = remember(hex) { parseHexColor(hex) } ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun ViolationDetailLine(
    line: String,
    violation: AccessibilityViolation,
    modifier: Modifier = Modifier,
) {
    val contrastLine = remember(violation) { ReportHelper.contrastColorLine(violation) }
    if (contrastLine != null && line == contrastLine) {
        ContrastColorLine(violation = violation, modifier = modifier)
    } else {
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
            modifier = modifier,
        )
    }
}

@Composable
fun ContrastColorLine(
    violation: AccessibilityViolation,
    modifier: Modifier = Modifier,
) {
    val fg = violation.foregroundColorHex?.takeIf { it.isNotBlank() }
    val bg = violation.backgroundColorHex?.takeIf { it.isNotBlank() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Colori:", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        Text("primo piano", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        ColorSwatch(hex = fg ?: "#00000000")
        Text(
            fg ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
            fontWeight = FontWeight.Medium,
        )
        Text("·", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        Text("sfondo", style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        ColorSwatch(hex = bg ?: "#00000000")
        Text(
            bg ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = contentSecondary(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun ViolationExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Comprimi sezione" else "Espandi sezione",
                tint = contentSecondary(),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** Righe tecniche escludendo la riga colori (mostrata separatamente per contrasto). */
fun technicalDetailLines(violation: AccessibilityViolation): List<String> =
    ReportHelper.violationDetailLines(violation).filter { line ->
        val contrast = ReportHelper.contrastColorLine(violation)
        contrast == null || line != contrast
    }

fun isContrastViolation(violation: AccessibilityViolation): Boolean =
    violation.type == ViolationType.LOW_COLOR_CONTRAST ||
        violation.type == ViolationType.LOW_NON_TEXT_CONTRAST
