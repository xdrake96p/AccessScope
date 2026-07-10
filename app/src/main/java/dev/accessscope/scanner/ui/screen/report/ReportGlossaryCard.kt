package dev.accessscope.scanner.ui.screen.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.contentSecondary

@Composable
internal fun GlossaryCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val terms = listOf(
        "Colori nel report" to "🔴 critico · 🟠 grave · 🟡 medio · ⚪ lieve. Il verde è solo per le metriche OK in dashboard.",
        "TalkBack" to "Lettore vocale di Android: legge ad alta voce cosa tocchi.",
        "WCAG" to "Linee guida internazionali per rendere siti e app usabili da tutti.",
        "Contrasto" to "Differenza tra colore testo e sfondo: più alto = più leggibile.",
        "Target di tocco" to "Area premibile: deve essere abbastanza grande (circa 48×48 px).",
        "contentDescription" to "Testo che TalkBack legge al posto di un'icona o immagine.",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = CardShape,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Glossario rapido", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(AccessScopeMotion.fadeInTween),
                exit = fadeOut(AccessScopeMotion.screenExitTween),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    terms.forEach { (term, definition) ->
                        Text(term, fontWeight = FontWeight.Medium, color = BrandPrimary)
                        Text(definition, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = contentSecondary())
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
