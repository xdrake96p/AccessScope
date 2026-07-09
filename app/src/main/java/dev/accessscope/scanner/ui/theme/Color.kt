/**
 * Palette premium AccessScope — ciano, lavanda, viola (WCAG AAA).
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// —— Accenti brand ——
/** Ciano vibrante (dark) / ciano soft (light primary). */
val CyanAccent = Color(0xFF06B6D4)

/** Ciano chiaro per gradienti light. */
val CyanSoft = Color(0xFF22D3EE)

/** Lavanda — accento secondario light. */
val LavenderAccent = Color(0xFFA78BFA)

/** Viola profondo — accento dark/gradiente. */
val VioletDeep = Color(0xFF6D28D9)

/** Viola scuro per testo su container. */
val VioletDark = Color(0xFF5B21B6)

/** Compatibilità: primario brand. */
val BrandPrimary = Color(0xFF0891B2)

val BrandSecondary = Color(0xFF7C3AED)

val BrandDark = VioletDark

// —— Light surfaces ——
val SurfaceLight = Color(0xFFF8F9FA)

val CardSurface = Color(0xFFFFFFFF)

val BrandLight = Color(0xFFE0F2FE)

// —— Dark surfaces ——
/** Grigio scuro profondo (#121212) — evita nero puro. */
val SurfaceDark = Color(0xFF121212)

/** Card dark — leggermente più chiara dello sfondo (#1E1E1E). */
val CardSurfaceDark = Color(0xFF1E1E1E)

val BrandLightDark = Color(0xFF1E3A5F)

// —— Text ——
/** Testo primario light — grigio scuro, non nero puro. */
val TextPrimary = Color(0xFF212529)

val TextSecondary = Color(0xFF495057)

/** Testo primario dark — grigio chiaro, non bianco puro. */
val TextPrimaryDark = Color(0xFFE0E0E0)

val TextSecondaryDark = Color(0xFFB0B0B0)

// —— Semantic ——
val Danger = Color(0xFFDC2626)

val Warning = Color(0xFFD97706)

val Success = Color(0xFF059669)

/** Accento preferiti — ambra Material, visibile in entrambi i temi. */
val FavoriteAccent = Color(0xFFF59E0B)

val FocusRing = CyanAccent

/** Gradiente header light: ciano soft → lavanda. */
val HeaderGradientLight = Brush.horizontalGradient(
    colors = listOf(Color(0xFF67E8F9), Color(0xFF7DD3FC), Color(0xFFC4B5FD)),
)

/** Gradiente header brand Navy→Teal (equivalente Compose di @drawable/bg_header_gradient). */
val HeaderBrandGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF0D2C54), Color(0xFF00A3A6)),
)

/** Gradiente header dark: ciano → viola profondo. */
val HeaderGradientDark = Brush.horizontalGradient(
    colors = listOf(Color(0xFF0891B2), Color(0xFF06B6D4), Color(0xFF6D28D9)),
)

@Composable
fun headerGradient(): Brush = HeaderBrandGradient

@Composable
fun brandHighlightContainer(): Color = MaterialTheme.colorScheme.primaryContainer

@Composable
fun contentSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Sfondo container per metriche di successo — adattivo light/dark. */
@Composable
fun successContainer(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Success.copy(alpha = if (isDark) 0.16f else 0.10f)
}

/** Testo su container di successo. */
@Composable
fun successOnContainer(): Color = Success

fun severityColor(severity: dev.accessscope.scanner.data.ViolationSeverity): Color =
    when (severity) {
        dev.accessscope.scanner.data.ViolationSeverity.CRITICAL -> Danger
        dev.accessscope.scanner.data.ViolationSeverity.SERIOUS -> Warning
        dev.accessscope.scanner.data.ViolationSeverity.MODERATE -> Color(0xFFF59E0B)
        dev.accessscope.scanner.data.ViolationSeverity.MINOR -> TextSecondary
    }
