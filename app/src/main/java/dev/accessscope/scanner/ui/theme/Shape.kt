/**
 * Forme arrotondate uniformi del design system AccessScope (Material 3).
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Raggio standard card e contenitori (16dp). */
val CardShape = RoundedCornerShape(16.dp)

/** Raggio header e sezioni hero (24dp, allineato a bg_header_gradient). */
val HeroShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/** Raggio pulsanti primari e barra di ricerca (14dp). */
val ControlShape = RoundedCornerShape(14.dp)

/** Raggio chip e badge compatti (20dp). */
val ChipShape = RoundedCornerShape(20.dp)

/** Raggio elementi compatti interni (12dp). */
val CompactShape = RoundedCornerShape(12.dp)

/** Forme Material 3 collegate al tema globale. */
val AccessScopeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = CompactShape,
    medium = ControlShape,
    large = CardShape,
    extraLarge = HeroShape,
)
