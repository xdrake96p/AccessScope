package dev.accessscope.scanner.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object AccessScopeMotion {
    val gentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val screenEnterTween = tween<Float>(durationMillis = 280)
    val screenExitTween = tween<Float>(durationMillis = 220)
    val navSlideTween = tween<IntOffset>(durationMillis = 280)
    val navSlideExitTween = tween<IntOffset>(durationMillis = 220)
    val fadeInTween = tween<Float>(durationMillis = 200)
    val expandTween = tween<Float>(durationMillis = 240)
}
