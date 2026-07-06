/**
 * Token di animazione Material Motion per AccessScope.
 *
 * Spring per micro-interazioni e navigazione; tween leggeri per fade.
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/** Specifiche di animazione condivise tra schermate e componenti. */
object AccessScopeMotion {
    /** Spring morbido per transizioni di navigazione (Material emphasized). */
    val navSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Spring per float (scale, alpha, chip). */
    val gentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Spring rapido per pulsanti e toggle. */
    val snappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val screenEnterTween = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    val screenExitTween = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    val fadeInTween = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val expandTween = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

    @Deprecated("Usare navSpring", ReplaceWith("navSpring"))
    val navSlideTween = tween<IntOffset>(durationMillis = 280)

    @Deprecated("Usare navSpring", ReplaceWith("navSpring"))
    val navSlideExitTween = tween<IntOffset>(durationMillis = 220)
}
