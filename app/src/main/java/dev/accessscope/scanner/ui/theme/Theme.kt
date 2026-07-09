/**
 * Tema Material 3 AccessScope — palette premium ciano/lavanda/viola.
 */
package dev.accessscope.scanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = CardSurface,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0E7490),
    secondary = BrandSecondary,
    onSecondary = CardSurface,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = VioletDark,
    tertiary = LavenderAccent,
    onTertiary = CardSurface,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = VioletDark,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = CardSurface,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val DarkColors = darkColorScheme(
    primary = CyanSoft,
    onPrimary = SurfaceDark,
    primaryContainer = Color(0xFF164E63),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = LavenderAccent,
    onSecondary = SurfaceDark,
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = VioletDeep,
    onTertiary = TextPrimaryDark,
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2F3540),
    onSurfaceVariant = TextSecondaryDark,
    error = Danger,
    onError = TextPrimaryDark,
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF374151),
)

@Composable
fun AccessScopeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = if (useDarkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AccessScopeShapes,
        content = content,
    )
}
