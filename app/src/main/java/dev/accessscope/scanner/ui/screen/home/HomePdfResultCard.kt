package dev.accessscope.scanner.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.ui.components.AccessScopeCard
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CodeTextStyle
import dev.accessscope.scanner.ui.theme.contentSecondary

@Composable
internal fun PdfResultCard(
    path: String,
    onOpenPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessScopeCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = BrandPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Report PDF salvato", fontWeight = FontWeight.SemiBold)
                Text(path, style = CodeTextStyle, color = contentSecondary())
            }
        }
        TextButton(onClick = onOpenPdf) {
            Text("Apri file PDF")
        }
    }
}
