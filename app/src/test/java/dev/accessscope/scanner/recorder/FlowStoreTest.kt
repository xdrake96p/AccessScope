package dev.accessscope.scanner.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regressione: il YAML esportato deve riflettere [FlowOptimizer.sanitizeForPlay], la stessa
 * trasformazione applicata al Play in-app — altrimenti un flusso verde in-app può esportare
 * uno YAML privo degli stessi fix e fallire nel `maestro` CLI reale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class FlowStoreTest {

    @Test
    fun saveFlow_exportedYaml_reflectsSanitizeForPlayNotJustRawActions() {
        // Un tap "point-only" (nessun id/testo/cd, solo coordinate) sopravvive a optimize() —
        // isNoiseTap lo esenta perché ha pointPercentX valorizzato — ma sanitizeForPlay lo
        // scarta comunque (dropPlaybackNoiseTaps: coordinate obsolete = tap spurio a replay).
        // optimize = false qui isola esattamente il comportamento nuovo di writeArtifacts,
        // senza dover ragionare sull'intera pipeline di optimize().
        val store = FlowStore(RuntimeEnvironment.getApplication())
        val actions = listOf(
            RecordedAction.LaunchApp("com.example.app"),
            RecordedAction.Tap("com.example.app", pointPercentX = 0.5f, pointPercentY = 0.5f),
        )

        val flow = store.saveFlow(
            name = "Test",
            appId = "com.example.app",
            appLabel = "Test App",
            actions = actions,
            optimize = false,
            enforceZeroEdit = false,
        )

        val yaml = store.readYaml(flow)
        assertNotNull(yaml)
        assertFalse("point-only tap should be dropped by sanitizeForPlay before export", yaml!!.contains("point:"))

        // actions.json resta la versione pre-sanitize: editor e ri-ottimizzazione al prossimo
        // salvataggio si aspettano le azioni originali, non quelle già sanificate per Play.
        val storedActions = store.readActions(flow.id)
        assertEquals(2, storedActions?.size)
    }

    @Test
    fun savePlayReport_updatesIndexAndPersists() {
        val store = FlowStore(RuntimeEnvironment.getApplication())
        val flow = store.saveFlow(
            name = "Report test",
            appId = "com.example.app",
            appLabel = "Example",
            actions = listOf(
                RecordedAction.LaunchApp("com.example.app"),
                RecordedAction.Tap("com.example.app", text = "OK"),
            ),
            optimize = false,
            enforceZeroEdit = false,
        )
        val report = dev.accessscope.scanner.recorder.model.PlayExecutionReport(
            runId = "run01",
            flowId = flow.id,
            flowName = flow.name,
            appId = flow.appId,
            appLabel = flow.appLabel,
            kind = dev.accessscope.scanner.recorder.model.PlayRunKind.PLAY,
            startedAtMs = 1_000L,
            finishedAtMs = 2_000L,
            totalSteps = 2,
            passedSteps = 2,
            failedSteps = 0,
            skippedOptionalSteps = 0,
            success = true,
            steps = listOf(
                dev.accessscope.scanner.recorder.model.PlayStepResult(
                    0, "launchApp", "LaunchApp",
                    dev.accessscope.scanner.recorder.model.PlayStepStatus.PASSED,
                ),
            ),
        )
        store.savePlayReport(report)
        val updated = store.getFlow(flow.id)
        assertEquals(true, updated?.lastPlaySuccess)
        assertEquals(2, updated?.lastPlayPassedSteps)
        assertEquals(1, store.listPlayReports(flow.id).size)
    }

    @Test
    fun renameFlow_updatesNameAndYamlHeader() {
        val store = FlowStore(RuntimeEnvironment.getApplication())
        val flow = store.saveFlow(
            name = "Vecchio nome",
            appId = "com.example.app",
            appLabel = "Example",
            actions = listOf(RecordedAction.LaunchApp("com.example.app")),
            optimize = false,
            enforceZeroEdit = false,
        )
        val renamed = store.renameFlow(flow.id, "Nuovo nome cliente")
        assertEquals("Nuovo nome cliente", renamed?.name)
        val yaml = store.readYaml(renamed!!)
        assertTrue(yaml!!.contains("Nuovo nome cliente"))
    }
}
