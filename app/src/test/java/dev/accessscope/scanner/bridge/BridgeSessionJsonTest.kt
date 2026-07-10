package dev.accessscope.scanner.bridge

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.EvidenceKind
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BridgeSessionJsonTest {

    @Test
    fun sessionToJson_omitsSensitiveViolationFields() {
        val session = ArchivedScanSession(
            id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            completedAtMs = 1L,
            targetPackages = setOf("com.example.app"),
            violations = listOf(
                AccessibilityViolation(
                    type = ViolationType.MISSING_LABEL,
                    viewClassName = "TextView",
                    screenTitle = "Home",
                    packageName = "com.example.app",
                    details = "Missing label",
                    elementLabel = "Saldo segreto",
                    evidenceImagePath = "/data/data/dev.accessscope.scanner/cache/secret.jpg",
                    foregroundColorHex = "#FFFFFF",
                    backgroundColorHex = "#000000",
                    evidenceKind = EvidenceKind.SCREENSHOT,
                ),
            ),
            screenReaderFindings = listOf(
                ScreenReaderFinding(
                    packageName = "com.example.app",
                    screenTitle = "Home",
                    nodeClassName = "Button",
                    announcedText = "Trasferisci 500 euro",
                    issue = "Too short",
                ),
            ),
            uniqueScreens = 1,
            scanAnalyses = 1,
            scanScopeLabel = "Full",
            score = 80,
            pdfPath = "Download/report.pdf",
            violationKeys = setOf("k1"),
        )

        val json = BridgeSessionJson.sessionToJson(session)
        val violation = json.getJSONArray("violations").getJSONObject(0)
        val finding = json.getJSONArray("screenReaderFindings").getJSONObject(0)

        assertFalse(violation.has("elementLabel"))
        assertFalse(violation.has("evidenceImagePath"))
        assertFalse(violation.has("foregroundColorHex"))
        assertFalse(violation.has("backgroundColorHex"))
        assertFalse(finding.has("announcedText"))
        assertTrue(violation.has("severity"))
        assertTrue(json.has("pdfPath"))
    }
}
