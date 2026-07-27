package dev.accessscope.scanner.util

import dev.accessscope.scanner.data.ArchivedScanSession
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Contract test anti-regressione (piano M0-R3): lo schema JSON di
 * [ArchivedScanSession] serializzato dallo store deve contenere sempre
 * tutti i campi presenti dalla v1.3.0 (retrocompatibilità bridge/cronologia).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SessionJsonSchemaContractTest {

    private val store = ScanHistoryStore(RuntimeEnvironment.getApplication())

    @Test
    fun sessionJson_containsAllV130Fields() {
        val session = ArchivedScanSession(
            id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            completedAtMs = 1234567890L,
            targetPackages = setOf("com.example.app"),
            violations = emptyList(),
            screenReaderFindings = emptyList(),
            uniqueScreens = 3,
            scanAnalyses = 42,
            scanScopeLabel = "Full",
            score = 92,
            pdfPath = "Download/AccessScope_test.pdf",
            violationKeys = setOf("k1", "k2"),
        )

        val json = store.sessionToJson(session)

        // Campi presenti dalla v1.3.0: non rimuovere né rinominare.
        val v130Fields = listOf(
            "id", "completedAtMs", "targetPackages", "violations",
            "screenReaderFindings", "uniqueScreens", "scanAnalyses",
            "scanScopeLabel", "score", "pdfPath", "violationKeys", "visitedScreens",
        )
        v130Fields.forEach { fieldName ->
            assertTrue("Campo v1.3.0 mancante nello schema sessione: $fieldName", json.has(fieldName))
        }
    }
}
