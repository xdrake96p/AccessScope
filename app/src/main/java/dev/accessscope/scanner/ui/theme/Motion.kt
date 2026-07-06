/**
 * Token di animazione condivisi nell'interfaccia AccessScope.
 *
 * Centralizza durate e curve per transizioni di schermata, fade e navigazione,
 * garantendo coerenza visiva tra Home, Report e componenti riutilizzabili.
 */
package dev.accessscope.scanner.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Oggetto con specifiche di animazione riutilizzabili in tutta l'app.
 *
 * Le proprietà sono [androidx.compose.animation.core.FiniteAnimationSpec] pronte
 * per [androidx.compose.animation.AnimatedContent], [androidx.compose.animation.AnimatedVisibility]
 * e transizioni di navigazione.
 */
object AccessScopeMotion {
    /** Spring morbido con leggero rimbalzo per micro-interazioni. */
    val gentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Tween di entrata per contenuti di schermata (280 ms). */
    val screenEnterTween = tween<Float>(durationMillis = 280)

    /** Tween di uscita per contenuti di schermata (220 ms). */
    val screenExitTween = tween<Float>(durationMillis = 220)

    /** Tween di entrata per slide di navigazione su offset (280 ms). */
    val navSlideTween = tween<IntOffset>(durationMillis = 280)

    /** Tween di uscita per slide di navigazione su offset (220 ms). */
    val navSlideExitTween = tween<IntOffset>(durationMillis = 220)

    /** Tween rapido per fade-in di elementi (200 ms). */
    val fadeInTween = tween<Float>(durationMillis = 200)

    /** Tween per espansione verticale di sezioni (240 ms). */
    val expandTween = tween<Float>(durationMillis = 240)
}
