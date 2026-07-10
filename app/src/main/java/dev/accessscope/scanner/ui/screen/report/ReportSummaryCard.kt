package dev.accessscope.scanner.ui.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.report.ReportHelper
import dev.accessscope.scanner.ui.theme.BrandDark
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.BrandSecondary
import dev.accessscope.scanner.ui.theme.contentSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Card di riepilogo con punteggio, gradiente brand e metriche della sessione.
 */
@Composable
internal fun ReportSummaryCard(
    score: Int,
    scannedScreens: Int,
    scanAnalyses: Int,
    scanScopeLabel: String,
    appCount: Int,
    violationCount: Int,
    talkBackCount: Int,
    passedCheckCount: Int = 0,
) {
    val date = remember {
        SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ITALY).format(Date())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(BrandDark, BrandPrimary, BrandSecondary)),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AccessScope", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Punteggio stimato: $score/100",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    ReportHelper.scoreLabel(score),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRow("Data scansione", date)
            SummaryRow("App controllate", "$appCount")
            SummaryRow("Ambiti analizzati", scanScopeLabel)
            SummaryRow("Schermate uniche", "$scannedScreens")
            if (scanAnalyses > scannedScreens) {
                SummaryRow("Analisi eseguite", "$scanAnalyses")
            }
            SummaryRow("Problemi trovati", "$violationCount")
            if (passedCheckCount > 0) {
                SummaryRow("Controlli superati", "$passedCheckCount")
            }
            SummaryRow("Note screen reader", "$talkBackCount")
        }
    }
}

@Composable
internal fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = contentSecondary(), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
