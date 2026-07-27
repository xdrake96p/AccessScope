/**
 * Tipografia AccessScope — strategia tri-font del design system "Scanner & HUD".
 *
 * - **Hanken Grotesk**: display e titoli (identità tech-forward);
 * - **Inter**: corpo e titoli minori (leggibilità);
 * - **JetBrains Mono**: label, chip, badge e dati tecnici (estetica scanner).
 *
 * I font sono scaricati da Google Fonts (`ui-text-google-fonts`); in caso di
 * fallimento della risoluzione si ricade sui font di sistema.
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import dev.accessscope.scanner.R

/** Provider Google Play services per i downloadable fonts. */
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/** Hanken Grotesk — display, headline e numeri di punteggio. */
val HankenGroteskFamily = FontFamily(
    Font(GoogleFont("Hanken Grotesk"), fontProvider, FontWeight.SemiBold),
    Font(GoogleFont("Hanken Grotesk"), fontProvider, FontWeight.Bold),
    Font(GoogleFont("Hanken Grotesk"), fontProvider, FontWeight.ExtraBold),
)

/** Inter — corpo testo e titoli di supporto. */
val InterFamily = FontFamily(
    Font(GoogleFont("Inter"), fontProvider, FontWeight.Normal),
    Font(GoogleFont("Inter"), fontProvider, FontWeight.Medium),
    Font(GoogleFont("Inter"), fontProvider, FontWeight.SemiBold),
)

/** JetBrains Mono — label tecniche, chip, badge e dati monospaziati. */
val JetBrainsMonoFamily = FontFamily(
    Font(GoogleFont("JetBrains Mono"), fontProvider, FontWeight.Normal),
    Font(GoogleFont("JetBrains Mono"), fontProvider, FontWeight.Medium),
    Font(GoogleFont("JetBrains Mono"), fontProvider, FontWeight.SemiBold),
)

/** Compatibilità: famiglia monospazio per dati tecnici (ora JetBrains Mono). */
val MonospaceFamily = JetBrainsMonoFamily

/**
 * Tipografia globale AccessScope (base 16sp, interlinea ≥ 1.5 sul corpo).
 * Le label mono vanno rese in maiuscolo nei componenti (chip, tab, meta-dati).
 */
val Typography = Typography(
    displayMedium = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = HankenGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
    ),
)

/**
 * Stile monospazio per stringhe tecniche (package, path, ID view).
 */
val CodeTextStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)
