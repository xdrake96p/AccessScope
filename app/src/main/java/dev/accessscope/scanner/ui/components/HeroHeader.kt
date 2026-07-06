/**
 * Header hero e card "Cosa analizziamo" — gradiente ciano/viola premium.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.ChipShape
import dev.accessscope.scanner.ui.theme.HeroShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.headerGradient

@Composable
fun HeroHeader(
    selectedCount: Int,
    isScanning: Boolean,
    onOpenSettings: () -> Unit = {},
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val onHero = if (isDark) Color.White else Color(0xFF0F172A)
    val onHeroMuted = onHero.copy(alpha = 0.78f)
    val chipBg = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f)
    val chipBgHighlight = if (isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.75f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(headerGradient())
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.65f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Radar,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "AccessScope",
                            style = MaterialTheme.typography.headlineMedium,
                            color = onHero,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "WCAG scanner per sviluppatori Android",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onHeroMuted,
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.55f),
                        ),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Impostazioni",
                        tint = onHero,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(
                    targetState = isScanning,
                    transitionSpec = {
                        fadeIn(AccessScopeMotion.fadeInTween) togetherWith fadeOut(AccessScopeMotion.screenExitTween)
                    },
                    label = "scan_status_chip",
                ) { scanning ->
                    HeaderChip(
                        label = if (scanning) "Scansione attiva" else "Pronto",
                        highlight = scanning,
                        textColor = onHero,
                        bg = if (scanning) chipBgHighlight else chipBg,
                    )
                }
                HeaderChip(
                    label = "$selectedCount app selezionate",
                    textColor = onHero,
                    bg = chipBg,
                )
            }
        }
    }
}

@Composable
private fun HeaderChip(
    label: String,
    highlight: Boolean = false,
    textColor: Color,
    bg: Color,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(ChipShape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = textColor,
        fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
    )
}

@Composable
fun FeatureHighlights(modifier: Modifier = Modifier) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Text(
            "Cosa analizziamo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(4.dp))
        FeatureHighlightRow(Icons.Outlined.Visibility, "Etichette, contrasto colore, dimensione testo")
        FeatureHighlightRow(Icons.Outlined.TouchApp, "Target di tocco, spaziatura, gerarchia titoli")
        FeatureHighlightRow(Icons.Outlined.RecordVoiceOver, "Simulazione TalkBack e report PDF")
        FeatureHighlightRow(Icons.Outlined.Contrast, "37 controlli WCAG in tempo reale")
        FeatureHighlightRow(Icons.Outlined.PictureAsPdf, "Report PDF per team e audit legali")
    }
}

@Composable
private fun FeatureHighlightRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentSecondary(),
            modifier = Modifier.weight(1f),
        )
    }
}
