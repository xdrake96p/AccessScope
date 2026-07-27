package dev.accessscope.scanner.recorder

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
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

    @Test
    fun scroll_producesScrollAction() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = "com.example"
        }
        val actions = recorder.onEvent(event, 1080, 1920)
        event.recycle()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is RecordedAction.Scroll)
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
