package dev.accessscope.scanner.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressione [FlowOptimizer] (Maestro Beta).
 */
class FlowOptimizerTest {

    @Test
    fun coalesceInputText_keepsLastOnSameField() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "te", viewId = "com.app:id/username"),
            RecordedAction.InputText("com.app", "tes", viewId = "com.app:id/username"),
            RecordedAction.InputText("com.app", "test", viewId = "com.app:id/username"),
            RecordedAction.Tap("com.app", text = "OK"),
        )
        val result = FlowOptimizer.coalesceInputText(actions)
        assertEquals(2, result.size)
        assertEquals("test", (result[0] as RecordedAction.InputText).text)
    }

    @Test
    fun coalesceInputText_keepsTwoIdenticalPinsWithGap() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode", timestampMs = 1_000L),
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode", timestampMs = 3_000L),
        )
        val result = FlowOptimizer.coalesceInputText(actions)
        assertEquals(2, result.size)
    }

    @Test
    fun coalesceInputText_neverMergesPinLikeEvenShortGap() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode", timestampMs = 1_000L),
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode", timestampMs = 1_200L),
        )
        assertEquals(2, FlowOptimizer.coalesceInputText(actions).size)
    }

    @Test
    fun coalesceInputText_mergesPasswordMaskedDuplicates() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "****", viewId = "com.app:id/password", isPassword = true, timestampMs = 1_000L),
            RecordedAction.InputText("com.app", "****", viewId = "com.app:id/password", isPassword = true, timestampMs = 1_100L),
        )
        assertEquals(1, FlowOptimizer.coalesceInputText(actions).size)
    }

    @Test
    fun coalesceInputText_mergesIncrementalTyping() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "12", viewId = "com.app:id/pincode", timestampMs = 1_000L),
            RecordedAction.InputText("com.app", "121", viewId = "com.app:id/pincode", timestampMs = 1_100L),
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode", timestampMs = 1_200L),
        )
        val result = FlowOptimizer.coalesceInputText(actions)
        assertEquals(1, result.size)
        assertEquals("121212", (result[0] as RecordedAction.InputText).text)
    }

    @Test
    fun dedupeTaps_collapsesIdenticalWithinWindow() {
        val t0 = 1_000L
        val actions = listOf(
            RecordedAction.Tap("com.app", text = "CONTINUA", timestampMs = t0),
            RecordedAction.Tap("com.app", text = "CONTINUA", timestampMs = t0 + 200),
        )
        val result = FlowOptimizer.dedupeTaps(actions)
        assertEquals(1, result.size)
    }

    @Test
    fun dropNoiseScrolls_betweenInputs() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "a"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.InputText("com.app", "b"),
        )
        val result = FlowOptimizer.dropNoiseScrolls(actions)
        assertEquals(2, result.size)
        assertTrue(result.none { it is RecordedAction.Scroll })
    }

    @Test
    fun enrich_addsWaitAfterLaunch_notHideKeyboardAfterInput() {
        val actions = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.InputText("com.app", "user"),
            RecordedAction.Tap("com.app", text = "Next"),
        )
        val result = FlowOptimizer.enrich(actions)
        assertTrue(result.any { it is RecordedAction.WaitForAnimation })
        assertTrue(result.none { it is RecordedAction.HideKeyboard })
        val yaml = MaestroYamlExporter.export("com.app", "T", FlowOptimizer.optimize(actions))
        assertTrue(yaml.contains("waitForAnimationToEnd"))
        assertFalse(yaml.contains("hideKeyboard"))
        assertFalse(yaml.contains("progressBar"))
    }

    @Test
    fun dropNoiseTaps_removesProgressBar() {
        val actions = listOf(
            RecordedAction.Tap("com.app", viewId = "com.app:id/signInButton"),
            RecordedAction.Tap("com.app", viewId = "com.app:id/progressBarContent"),
            RecordedAction.InputText("com.app", "user", viewId = "com.app:id/username"),
        )
        val result = FlowOptimizer.dropNoiseTaps(actions)
        assertEquals(2, result.size)
        assertTrue(result.none {
            it is RecordedAction.Tap &&
                MaestroSelectorHeuristics.isNoiseViewId(it.viewId)
        })
    }

    @Test
    fun dropFocusTapsBeforeInput_removesPasswordFocusTap() {
        val actions = listOf(
            RecordedAction.Tap("com.app", viewId = "com.app:id/password"),
            RecordedAction.InputText("com.app", "****", viewId = "com.app:id/password", isPassword = true),
        )
        val result = FlowOptimizer.dropFocusTapsBeforeInput(actions)
        assertEquals(1, result.size)
        assertTrue(result[0] is RecordedAction.InputText)
    }

    @Test
    fun sanitizeForPlay_stripsLegacyNoise() {
        val raw = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", viewId = "progressBarContent"),
            RecordedAction.Tap("com.app", viewId = "password"),
            RecordedAction.InputText("com.app", "user", viewId = "username"),
            RecordedAction.HideKeyboard("com.app"),
            RecordedAction.Wait("com.app", timeoutMs = 5_000L),
        )
        val sanitized = FlowOptimizer.sanitizeForPlay(raw)
        // Progress droppato; tap password / hideKeyboard / wait editor conservati (+ eventuali waitForAnimation).
        assertTrue(sanitized.none { it is RecordedAction.Tap && it.viewId?.contains("progress") == true })
        assertTrue(sanitized[0] is RecordedAction.LaunchApp)
        assertTrue(sanitized.any { it is RecordedAction.Tap && it.viewId!!.contains("password") })
        assertTrue(sanitized.any { it is RecordedAction.HideKeyboard })
        assertTrue(sanitized.any { it is RecordedAction.Wait && it.timeoutMs == 5_000L })
    }

    @Test
    fun sanitizeForPlay_keepsEditorAddedFieldTapAndWait() {
        val raw = listOf(
            RecordedAction.LaunchApp("com.app"),
            RecordedAction.Tap("com.app", text = "CONTINUA"),
            RecordedAction.Wait("com.app", timeoutMs = 3_000L),
            RecordedAction.Tap("com.app", viewId = "com.app:id/pincode"),
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode"),
        )
        val sanitized = FlowOptimizer.sanitizeForPlay(raw)
        assertTrue(sanitized.any { it is RecordedAction.LaunchApp })
        assertTrue(sanitized.any { it is RecordedAction.Tap && it.text == "CONTINUA" })
        val wait = sanitized.filterIsInstance<RecordedAction.Wait>().first { it.timeoutMs == 3_000L || it.visibleId != null }
        assertTrue(wait.visibleId == null || wait.visibleId!!.contains("pincode") || wait.timeoutMs == 3_000L)
        assertTrue(sanitized.any { it is RecordedAction.InputText })
    }

    @Test
    fun dropNoiseScrolls_afterPinInput() {
        val actions = listOf(
            RecordedAction.InputText("com.app", "121212", viewId = "com.app:id/pincode"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Tap("com.app", text = "POLIZZA"),
        )
        val result = FlowOptimizer.dropNoiseScrolls(actions)
        assertEquals(2, result.size)
        assertTrue(result.none { it is RecordedAction.Scroll })
    }

    @Test
    fun dropNoiseScrolls_multipleScrollsWithAnimationWaitsBetween_allKept() {
        // Bug reale (flusso AXA registrato): dropNoiseScrolls gira sia in optimize() sia di
        // nuovo in sanitizeForPlay(), ma tra le due passate WaitPlanner inserisce un
        // WaitForAnimation dopo ogni scroll. Alla seconda passata, un cammino indietro che
        // tollera solo Scroll consecutivi si fermava sul wait appena inserito e trattava come
        // "rumore post-PIN" ogni scroll tranne il primo — 4 scroll reali diventavano 1 solo
        // scroll + 4 waitForAnimationToEnd orfani nello YAML, perdendo la distanza di scroll
        // necessaria a raggiungere il target ("POLIZZA N. 404347818", ben oltre la prima
        // schermata di una lista lunga).
        val actions = listOf(
            RecordedAction.Tap("com.app", text = "NON ORA"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.WaitForAnimation("com.app", timeoutMs = 800L),
            RecordedAction.Scroll("com.app"),
            RecordedAction.WaitForAnimation("com.app", timeoutMs = 800L),
            RecordedAction.Scroll("com.app"),
            RecordedAction.WaitForAnimation("com.app", timeoutMs = 800L),
            RecordedAction.Scroll("com.app"),
            RecordedAction.WaitForAnimation("com.app", timeoutMs = 800L),
            RecordedAction.Tap("com.app", text = "POLIZZA N.  404347818"),
        )
        val result = FlowOptimizer.dropNoiseScrolls(actions)
        assertEquals(4, result.count { it is RecordedAction.Scroll })
    }

    @Test
    fun auditScrollCardinality_noLossWhenScrollsPromotedToScrollUntilVisible() {
        // Il caso corretto: ScrollCoalescer collassa un run di scroll in UNA sola
        // scrollUntilVisible — è esattamente il suo scopo, non una perdita.
        val raw = listOf(
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Tap("com.app", text = "POLIZZA N.  404347818"),
        )
        val optimized = listOf(
            RecordedAction.ScrollUntilVisible("com.app", visibleText = "POLIZZA N.  404347818"),
            RecordedAction.Tap("com.app", text = "POLIZZA N.  404347818"),
        )
        assertTrue(FlowOptimizer.auditScrollCardinality(raw, optimized).isEmpty())
    }

    @Test
    fun auditScrollCardinality_warnsWhenScrollsDisappearWithoutPromotion() {
        // Il bug reale (Fase 3 della riscrittura pipeline): 4 scroll grezzi, ma il flusso
        // ottimizzato ne ha solo 1 e nessuna scrollUntilVisible a spiegare la riduzione — la
        // distanza di scroll necessaria a raggiungere il target è andata persa in silenzio.
        val raw = listOf(
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Scroll("com.app"),
            RecordedAction.Tap("com.app", text = "POLIZZA N.  404347818"),
        )
        val optimizedWithLoss = listOf(
            RecordedAction.Scroll("com.app"),
            RecordedAction.WaitForAnimation("com.app"),
            RecordedAction.WaitForAnimation("com.app"),
            RecordedAction.WaitForAnimation("com.app"),
            RecordedAction.Tap("com.app", text = "POLIZZA N.  404347818"),
        )
        val warnings = FlowOptimizer.auditScrollCardinality(raw, optimizedWithLoss)
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("scroll"))
    }

    @Test
    fun auditScrollCardinality_noRawScrolls_neverWarns() {
        val raw = listOf(RecordedAction.Tap("com.app", text = "OK"))
        assertTrue(FlowOptimizer.auditScrollCardinality(raw, raw).isEmpty())
    }
}
