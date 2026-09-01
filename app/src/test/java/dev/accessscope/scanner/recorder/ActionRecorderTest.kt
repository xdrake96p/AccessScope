package dev.accessscope.scanner.recorder

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cattura eventi → azioni Maestro (testo / scroll / discard).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActionRecorderTest {

    private val recorder = ActionRecorder()

    @Test
    fun click_withText_producesTap() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.example"
            text.add("Accedi")
        }
        val actions = recorder.onEvent(event, 1080, 1920)
        event.recycle()
        assertEquals(1, actions.size)
        val tap = actions.first() as RecordedAction.Tap
        assertEquals("Accedi", tap.text)
        assertEquals("com.example", tap.packageName)
    }

    @Test
    fun textChanged_thenFlush_producesInputText() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED).apply {
            packageName = "com.example"
            text.add("user@test.com")
        }
        assertTrue(recorder.onEvent(event, 1080, 1920).isEmpty())
        event.recycle()
        val flushed = recorder.flush()
        assertEquals(1, flushed.size)
        val input = flushed.first() as RecordedAction.InputText
        assertEquals("user@test.com", input.text)
    }

    // resolveTypedText è la funzione pura estratta dal fix hint-leak (bug reale su
    // it.nexi.bff/MPS: un campo IBAN mai toccato produceva "- inputText: \"IBAN
    // (obbligatorio)\"" — l'hint, non un testo digitato). Testata qui direttamente, senza
    // passare da AccessibilityNodeInfo/AccessibilityEvent reali: più precisa e non soggetta
    // allo stato statico di ShadowAccessibilityNodeInfo tra i test.

    @Test
    fun resolveTypedText_nodeTextEqualsHint_treatedAsEmpty() {
        // Campo vuoto: AOSP riporta l'hint come testo del nodo (focus su campo mai toccato).
        val result = recorder.resolveTypedText(
            eventText = null,
            nodeText = "IBAN (obbligatorio)",
            hintText = "IBAN (obbligatorio)",
        )
        assertNull(result)
    }

    @Test
    fun resolveTypedText_realTypedText_returnsTypedTextNotHint() {
        val result = recorder.resolveTypedText(
            eventText = null,
            nodeText = "IT60X0542811101000000123456",
            hintText = "IBAN (obbligatorio)",
        )
        assertEquals("IT60X0542811101000000123456", result)
    }

    @Test
    fun resolveTypedText_eventTextEqualsHint_alsoTreatedAsEmpty() {
        // L'evento stesso può portare l'hint (event.text) quando il campo torna vuoto, non
        // solo il nodo — stessa protezione su entrambe le sorgenti.
        val result = recorder.resolveTypedText(
            eventText = "IBAN (obbligatorio)",
            nodeText = null,
            hintText = "IBAN (obbligatorio)",
        )
        assertNull(result)
    }

    @Test
    fun resolveTypedText_typedTextCoincidingWithHint_treatedAsEmpty_acceptedTradeoff() {
        // Limite noto e accettato: non c'è modo affidabile di distinguere "il campo è ancora
        // vuoto e mostra l'hint" da "l'utente ha digitato per davvero lo stesso testo
        // dell'hint" con i soli campi text/hintText — digitare il placeholder alla lettera è
        // comunque praticamente impossibile in pratica. Preferibile non registrare, piuttosto
        // che rischiare di nuovo il leak.
        val result = recorder.resolveTypedText(
            eventText = "IBAN (obbligatorio)",
            nodeText = "IBAN (obbligatorio)",
            hintText = "IBAN (obbligatorio)",
        )
        assertNull(result)
    }

    @Test
    fun resolveTypedText_noHint_returnsTextUnchanged() {
        val result = recorder.resolveTypedText(eventText = null, nodeText = "user@test.com", hintText = null)
        assertEquals("user@test.com", result)
    }

    @Test
    fun scroll_withoutDelta_discarded() {
        recorder.reset()
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = "com.example"
            fromIndex = -1
            toIndex = -1
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                runCatching {
                    AccessibilityEvent::class.java.getMethod("setScrollDeltaY", Int::class.javaPrimitiveType)
                        .invoke(this, 0)
                    AccessibilityEvent::class.java.getMethod("setScrollDeltaX", Int::class.javaPrimitiveType)
                        .invoke(this, 0)
                }
            }
        }
        val actions = recorder.onEvent(event, 1080, 1920)
        event.recycle()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun scroll_withDelta_producesScrollAction() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = "com.example"
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                // Robolectric: set field if available
                runCatching {
                    AccessibilityEvent::class.java.getMethod("setScrollDeltaY", Int::class.javaPrimitiveType)
                        .invoke(this, 40)
                }
            }
            fromIndex = 0
            toIndex = 3
        }
        val actions = recorder.onEvent(event, 1080, 1920)
        event.recycle()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is RecordedAction.Scroll)
    }

    @Test
    fun backPressed_recordsBackAndSuppressesScroll() {
        val back = recorder.onBackPressed("com.example")
        assertEquals(1, back.size)
        assertTrue(back.first() is RecordedAction.Back)

        val scroll = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = "com.example"
            fromIndex = 0
            toIndex = 2
        }
        val after = recorder.onEvent(scroll, 1080, 1920)
        scroll.recycle()
        assertTrue(after.isEmpty())
    }

    @Test
    fun click_nonOra_fromEventText_withoutSource() {
        val click = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.example"
            text.add("Non ora")
        }
        val actions = recorder.onEvent(click, 1080, 1920)
        click.recycle()
        assertEquals(1, actions.size)
        val tap = actions.first() as RecordedAction.Tap
        assertEquals("Non ora", tap.text)
        assertEquals(
            dev.accessscope.scanner.recorder.model.StepExecutionMode.Optional,
            tap.executionMode,
        )
    }

    @Test
    fun click_withoutSelector_discarded() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.example"
        }
        val actions = recorder.onEvent(event, 1080, 1920)
        event.recycle()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun click_thenText_flushesInputBeforeTap() {
        val textEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED).apply {
            packageName = "com.example"
            text.add("hello")
        }
        recorder.onEvent(textEvent, 1080, 1920)
        textEvent.recycle()

        val click = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.example"
            text.add("OK")
        }
        val actions = recorder.onEvent(click, 1080, 1920)
        click.recycle()
        assertEquals(2, actions.size)
        assertTrue(actions[0] is RecordedAction.InputText)
        assertTrue(actions[1] is RecordedAction.Tap)
        assertEquals("hello", (actions[0] as RecordedAction.InputText).text)
    }
}

/**
 * Contatori step reali vs LaunchApp sintetico (Robolectric per Log).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordingSessionControllerTest {

    @Test
    fun realStepCount_excludesLaunchApp() {
        val c = RecordingSessionController()
        c.start("com.example", "Example")
        assertEquals(0, c.realStepCount())
        assertEquals(1, c.state.value.actions.size)
        assertTrue(c.state.value.actions.first() is RecordedAction.LaunchApp)
        c.stop()
        assertEquals(0, c.realStepCount(c.state.value.actions))
    }
}
