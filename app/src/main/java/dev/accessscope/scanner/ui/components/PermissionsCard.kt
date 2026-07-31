/**
 * Card permessi con icone di stato professionali e raggruppamento chiaro.
 *
 * Tratta lo stato «toggle ON ma servizio non collegato» (post force-stop Samsung)
 * come non pronto: badge warning + CTA di recovery OFF→ON.
 */
package dev.accessscope.scanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import dev.accessscope.scanner.ui.accessibility.asSectionHeading
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.ControlShape
import dev.accessscope.scanner.ui.theme.Danger
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.Warning
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.util.PermissionHelper

/**
 * Stato visuale di una riga permesso.
 */
private enum class PermissionVisualStatus {
    /** Pronto all’uso. */
    Ok,

    /** Toggle ON ma servizio unbound — recovery OFF→ON. */
    Warning,

    /** Da concedere. */
    Missing,
}

/**
 * Card Home con stato accessibilità (incluso unbound) e overlay.
 *
 * @param accessibilityGranted Toggle accessibilità ON nelle Settings.
 * @param accessibilityConnected Istanza servizio viva (`onServiceConnected`).
 * @param overlayGranted Permesso «mostra sopra altre app».
 * @param onRefresh Ricarica lo stato permessi.
 * @param modifier Modifier Compose.
 */
@Composable
fun PermissionsCard(
    accessibilityGranted: Boolean,
    accessibilityConnected: Boolean,
    overlayGranted: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val a11yReady = accessibilityGranted && accessibilityConnected
    val a11yStatus = when {
        a11yReady -> PermissionVisualStatus.Ok
        accessibilityGranted -> PermissionVisualStatus.Warning
        else -> PermissionVisualStatus.Missing
    }
    val readyCount = listOf(a11yReady, overlayGranted).count { it }

    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Permessi richiesti",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.asSectionHeading(),
                )
                Text(
                    "Necessari per analizzare altre app",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
            PermissionStatusBadge(ok = readyCount == 2, label = "$readyCount/2")
        }

        LinearProgressIndicator(
            progress = { readyCount / 2f },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Permessi pronti: $readyCount su 2"
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        PermissionItemCard(
            title = "Servizio di accessibilità",
            subtitle = when (a11yStatus) {
                PermissionVisualStatus.Ok -> "Attivo e connesso"
                PermissionVisualStatus.Warning ->
                    "ON ma non collegato (tipico dopo update) — OFF → attendi → ON"
                PermissionVisualStatus.Missing -> "Da attivare nelle impostazioni"
            },
            status = a11yStatus,
            icon = Icons.Outlined.Accessibility,
            actionLabel = if (a11yStatus == PermissionVisualStatus.Warning) {
                "Ripristina collegamento"
            } else {
                "Apri impostazioni AccessScope"
            },
            onAction = {
                PermissionHelper.safeStartSettingsIntent(
                    context,
                    PermissionHelper.accessibilityServiceIntent(
                        context,
                        AccessScopeAccessibilityService::class.java,
                    ),
                )
            },
            steps = when (a11yStatus) {
                PermissionVisualStatus.Missing -> listOf(
                    "Tocca il pulsante qui sotto",
                    "Cerca «AccessScope» nella lista",
                    "Attiva e conferma con Consenti",
                )
                PermissionVisualStatus.Warning -> listOf(
                    "Tocca «Ripristina collegamento»",
                    "Disattiva AccessScope e attendi 2 secondi",
                    "Riattiva e tocca Consenti, poi torna qui",
                )
                PermissionVisualStatus.Ok -> emptyList()
            },
        )

        PermissionItemCard(
            title = "Mostra sopra altre app",
            subtitle = if (overlayGranted) {
                "Overlay STOP disponibile"
            } else {
                "Per il pulsante STOP in scansione"
            },
            status = if (overlayGranted) PermissionVisualStatus.Ok else PermissionVisualStatus.Missing,
            icon = Icons.Outlined.Layers,
            actionLabel = "Apri impostazioni overlay",
            onAction = {
                PermissionHelper.safeStartSettingsIntent(
                    context,
                    PermissionHelper.overlaySettingsIntent(context),
                    fallback = PermissionHelper.appDetailsIntent(context),
                    fallbackToast = "Aperta schermata dettaglio app",
                )
            },
        )

        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            shape = ControlShape,
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Aggiorna stato permessi")
        }
    }
}

@Composable
private fun PermissionStatusBadge(ok: Boolean, label: String) {
    val color = if (ok) Success else Danger
    Row(
        modifier = Modifier
            .clip(ControlShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    subtitle: String,
    status: PermissionVisualStatus,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    steps: List<String> = emptyList(),
) {
    val accent: Color = when (status) {
        PermissionVisualStatus.Ok -> Success
        PermissionVisualStatus.Warning -> Warning
        PermissionVisualStatus.Missing -> MaterialTheme.colorScheme.outlineVariant
    }
    val statusIcon = when (status) {
        PermissionVisualStatus.Ok -> Icons.Outlined.CheckCircle
        PermissionVisualStatus.Warning -> Icons.Outlined.Warning
        PermissionVisualStatus.Missing -> Icons.Outlined.Cancel
    }
    val statusTint = when (status) {
        PermissionVisualStatus.Ok -> Success
        PermissionVisualStatus.Warning -> Warning
        PermissionVisualStatus.Missing -> Danger
    }
    val statusCd = when (status) {
        PermissionVisualStatus.Ok -> "$title concesso"
        PermissionVisualStatus.Warning -> "$title non collegato"
        PermissionVisualStatus.Missing -> "$title non concesso"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .border(1.dp, accent.copy(alpha = 0.45f), ControlShape)
            .background(
                when (status) {
                    PermissionVisualStatus.Ok -> Success.copy(alpha = 0.06f)
                    PermissionVisualStatus.Warning -> Warning.copy(alpha = 0.10f)
                    PermissionVisualStatus.Missing ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = BrandPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
            }
            Icon(
                imageVector = statusIcon,
                contentDescription = statusCd,
                tint = statusTint,
                modifier = Modifier.size(22.dp),
            )
        }
        AnimatedVisibility(steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEachIndexed { index, text ->
                    InstructionStep(index + 1, text)
                }
            }
        }
        if (status != PermissionVisualStatus.Ok) {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
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
