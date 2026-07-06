package dev.accessscope.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = CardSurface,
    secondary = BrandSecondary,
    background = SurfaceLight,
    surface = CardSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val DarkColors = darkColorScheme(
    primary = BrandSecondary,
    secondary = BrandPrimary,
)

@Composable
fun AccessScopeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
