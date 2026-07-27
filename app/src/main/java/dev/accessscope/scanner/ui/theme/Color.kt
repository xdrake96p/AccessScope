/**
 * Palette AccessScope — design system "Scanner & HUD" (Electric Teal).
 *
 * Token chiaro/scuro dai DESIGN.md Stitch (`docs/restyle/stitch_parte1`).
 * Le costanti storiche del tema precedente sono mantenute come getter
 * composable di compatibilità che delegano a [MaterialTheme.colorScheme],
 * così i componenti esistenti non richiedono modifiche.
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.accessscope.scanner.data.ViolationSeverity

// —— Light scheme (DESIGN.md light) ——
val PrimaryLight = Color(0xFF006875)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFF00E5FF)
val OnPrimaryContainerLight = Color(0xFF00626E)
val SecondaryLight = Color(0xFF5B00DF)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFF7531FF)
val OnSecondaryContainerLight = Color(0xFFEADFFF)
val TertiaryLight = Color(0xFF765A00)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFEC931)
val OnTertiaryContainerLight = Color(0xFF6F5500)
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF93000A)
val BackgroundLight = Color(0xFFFCF9F8)
val OnBackgroundLight = Color(0xFF1C1B1B)
val SurfaceLight = Color(0xFFFCF9F8)
val OnSurfaceLight = Color(0xFF1C1B1B)
val SurfaceVariantLight = Color(0xFFE5E2E1)
val OnSurfaceVariantLight = Color(0xFF3B494C)
val OutlineLight = Color(0xFF6B7A7D)
val OutlineVariantLight = Color(0xFFBAC9CC)
val SurfaceDimLight = Color(0xFFDCD9D9)
val SurfaceBrightLight = Color(0xFFFCF9F8)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF6F3F2)
val SurfaceContainerLight = Color(0xFFF0EDEC)
val SurfaceContainerHighLight = Color(0xFFEBE7E7)
val SurfaceContainerHighestLight = Color(0xFFE5E2E1)
val InverseSurfaceLight = Color(0xFF313030)
val InverseOnSurfaceLight = Color(0xFFF3F0EF)
val InversePrimaryLight = Color(0xFF00DAF3)

// —— Dark scheme (DESIGN.md dark) ——
val PrimaryDark = Color(0xFFC3F5FF)
val OnPrimaryDark = Color(0xFF00363D)
val PrimaryContainerDark = Color(0xFF00E5FF)
val OnPrimaryContainerDark = Color(0xFF00626E)
val SecondaryDark = Color(0xFFD1BEEF)
val OnSecondaryDark = Color(0xFF372950)
val SecondaryContainerDark = Color(0xFF50426B)
val OnSecondaryContainerDark = Color(0xFFC2B0E0)
val TertiaryDark = Color(0xFFDCF0F8)
val OnTertiaryDark = Color(0xFF213339)
val TertiaryContainerDark = Color(0xFFC0D4DC)
val OnTertiaryContainerDark = Color(0xFF4A5C63)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
val BackgroundDark = Color(0xFF0D1518)
val OnBackgroundDark = Color(0xFFDBE4E8)
val SurfaceDark = Color(0xFF0D1518)
val OnSurfaceDark = Color(0xFFDBE4E8)
val SurfaceVariantDark = Color(0xFF2E3639)
val OnSurfaceVariantDark = Color(0xFFBAC9CC)
val OutlineDark = Color(0xFF849396)
val OutlineVariantDark = Color(0xFF3B494C)
val SurfaceDimDark = Color(0xFF0D1518)
val SurfaceBrightDark = Color(0xFF323A3E)
val SurfaceContainerLowestDark = Color(0xFF070F12)
val SurfaceContainerLowDark = Color(0xFF151D20)
val SurfaceContainerDark = Color(0xFF192124)
val SurfaceContainerHighDark = Color(0xFF232B2E)
val SurfaceContainerHighestDark = Color(0xFF2E3639)
val InverseSurfaceDark = Color(0xFFDBE4E8)
val InverseOnSurfaceDark = Color(0xFF2A3235)
val InversePrimaryDark = Color(0xFF006875)

// —— Colori "fixed" (identici nei due schemi, per DESIGN.md) ——
val PrimaryFixed = Color(0xFF9CF0FF)
val PrimaryFixedDim = Color(0xFF00DAF3)
val OnPrimaryFixed = Color(0xFF001F24)
val OnPrimaryFixedVariant = Color(0xFF004F58)
val SecondaryFixed = Color(0xFFE8DDFF)
val SecondaryFixedDim = Color(0xFFCFBDFF)
val OnSecondaryFixed = Color(0xFF22005D)
val OnSecondaryFixedVariant = Color(0xFF5300CD)
val TertiaryFixed = Color(0xFFFFDF96)
val TertiaryFixedDim = Color(0xFFF3BF26)
val OnTertiaryFixed = Color(0xFF251A00)
val OnTertiaryFixedVariant = Color(0xFF594400)

// —— Semantici custom ——
/**
 * Ambra preferiti (stella), adattiva light/dark.
 *
 * Light `#8B6914` ≈ 5.1:1 su bianco (WCAG 1.4.11 UI ≥ 3:1);
 * dark `#FEC931` ≈ 12.5:1 su surface scura. Il giallo Stitch puro
 * (`#FEC931`) fallisce sul tema chiaro (~1.5:1).
 */
private val FavoriteAccentLight = Color(0xFF8B6914)
private val FavoriteAccentDark = Color(0xFFFEC931)

/** Accento preferiti (stella): leggibile su entrambi i temi. */
val FavoriteAccent: Color
    @Composable get() = if (isDarkScheme) FavoriteAccentDark else FavoriteAccentLight

/** `true` se lo schema corrente è scuro (stima da luminanza del background). */
private val isDarkScheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

// —— Compatibilità col tema precedente (getter delegati allo scheme) ——
/** @deprecated Usare `MaterialTheme.colorScheme.primary`. */
val BrandPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** @deprecated Usare `MaterialTheme.colorScheme.secondary`. */
val BrandSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

/** @deprecated Usare `MaterialTheme.colorScheme.inversePrimary`. */
val BrandDark: Color
    @Composable get() = MaterialTheme.colorScheme.inversePrimary

/** @deprecated Usare `MaterialTheme.colorScheme.error`. */
val Danger: Color
    @Composable get() = MaterialTheme.colorScheme.error

/** @deprecated Usare `MaterialTheme.colorScheme.primary` (successo = teal nel nuovo design). */
val Success: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/**
 * Accento di avviso (gravità MODERATE): ambra `tertiary` nel chiaro,
 * `tertiaryFixedDim` nello scuro per mantenere la leggibilità.
 */
val Warning: Color
    @Composable get() = if (isDarkScheme) TertiaryFixedDim else MaterialTheme.colorScheme.tertiary

/** Gradiente hero del nuovo brand: primary → inversePrimary (teal). */
@Composable
fun headerGradient(): Brush = Brush.horizontalGradient(
    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
)

@Composable
fun brandHighlightContainer(): Color = MaterialTheme.colorScheme.primaryContainer

@Composable
fun contentSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Sfondo container per metriche di successo — teal soft, adattivo light/dark. */
@Composable
fun successContainer(): Color =
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkScheme) 0.25f else 0.40f)

/** Testo su container di successo. */
@Composable
fun successOnContainer(): Color = MaterialTheme.colorScheme.onPrimaryContainer

/**
 * Colore accento per gravità di violazione, coerente coi mockup Stitch:
 * CRITICAL/SERIOUS → error, MODERATE → ambra [Warning], MINOR → onSurfaceVariant.
 *
 * Nota (dogfooding WCAG): MINOR usa `onSurfaceVariant` e non `outline` perché
 * `outline` (#6B7A7D su #FCF9F8 = 4.26:1) non raggiunge AA 4.5:1 sul tema chiaro.
 */
@Composable
fun severityColor(severity: ViolationSeverity): Color = when (severity) {
    ViolationSeverity.CRITICAL -> MaterialTheme.colorScheme.error
    ViolationSeverity.SERIOUS -> MaterialTheme.colorScheme.error
    ViolationSeverity.MODERATE -> Warning
    ViolationSeverity.MINOR -> MaterialTheme.colorScheme.onSurfaceVariant
}
