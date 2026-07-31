package dev.accessscope.scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ScanEvidenceStoreTest {

    @Test
    fun saveScreenScreenshot_secondVisit_doesNotOverwriteFirst() {
        // Bug: sovrascrivere lo screenshot ad ogni rivisita dello stesso fingerprint fa sì che i
        // marker delle violazioni rilevate alla prima visita finiscano disegnati sul contenuto
        // sbagliato quando il report mostra lo sfondo più recente al posto di quello originale.
        val store = ScanEvidenceStore(RuntimeEnvironment.getApplication())
        val sessionId = "session-1"
        val fingerprint = "com.example.app::Home"

        val first = bitmap(Color.RED)
        val pathA = store.saveScreenScreenshot(sessionId, fingerprint, first)
        first.recycle()
        assertNotNull(pathA)
        val sizeAfterFirst = File(pathA!!).length()

        val second = bitmap(Color.BLUE)
        val pathB = store.saveScreenScreenshot(sessionId, fingerprint, second)
        second.recycle()

        assertEquals(pathA, pathB)
        assertEquals(sizeAfterFirst, File(pathB!!).length())
    }

    @Test
    fun saveScreenScreenshot_differentFingerprints_eachGetsOwnFile() {
        val store = ScanEvidenceStore(RuntimeEnvironment.getApplication())
        val sessionId = "session-1"

        val home = bitmap(Color.RED)
        val pathHome = store.saveScreenScreenshot(sessionId, "com.example.app::Home", home)
        home.recycle()

        val settings = bitmap(Color.BLUE)
        val pathSettings = store.saveScreenScreenshot(sessionId, "com.example.app::Settings", settings)
        settings.recycle()

        assertNotNull(pathHome)
        assertNotNull(pathSettings)
        assert(pathHome != pathSettings)
    }

    private fun bitmap(color: Int): Bitmap =
        Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
