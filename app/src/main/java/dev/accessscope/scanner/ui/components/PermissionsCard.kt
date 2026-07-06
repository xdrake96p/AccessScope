package dev.accessscope.scanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.ui.theme.BrandLight
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.util.PermissionHelper

@Composable
fun PermissionsCard(
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val grantedCount = listOf(accessibilityGranted, overlayGranted).count { it }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Permessi richiesti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$grantedCount/2", color = if (grantedCount == 2) Success else Danger, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { grantedCount / 2f },
                modifier = Modifier.fillMaxWidth(),
                color = BrandPrimary,
            )

            AccessibilityPermissionBlock(
                granted = accessibilityGranted,
                onOpenSettings = {
                    context.startActivity(
                        PermissionHelper.accessibilityServiceIntent(
                            context,
                            AccessScopeAccessibilityService::class.java,
                        ),
                    )
                },
            )

            PermissionRow(
                label = "Mostra sopra altre app",
                description = "Per il pulsante STOP durante la scansione",
                granted = overlayGranted,
                icon = Icons.Outlined.Layers,
                actionLabel = "Apri impostazioni overlay",
                onOpen = {
                    context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                },
            )

            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Ho completato — aggiorna stato")
            }
        }
    }
}

@Composable
private fun AccessibilityPermissionBlock(
    granted: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (granted) BrandLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Accessibility, contentDescription = null, tint = BrandPrimary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("AccessScope — Servizio di accessibilità", fontWeight = FontWeight.SemiBold)
                Text(
                    if (granted) "Attivo ✓" else "Da attivare",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) Success else Danger,
                )
            }
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (granted) Success else Danger,
            )
        }

        AnimatedVisibility(visible = !granted) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Cosa fare (in ordine):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                InstructionStep(1, "Tocca il pulsante qui sotto")
                InstructionStep(2, "Cerca «AccessScope» nella lista")
                InstructionStep(3, "Su Samsung: Accessibilità → Servizi installati → AccessScope")
                InstructionStep(4, "Attiva «Usa AccessScope» e conferma")
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apri impostazioni AccessScope")
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$number.",
            modifier = Modifier.width(22.dp),
            fontWeight = FontWeight.Bold,
            color = BrandPrimary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BrandPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (granted) Success else Danger,
            )
        }
        if (!granted) {
            Text(
                "→ $actionLabel",
                modifier = Modifier.padding(start = 36.dp, top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = BrandPrimary,
            )
        }
    }
}
