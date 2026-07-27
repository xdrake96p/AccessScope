/**
 * Test riepilogo one-line overlay REC.
 */
package dev.accessscope.scanner.recorder

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica che l’anteprima mostri id/text/point leggibili.
 */
class RecordingLivePreviewTest {

    @Test
    fun summarize_tapIncludesId() {
        val s = RecordingLivePreview.summarize(
            RecordedAction.Tap("com.app", viewId = "com.app:id/signInButton"),
        )
        assertTrue(s.contains("tapOn"))
        assertTrue(s.contains("id=signInButton"))
    }

    @Test
    fun summarize_tapPrefersTextOverStructuralId() {
        val s = RecordingLivePreview.summarize(
            RecordedAction.Tap(
                "com.app",
                viewId = "com.app:id/drawer_layout",
                text = "CONTINUA",
            ),
        )
        assertTrue(s.contains("CONTINUA"))
    }

    @Test
    fun summarize_inputPinShowsIdAndText() {
        val s = RecordingLivePreview.summarize(
            RecordedAction.InputText(
                "com.app",
                "121212",
                viewId = "com.app:id/pincode",
            ),
        )
        assertTrue(s.contains("inputText"))
        assertTrue(s.contains("id=pincode"))
        assertTrue(s.contains("121212"))
    }

    @Test
    fun summarize_passwordMasked() {
        val s = RecordingLivePreview.summarize(
            RecordedAction.InputText(
                "com.app",
                "****",
                viewId = "com.app:id/password",
                isPassword = true,
            ),
        )
        assertTrue(s.contains("****"))
    }
}
