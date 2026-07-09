/**
 * Header hero e card "Cosa analizziamo" — gradiente brand Navy→Teal.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.PictureAsPdf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.R
import dev.accessscope.scanner.ui.accessibility.asSectionHeading
import dev.accessscope.scanner.ui.theme.AccessScopeMotion
import dev.accessscope.scanner.ui.theme.ChipShape
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.HeroShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.theme.headerGradient

/** Testo ad alto contrasto su sfondo gradiente scuro (#F5F5F5 su Navy). */
private val HeroTextPrimary = Color(0xFFF5F5F5)

/** Sottotitolo header — bianco al 90% per leggibilità WCAG AA. */
private val HeroTextSecondary = Color(0xE6FFFFFF)

/** Testo chip su sfondo chiaro semi-trasparente. */
private val HeroChipText = Color(0xFF0D2C54)

@Composable
fun HeroHeader(
    selectedCount: Int,
    isScanning: Boolean,
    onOpenSettings: () -> Unit = {},
) {
    val gradient = headerGradient()
    val chipBg = Color.White.copy(alpha = 0.88f)
    val chipBgHighlight = Color.White.copy(alpha = 0.96f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(gradient)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .semantics {
                contentDescription = buildString {
                    append("AccessScope, WCAG scanner per sviluppatori Android. ")
                    append(if (isScanning) "Scansione attiva. " else "Pronto. ")
                    append(
                        if (selectedCount == 1) "1 app selezionata."
                        else "$selectedCount app selezionate.",
                    )
                }
            },
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
                    Image(
                        painter = painterResource(R.drawable.ic_access_scope_logo),
                        contentDescription = "Logo AccessScope",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CompactShape),
                        contentScale = ContentScale.Fit,
                    )
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "AccessScope",
                            style = MaterialTheme.typography.headlineMedium,
                            color = HeroTextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.asSectionHeading(),
                        )
                        Text(
                            "WCAG scanner per sviluppatori Android",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HeroTextSecondary,
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Impostazioni",
                        tint = HeroTextPrimary,
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
                        textColor = HeroChipText,
                        bg = if (scanning) chipBgHighlight else chipBg,
                    )
                }
                HeaderChip(
                    label = if (selectedCount == 1) "1 app selezionata" else "$selectedCount app selezionate",
                    textColor = HeroChipText,
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
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics { contentDescription = label },
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
            modifier = Modifier.asSectionHeading(),
        )
        Spacer(Modifier.size(4.dp))
        FeatureHighlightRow(Icons.Outlined.Visibility, "Etichette, contrasto colore, dimensione testo", "Etichette e contrasto")
        FeatureHighlightRow(Icons.Outlined.TouchApp, "Target di tocco, spaziatura, gerarchia titoli", "Target di tocco")
        FeatureHighlightRow(Icons.Outlined.RecordVoiceOver, "Simulazione TalkBack e report PDF", "TalkBack")
        FeatureHighlightRow(Icons.Outlined.Contrast, "37 controlli WCAG in tempo reale", "Controlli WCAG")
        FeatureHighlightRow(Icons.Outlined.PictureAsPdf, "Report PDF per team e audit legali", "Report PDF")
    }
}

@Composable
private fun FeatureHighlightRow(icon: ImageVector, text: String, iconLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
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
                contentDescription = iconLabel,
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
