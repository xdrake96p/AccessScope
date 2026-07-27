/**
 * Splash screen animata "Scanner & HUD": gradiente teal, scanline e logo con entrance.
 */
package dev.accessscope.scanner.ui.screen.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.BuildConfig
import dev.accessscope.scanner.R
import dev.accessscope.scanner.ui.theme.HankenGroteskFamily
import dev.accessscope.scanner.ui.theme.JetBrainsMonoFamily
import dev.accessscope.scanner.ui.theme.OnPrimaryFixedVariant
import dev.accessscope.scanner.ui.theme.PrimaryDark
import dev.accessscope.scanner.ui.theme.PrimaryFixedDim
import dev.accessscope.scanner.ui.theme.PrimaryLight
import kotlinx.coroutines.delay

/** Durata totale della splash prima della navigazione automatica. */
private const val SPLASH_DURATION_MS = 2600L

/**
 * Schermata di avvio con entrance del logo, scanline HUD e indicatore di inizializzazione.
 *
 * @param onFinished Callback invocato al termine per proseguire verso onboarding o home.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.8f) }
    val logoAlpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "splash_fx")
    val scanlineProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "scanline",
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val loadingOffset by infinite.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "loading",
    )
    val trackWidthPx = with(LocalDensity.current) { 160.dp.toPx() }

    LaunchedEffect(Unit) { logoScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f)) }
    LaunchedEffect(Unit) { logoAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) {
        delay(800)
        taglineAlpha.animateTo(0.85f, tween(700))
    }
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(OnPrimaryFixedVariant, PrimaryLight.copy(alpha = 0.92f), OnPrimaryFixedVariant),
                ),
            ),
    ) {
        // Scanline HUD che attraversa lo schermo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val y = size.height * scanlineProgress
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, PrimaryFixedDim.copy(alpha = 0.22f), Color.Transparent),
                            startY = y - 140f,
                            endY = y + 140f,
                        ),
                    )
                },
        )

        HudCornerAccents()

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(PrimaryFixedDim.copy(alpha = glowAlpha), Color.Transparent),
                                ),
                            )
                        },
                )
                Image(
                    painter = painterResource(R.drawable.ic_access_scope_logo),
                    contentDescription = "Logo AccessScope",
                    modifier = Modifier
                        .size(132.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "AccessScope",
                fontFamily = HankenGroteskFamily,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.alpha(logoAlpha.value),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ACCESSIBILITY SCANNING IN REAL-TIME",
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryDark,
                modifier = Modifier.alpha(taglineAlpha.value),
            )
        }

        // Indicatore di inizializzazione in basso
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(2.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(2.dp)
                        .graphicsLayer { translationX = loadingOffset * trackWidthPx }
                        .background(PrimaryFixedDim),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "INITIALIZING HUD INTERFACE",
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Text(
            "v${BuildConfig.VERSION_NAME}",
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )
    }
}

/** Accent angolari stile HUD ai quattro angoli dello schermo. */
@Composable
private fun HudCornerAccents() {
    val accent = PrimaryFixedDim.copy(alpha = 0.5f)
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .drawBehind {
                val arm = 26.dp.toPx()
                val stroke = 1.5.dp.toPx()
                val w = size.width
                val h = size.height
                // alto-sinistra
                drawLine(accent, Offset(0f, 0f), Offset(arm, 0f), stroke)
                drawLine(accent, Offset(0f, 0f), Offset(0f, arm), stroke)
                // alto-destra
                drawLine(accent, Offset(w - arm, 0f), Offset(w, 0f), stroke)
                drawLine(accent, Offset(w, 0f), Offset(w, arm), stroke)
                // basso-sinistra
                drawLine(accent, Offset(0f, h - arm), Offset(0f, h), stroke)
                drawLine(accent, Offset(0f, h), Offset(arm, h), stroke)
                // basso-destra
                drawLine(accent, Offset(w - arm, h), Offset(w, h), stroke)
                drawLine(accent, Offset(w, h - arm), Offset(w, h), stroke)
            },
    )
}
