package dev.accessscope.scanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Warning
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
                Text("Permessi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$grantedCount/2", color = if (grantedCount == 2) Success else Danger)
            }
            LinearProgressIndicator(
                progress = { grantedCount / 2f },
                modifier = Modifier.fillMaxWidth(),
                color = BrandPrimary,
            )
            PermissionRow(
                label = "Servizio di accessibilità",
                description = "Necessario per analizzare le app target",
                granted = accessibilityGranted,
                icon = Icons.Outlined.Accessibility,
                onOpen = {
                    context.startActivity(PermissionHelper.accessibilitySettingsIntent(context))
                    onRefresh()
                },
            )
            PermissionRow(
                label = "Sovrapposizione",
                description = "Mostra il pulsante STOP flottante",
                granted = overlayGranted,
                icon = Icons.Outlined.Layers,
                onOpen = {
                    context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                    onRefresh()
                },
            )
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Aggiorna stato permessi")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
}
