/**
 * Visualizzatore immagine evidenza con supporto accessibilità.
 */
package dev.accessscope.scanner.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.EvidenceKind
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.contentSecondary
import java.io.File

/** Messaggio mostrato per evidenze wireframe su schermate protette. */
const val SECURE_SCREEN_EVIDENCE_MESSAGE =
    "Schermata protetta (PIN/dati sensibili): screenshot reale non disponibile. " +
        "Evidenza ricostruita dall'albero di accessibilità."

/**
 * Mostra il crop annotato o un messaggio se l'evidenza non è disponibile.
 */
@Composable
fun ViolationEvidenceViewer(
    violation: AccessibilityViolation,
    imagePath: String?,
    modifier: Modifier = Modifier,
) {
    val description = remember(violation) {
        buildString {
            append("Evidenza visiva: ${violation.type.displayName}")
            violation.bounds?.let { append(". Area $it") }
        }
    }

    val isSynthetic = violation.evidenceKind == EvidenceKind.SYNTHETIC_SECURE

    if (imagePath.isNullOrBlank() || !File(imagePath).exists()) {
        val apiOk = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        val hasScreenRef = violation.screenEvidenceId != null || violation.screenFingerprint != null
        Text(
            when {
                isSynthetic ->
                    SECURE_SCREEN_EVIDENCE_MESSAGE
                !apiOk ->
                    "Evidenza visiva non disponibile: richiede Android 11 o superiore."
                hasScreenRef ->
                    "Screenshot schermata non trovato in cache. Ripeti la scansione di questa schermata."
                else ->
                    "Nessuno screenshot acquisito durante la scansione per questa violazione."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = contentSecondary(),
            modifier = modifier,
        )
        return
    }

    val imageBitmap = remember(imagePath) {
        BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
    }
    if (imageBitmap == null) {
        Text(
            "Impossibile caricare l'immagine evidenza.",
            style = MaterialTheme.typography.bodyMedium,
            color = contentSecondary(),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier) {
        if (isSynthetic) {
            Text(
                SECURE_SCREEN_EVIDENCE_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Image(
            bitmap = imageBitmap,
            contentDescription = description,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(CompactShape),
            contentScale = ContentScale.Fit,
        )
    }
}
