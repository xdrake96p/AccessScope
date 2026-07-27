/**
 * Tutorial iniziale AccessScope: 6 pagine con pager, dots, skip e "non mostrare più".
 */
package dev.accessscope.scanner.ui.screen.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.R
import dev.accessscope.scanner.data.ViolationArea
import dev.accessscope.scanner.ui.theme.CardShape
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.PillShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 6

/**
 * Tutorial a 6 pagine mostrato al primo avvio.
 *
 * @param onFinish Invocato alla chiusura; `dontShowAgain = true` se l'utente
 * ha saltato il tutorial o ha spuntato "non mostrare più".
 */
@Composable
fun OnboardingScreen(onFinish: (dontShowAgain: Boolean) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var dontShowAgain by remember { mutableStateOf(true) }
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header con skip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onFinish(true) }) {
                Text("Salta tutto", style = MaterialTheme.typography.labelLarge)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> ProblemPage()
                2 -> EcosystemPage()
                3 -> HowItWorksPage()
                4 -> WcagAreasPage()
                else -> ResultsPage()
            }
        }

        // Footer: dots, checkbox, CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(currentPage = pagerState.currentPage)
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                )
                Text(
                    "Non mostrare più al prossimo avvio",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentSecondary(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish(dontShowAgain)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    if (isLastPage) "Inizia subito" else "Avanti",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

/** Dots di avanzamento con glow sull'attivo. */
@Composable
private fun PageDots(currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGE_COUNT) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

/** Contenitore pagina scrollabile con titolo e sottotitolo opzionali. */
@Composable
private fun OnboardingPage(
    title: @Composable () -> Unit,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        title()
        subtitle?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = contentSecondary(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun PageTitle(text: String, accent: String? = null) {
    Text(
        text,
        fontFamily = HankenGroteskFamily,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    accent?.let {
        Text(
            it,
            fontFamily = HankenGroteskFamily,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/** Pagina 1 — Benvenuto. */
@Composable
private fun WelcomePage() {
    OnboardingPage(
        title = { PageTitle("Benvenuto in", "AccessScope") },
        subtitle = "Il toolkit per audit di accessibilità WCAG in tempo reale, progettato per sviluppatori Android.",
    ) {
        Image(
            painter = painterResource(R.drawable.ic_access_scope_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(24.dp))
        FeatureRow(Icons.AutoMirrored.Outlined.Label, "Etichette & Contrasto", "Testo, nomi accessibili e leggibilità.")
        FeatureRow(Icons.Outlined.TouchApp, "Target di tocco", "Dimensioni, spaziatura e aree cliccabili.")
        FeatureRow(Icons.Outlined.VerifiedUser, "Controlli WCAG", "40 tipi di violazione rilevati in tempo reale.")
    }
}

/** Pagina 2 — Il problema. */
@Composable
private fun ProblemPage() {
    OnboardingPage(
        title = { PageTitle("L'accessibilità viene testata tardi, raramente o mai.") },
        subtitle = "Nel ciclo di vita del software moderno l'inclusione è spesso un'aggiunta finale manuale invece che un requisito integrato.",
    ) {
        ProblemCard(Icons.Outlined.TimerOff, "Audit manuali lenti e costosi", "Controllare ogni schermata a mano richiede settimane e blocca il rilascio rapido delle feature.")
        ProblemCard(Icons.Outlined.VisibilityOff, "Tool statici limitati", "Molti strumenti analizzano solo il codice, ignorando il comportamento reale dell'app durante l'uso.")
        ProblemCard(Icons.Outlined.Warning, "Report frammentati", "I dati raccolti sono spesso tecnici e dispersivi: è difficile capire cosa correggere prima.")
    }
}

/** Pagina 3 — Ecosistema. */
@Composable
private fun EcosystemPage() {
    OnboardingPage(
        title = { PageTitle("Trova, Capisci, Correggi") },
        subtitle = "Un ecosistema integrato: dalla scansione in tempo reale alla correzione assistita dall'IA.",
    ) {
        ProblemCard(Icons.Outlined.Smartphone, "App mobile", "Analisi on-device delle barriere di accessibilità mentre navighi l'app da testare.")
        ProblemCard(Icons.Outlined.Terminal, "Plugin Android Studio", "Installa l'app sul device, avvia la scansione e recupera i risultati direttamente nell'IDE.")
        ProblemCard(Icons.Outlined.AutoFixHigh, "VS Code / Cursor", "Estensione gemella per l'editor, con gli stessi comandi del plugin Android Studio.")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "Fix intelligenti con un click: AccessScope genera il prompt perfetto per la tua IA preferita con contesto e riferimenti WCAG.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Pagina 4 — Come funziona. */
@Composable
private fun HowItWorksPage() {
    OnboardingPage(
        title = { PageTitle("Come funziona") },
        subtitle = "Cinque passi per un audit completo, eseguito interamente sul tuo device.",
    ) {
        StepRow(1, Icons.Outlined.TouchApp, "Seleziona app", "Scegli l'app da analizzare tra quelle installate.")
        StepRow(2, Icons.Outlined.PlayCircle, "Avvia scansione", "AccessScope attiva il monitoraggio in tempo reale.")
        StepRow(3, Icons.Outlined.Explore, "Navighi tu l'app", "Apri menu, form e dialoghi: ogni schermata viene analizzata mentre la usi.")
        StepRow(4, Icons.Outlined.StopCircle, "Stop scansione", "Premi STOP dall'overlay quando hai finito.")
        StepRow(5, Icons.Outlined.CheckCircle, "Report & Fix", "Leggi il report, esporta il PDF o copia il prompt AI per correggere.")
    }
}

/** Pagina 5 — Le 8 aree WCAG controllate. */
@Composable
private fun WcagAreasPage() {
    OnboardingPage(
        title = { PageTitle("Cosa controlliamo") },
        subtitle = "Otto pilastri dell'accessibilità WCAG, verificati su ogni schermata che visiti.",
    ) {
        ViolationArea.entries.forEach { area ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(CompactShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(area.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(area.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(area.subtitle, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
                }
            }
        }
    }
}

/** Pagina 6 — Risultati certificati (benchmark reale Nexi). */
@Composable
private fun ResultsPage() {
    OnboardingPage(
        title = { PageTitle("Risultati certificati") },
        subtitle = "Sul benchmark reale dell'app Nexi, l'analisi AccessScope riduce il rumore con precisione chirurgica.",
    ) {
        // Confronto baseline vs attuale
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("BENCHMARK PRESTAZIONI", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily, color = contentSecondary())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(" 95.6% RUMORE IN MENO", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            BenchmarkBar(label = "Baseline (tool standard)", value = "136 problemi", fraction = 1f, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            BenchmarkBar(label = "Analisi AccessScope", value = "6 veri positivi", fraction = 0.05f, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultStat("92/100", "SCORE WCAG", Modifier.weight(1f))
            ResultStat("127", "TEST JVM", Modifier.weight(1f))
            ResultStat("100%", "PRECISIONE", Modifier.weight(1f))
        }
    }
}

@Composable
private fun BenchmarkBar(label: String, value: String, fraction: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontFamily = JetBrainsMonoFamily)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(PillShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ResultStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CompactShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontFamily = HankenGroteskFamily, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily, color = contentSecondary())
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CardShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CompactShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        }
    }
}

@Composable
private fun ProblemCard(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        }
    }
}

@Composable
private fun StepRow(number: Int, icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (number == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (number == 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "PASSO 0$number",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = if (number == 3) MaterialTheme.colorScheme.primary else contentSecondary(),
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = contentSecondary())
        }
    }
}
