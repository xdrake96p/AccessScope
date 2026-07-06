package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.BrandLight
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.HeaderGradient
import dev.accessscope.scanner.ui.theme.TextSecondary

@Composable
fun HeroHeader(
    selectedCount: Int,
    isScanning: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(HeaderGradient)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Radar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "AccessScope",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "WCAG scanner per sviluppatori Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderChip(
                    label = if (isScanning) "Scansione attiva" else "Pronto",
                    highlight = isScanning,
                )
                HeaderChip(label = "$selectedCount app selezionate")
            }
        }
    }
}

@Composable
private fun HeaderChip(label: String, highlight: Boolean = false) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (highlight) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (highlight) Color.White else Color.White.copy(alpha = 0.92f),
    )
}

@Composable
fun FeatureHighlights() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrandLight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Cosa analizziamo", fontWeight = FontWeight.SemiBold, color = BrandPrimary)
        Text("• Etichette, contrasto colore, dimensione testo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("• Target di tocco, spaziatura, gerarchia titoli", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("• Simulazione TalkBack e report PDF", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
