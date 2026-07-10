/**
 * Estensioni geometriche condivise tra i moduli precision.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.NodeSnapshot

internal fun Rect.area(): Int = width() * height()

internal fun NodeSnapshot.area(): Int = bounds.width() * bounds.height()
