/**
 * Hero card della home "Scanner & HUD": titolo, conteggio app e CTA scansione.
 *
 * CTA disabilitata: surface @ 90% + onSurfaceVariant sul primary, per contrasto WCAG AA.
 */
package dev.accessscope.scanner.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.PillShape

/**
 * Card principale con CTA "Avvia/Ferma scansione" su sfondo primary con griglia HUD.
 *
 * @param selectedCount Numero di app attualmente selezionate per la scansione.
 * @param isScanning True mentre una scansione è in corso (CTA → "Ferma scansione").
 * @param canStart True se si può avviare (app selezionata + permessi concessi).
 * @param disabledHint Motivo per cui la scansione non può partire (accessibilità).
 * @param onStart Avvia la scansione.
 * @param onStop Ferma la scansione in corso.
 */
@Composable
internal fun HomeHeroCard(
    selectedCount: Int,
    isScanning: Boolean,
    canStart: Boolean,
    disabledHint: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = Color.White.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.primary)
            .drawBehind {
                // Pattern a griglia HUD
                val step = 40.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }
            .padding(24.dp),
    ) {
        Column {
            Text(
                if (isScanning) "Scansione in corso…" else "Pronto all'analisi?",
                fontFamily = HankenGroteskFamily,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$selectedCount app selezionate per la scansione",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(20.dp))
            if (isScanning) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ferma scansione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Avvia scansione"
                            if (!canStart && disabledHint != null) stateDescription = disabledHint
                        },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        // Surface opaca sul primary: ≥4.5:1 col testo (il teal@0.4 falliva ~1.5:1).
                        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Avvia scansione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
