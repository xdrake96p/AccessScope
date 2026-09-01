package dev.accessscope.scanner.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FlowPlayer.oppositeScrollDirection] — la base del fallback bidirezionale di
 * `scrollUntilVisible`. Bug reale osservato su un flusso AXA registrato: la direzione salvata
 * nello YAML può non portare al target da uno stato fresco dell'app (l'utente aveva corretto una
 * sovra-scrollata durante la registrazione); prima il player martellava la stessa direzione
 * fallimentare (`scroll_noop` ogni giro) per l'intero timeout, interrompendo il resto del flusso.
 */
class FlowPlayerScrollDirectionTest {

    @Test
    fun opposite_flipsVerticalAxis() {
        assertEquals(ScrollDirection.DOWN, FlowPlayer.oppositeScrollDirection(ScrollDirection.UP))
        assertEquals(ScrollDirection.UP, FlowPlayer.oppositeScrollDirection(ScrollDirection.DOWN))
    }

    @Test
    fun opposite_flipsHorizontalAxis() {
        assertEquals(ScrollDirection.RIGHT, FlowPlayer.oppositeScrollDirection(ScrollDirection.LEFT))
        assertEquals(ScrollDirection.LEFT, FlowPlayer.oppositeScrollDirection(ScrollDirection.RIGHT))
    }

    @Test
    fun opposite_isSelfInverse() {
        for (direction in ScrollDirection.entries) {
            val back = FlowPlayer.oppositeScrollDirection(FlowPlayer.oppositeScrollDirection(direction))
            assertEquals(direction, back)
        }
    }
}
