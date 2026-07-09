/**
 * Modifieri Compose riutilizzabili per accessibilità WCAG (heading, target tocco, stato).
 */
package dev.accessscope.scanner.ui.accessibility

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Marca il nodo come intestazione per la navigazione TalkBack. */
fun Modifier.asSectionHeading(): Modifier = semantics { heading() }

/** Descrive lo stato corrente di un controllo (es. switch, badge). */
fun Modifier.a11yStateDescription(description: String): Modifier =
    semantics { stateDescription = description }

/** Garantisce area di tocco minima 48×48 dp (WCAG 2.5.5). */
fun Modifier.minimumTouchTargetSize(): Modifier =
    defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)

/** Sovrascrive la descrizione annunciata da TalkBack. */
fun Modifier.a11yContentDescription(description: String): Modifier =
    semantics { contentDescription = description }
