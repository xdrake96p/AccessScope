/**
 * Dettaglio singola anomalia (tab Dettagli): hero, anteprima, azioni correttive, evidenza.
 */
package dev.accessscope.scanner.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.ui.accessibility.asSectionHeading
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.components.AccessScopeTopBar
import dev.accessscope.scanner.ui.components.ContrastColorLine
import dev.accessscope.scanner.ui.components.SeverityChip
import dev.accessscope.scanner.ui.components.ViolationDetailLine
import dev.accessscope.scanner.ui.components.ViolationEvidenceViewer
import dev.accessscope.scanner.ui.components.isContrastViolation
import dev.accessscope.scanner.ui.components.parseHexColor
import dev.accessscope.scanner.ui.components.technicalDetailLines
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import dev.accessscope.scanner.ui.viewmodel.ScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schermata di dettaglio di una violazione (tab Dettagli della zona sessione).
 */
@Composable
fun ViolationDetailScreen(
    dedupeKey: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    sessionId: String? = null,
) {
    val context = LocalContext.current
    val violation = remember(dedupeKey, sessionId) {
        viewModel.findViolation(dedupeKey, sessionId)
    }
    var imagePath by remember(violation) { mutableStateOf(violation?.evidenceImagePath) }

    LaunchedEffect(violation, sessionId) {
        val v = violation ?: return@LaunchedEffect
        if (imagePath.isNullOrBlank() || !java.io.File(imagePath!!).exists()) {
            imagePath = withContext(Dispatchers.IO) {
                viewModel.resolveEvidencePath(v, sessionId)
            }
        }
    }

    Scaffold(
        topBar = {
            AccessScopeTopBar(
                title = "Dettaglio anomalia",
                onBack = onBack,
            )
        },
        bottomBar = {
            if (violation != null) {
                ShareBottomBar(onShare = { shareViolation(context, violation, viewModel.packageLabel(violation.packageName)) })
            }
        },
        modifier = Modifier.navigationBarsPadding(),
    ) { padding ->
        if (violation == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text("Violazione non trovata nella sessione corrente.")
            }
            return@Scaffold
        }

        ViolationDetailContent(
            violation = violation,
            imagePath = imagePath,
            packageLabel = viewModel.packageLabel(violation.packageName),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

@Composable
private fun ViolationDetailContent(
    violation: AccessibilityViolation,
    imagePath: String?,
    packageLabel: String,
    modifier: Modifier = Modifier,
) {
    val type = violation.type
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero: chip gravità + ID + titolo + descrizione
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(severity = type.severity)
                Text(
                    "ID: AS-${violation.dedupeKey.hashCode().toUInt().toString(16).uppercase().takeLast(6)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = contentSecondary(),
                )
            }
            Text(
                type.displayName,
                fontFamily = HankenGroteskFamily,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.asSectionHeading(),
            )
            Text(type.plainHint, style = MaterialTheme.typography.bodyLarge, color = contentSecondary())
        }

        // Anteprima visiva before/after (solo violazioni di contrasto con colori noti)
        if (isContrastViolation(violation)) {
            ContrastBeforeAfterCard(violation = violation)
        }

        // Azioni correttive numerate
        CorrectiveActionsCard(violation = violation)

        // Pro tip
        ProTipCard(type = type)

        // Evidenza visiva
        AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Evidenza visiva",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ViolationEvidenceViewer(
                violation = violation,
                imagePath = imagePath,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!violation.hasSpatialBounds()) {
                Text(
                    "Problema a livello schermata: l'evidenza mostra la schermata completa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
        }

        // Bento tecnico
        TechnicalBento(violation = violation, packageLabel = packageLabel)
    }
}

/** Card "Anteprima Visiva": stato attuale vs correzione suggerita per il contrasto. */
@Composable
private fun ContrastBeforeAfterCard(violation: AccessibilityViolation) {
    AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Anteprima visiva",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContrastPreviewCell(
                label = "STATO ATTUALE${violation.measuredValue?.let { " ($it)" } ?: ""}",
                background = parseHexColor(violation.backgroundColorHex.orEmpty()),
                foreground = parseHexColor(violation.foregroundColorHex.orEmpty()),
                tag = "INACCESSIBILE",
                tagColor = MaterialTheme.colorScheme.error,
                dashed = true,
                modifier = Modifier.weight(1f),
            )
            ContrastPreviewCell(
                label = "CORREZIONE${violation.requiredValue?.let { " ($it)" } ?: ""}",
                background = MaterialTheme.colorScheme.primary,
                foreground = MaterialTheme.colorScheme.onPrimary,
                tag = "CONFORME",
                tagColor = MaterialTheme.colorScheme.primary,
                dashed = false,
                modifier = Modifier.weight(1f),
            )
        }
        ContrastColorLine(violation = violation)
    }
}

@Composable
private fun ContrastPreviewCell(
    label: String,
    background: androidx.compose.ui.graphics.Color?,
    foreground: androidx.compose.ui.graphics.Color?,
    tag: String,
    tagColor: androidx.compose.ui.graphics.Color,
    dashed: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = contentSecondary(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CompactShape)
                .then(
                    if (dashed) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            CompactShape,
                        )
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CompactShape)
                    },
                )
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CompactShape)
                    .background(background ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Testo esempio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = foreground ?: MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = tagColor,
                    )
                }
            }
        }
    }
}

/** Card "Azioni Correttive" con passi numerati dal campo remediation della violazione. */
@Composable
private fun CorrectiveActionsCard(violation: AccessibilityViolation) {
    val steps = remember(violation) {
        violation.remediation
            ?.lines()
            ?.map { it.trim().removePrefix("- ").removeSuffix(".") }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(violation.type.plainHint)
    }
    AccessScopeCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Azioni correttive",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index < steps.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

/** Box "Pro Tip" con suggerimento specifico per tipo di violazione. */
@Composable
private fun ProTipCard(type: ViolationType) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
            .padding(14.dp),
    ) {
        Icon(
            Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "PRO TIP",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                proTipFor(type),
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary(),
            )
        }
    }
}

/** Bento tecnico: elemento target, riferimento WCAG, rilevamento. */
@Composable
private fun TechnicalBento(violation: AccessibilityViolation, packageLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TechCard(
            title = "ELEMENTO TARGET",
            modifier = Modifier.weight(1f),
        ) {
            Text(
                violation.viewId?.substringAfterLast('/') ?: violation.viewClassName.substringAfterLast('.'),
                style = CodeTextStyle,
                maxLines = 2,
            )
        }
        TechCard(
            title = "RIFERIMENTO",
            modifier = Modifier.weight(1f),
        ) {
            Text(violation.wcagReference, style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMonoFamily)
            Text(
                "${violation.type.severity.label} · ${(violation.confidence * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary(),
            )
        }
        TechCard(
            title = "RILEVAMENTO",
            modifier = Modifier.weight(1f),
        ) {
            Text(
                SimpleDateFormat("dd MMM, HH:mm", Locale.ITALY).format(Date(violation.timestampMs)),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFamily,
            )
            Text(
                violation.screenTitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary(),
                maxLines = 2,
            )
        }
    }
    technicalDetailLines(violation).forEach { line ->
        ViolationDetailLine(line = line, violation = violation)
    }
    Text(
        "App: $packageLabel",
        style = MaterialTheme.typography.labelSmall,
        color = contentSecondary(),
    )
}

@Composable
private fun TechCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(CompactShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = contentSecondary(),
        )
        content()
    }
}

/** Bottom bar con azione di condivisione del dettaglio. */
@Composable
private fun ShareBottomBar(onShare: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Condividi report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Condivide un riepilogo testuale della violazione via intent di sistema. */
private fun shareViolation(
    context: android.content.Context,
    violation: AccessibilityViolation,
    packageLabel: String,
) {
    val text = buildString {
        appendLine("AccessScope — ${violation.type.displayName} (${violation.type.severity.label})")
        appendLine("WCAG: ${violation.wcagReference}")
        appendLine("App: $packageLabel · Schermata: ${violation.screenTitle}")
        violation.measuredValue?.let { appendLine("Misurato: $it (richiesto: ${violation.requiredValue ?: "—"})") }
        violation.remediation?.let { appendLine("Suggerimento: $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Condividi report"))
}

/** Suggerimento pratico specifico per tipo di violazione. */
private fun proTipFor(type: ViolationType): String = when (type) {
    ViolationType.LOW_COLOR_CONTRAST, ViolationType.LOW_NON_TEXT_CONTRAST ->
        "Un font più pesante (bold) può migliorare marginalmente la leggibilità, ma non sostituisce il rapporto di contrasto richiesto dalle WCAG."
    ViolationType.SMALL_TOUCH_TARGET, ViolationType.INSUFFICIENT_TOUCH_SPACING ->
        "Spesso basta aumentare il padding attorno al contenuto: il target minimo consigliato è 48×48dp con spaziatura adeguata."
    ViolationType.MISSING_LABEL, ViolationType.IMAGE_MISSING_ALT ->
        "Descrivi l'azione, non l'aspetto: «Conferma pagamento» è meglio di «Icona blu con freccia»."
    ViolationType.TEXT_TOO_SMALL, ViolationType.TEXT_TRUNCATED ->
        "Testa il layout con Scala caratteri di sistema al 130%: nessun contenuto deve essere tagliato."
    else ->
        "Correggere questo elemento migliora anche il punteggio complessivo della schermata."
}
