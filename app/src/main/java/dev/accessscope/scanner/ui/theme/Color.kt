package dev.accessscope.scanner.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BrandPrimary = Color(0xFF0D7377)
val BrandSecondary = Color(0xFF14A085)
val BrandDark = Color(0xFF0A4F52)
val BrandLight = Color(0xFFE8F5F4)
val SurfaceLight = Color(0xFFF4F7F7)
val CardSurface = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1A2B2C)
val TextSecondary = Color(0xFF5C6B6C)
val Danger = Color(0xFFC62828)
val Warning = Color(0xFFE65100)
val Success = Color(0xFF2E7D32)

val HeaderGradient = Brush.horizontalGradient(
    colors = listOf(BrandDark, BrandPrimary, BrandSecondary),
)

fun severityColor(severity: dev.accessscope.scanner.data.ViolationSeverity): Color =
    when (severity) {
        dev.accessscope.scanner.data.ViolationSeverity.CRITICAL -> Danger
        dev.accessscope.scanner.data.ViolationSeverity.SERIOUS -> Warning
        dev.accessscope.scanner.data.ViolationSeverity.MODERATE -> Color(0xFFF9A825)
        dev.accessscope.scanner.data.ViolationSeverity.MINOR -> TextSecondary
    }
