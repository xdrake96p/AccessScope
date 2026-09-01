package dev.accessscope.scanner.recorder.optimization

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.MaestroYamlExporter
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.OptimizationContext
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.recorder.optimization.noise.NoiseActionFilter
import dev.accessscope.scanner.recorder.optimization.timing.WaitPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OTP/PIN su edit1…edit6 e tap pad numerico: step visibili, niente collasso indebito.
 */
class PinPadDigitSlotHealerTest {

    private val pkg = "it.nexi.bff"

    @Test
    fun heuristics_detectDigitSlotsAndPadKeys() {
        assertTrue(MaestroSelectorHeuristics.isPinPadDigitSlot("$pkg:id/edit1"))
        assertTrue(MaestroSelectorHeuristics.isPinPadDigitSlot("$pkg:id/edit6"))
        assertFalse(MaestroSelectorHeuristics.isPinPadDigitSlot("$pkg:id/edit_pass"))
        assertTrue(MaestroSelectorHeuristics.isPinPadKey("$pkg:id/uno", "1"))
    }

    @Test
    fun normalize_coalescesFullCodeOnSlotsAndDropsRedundantPadKeys() {
        val raw = listOf(
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit1", timestampMs = 1_000L),
            RecordedAction.WaitForAnimation(pkg, timeoutMs = 700L, timestampMs = 1_001L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit2", timestampMs = 1_100L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit6", timestampMs = 1_200L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/uno", text = "1", timestampMs = 2_000L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/due", text = "2", timestampMs = 2_100L),
            RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 3_000L),
            RecordedAction.InputText(pkg, "test", viewId = "$pkg:id/edt_user_code", timestampMs = 4_000L),
        )
        val cleaned = NoiseActionFilter.normalizePinOrOtpSlotInputs(raw)
        val slotInputs = cleaned.filterIsInstance<RecordedAction.InputText>()
            .filter { MaestroSelectorHeuristics.isPinPadDigitSlot(it.viewId) }
        assertEquals(1, slotInputs.size)
        assertEquals("123456", slotInputs.first().text)
        assertTrue(slotInputs.first().viewId!!.endsWith("edit1"))
        val padTaps = cleaned.filterIsInstance<RecordedAction.Tap>()
            .filter { MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) }
        assertTrue("pad ridondanti dopo codice completo devono essere eliminati", padTaps.isEmpty())
        assertTrue(cleaned.any { it is RecordedAction.InputText && it.viewId!!.contains("edt_user_code") })
    }

    @Test
    fun normalize_keepsSixRepeatedPadTapsAsSixSteps() {
        val raw = (1..6).map { i ->
            RecordedAction.Tap(
                pkg,
                viewId = "$pkg:id/uno",
                text = "1",
                timestampMs = 2_000L + i * 120L,
            )
        } + RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 3_500L)
        val cleaned = NoiseActionFilter.normalizePinOrOtpSlotInputs(raw)
        val padTaps = cleaned.filterIsInstance<RecordedAction.Tap>()
            .filter { MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) }
        assertEquals(6, padTaps.size)
        assertTrue(padTaps.all { it.executionMode == StepExecutionMode.Required })
        assertTrue(cleaned.none { it is RecordedAction.InputText })
    }

    @Test
    fun normalize_keepsDistinctPadTapsWithoutCoalescingToInputText() {
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 1_000L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/uno", text = "1", timestampMs = 2_000L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/due", text = "2", timestampMs = 2_100L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/tre", text = "3", timestampMs = 2_200L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/quattro", text = "4", timestampMs = 2_300L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/cinque", text = "5", timestampMs = 2_400L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/sei", text = "6", timestampMs = 2_500L),
            RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 3_000L),
        )
        val cleaned = NoiseActionFilter.normalizePinOrOtpSlotInputs(raw)
        val padTaps = cleaned.filterIsInstance<RecordedAction.Tap>()
            .filter { MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) }
        assertEquals(6, padTaps.size)
        assertTrue(cleaned.none { it is RecordedAction.InputText })
    }

    @Test
    fun normalize_keepsSingleDigitPerSlotAsSeparateInputs() {
        val raw = (1..6).map { slot ->
            RecordedAction.InputText(
                pkg,
                "1",
                viewId = "$pkg:id/edit$slot",
                timestampMs = 1_000L + slot * 100L,
            )
        }
        val cleaned = NoiseActionFilter.normalizePinOrOtpSlotInputs(raw)
        val slotInputs = cleaned.filterIsInstance<RecordedAction.InputText>()
            .filter { MaestroSelectorHeuristics.isPinPadDigitSlot(it.viewId) }
        assertEquals(6, slotInputs.size)
    }

    @Test
    fun pipeline_yamlHasSixPadTapsForRepeatedDigit() {
        val raw = (1..6).map { i ->
            RecordedAction.Tap(
                pkg,
                viewId = "$pkg:id/uno",
                text = "1",
                timestampMs = 2_000L + i * 100L,
            )
        }
        val optimized = FlowOptimizationPipeline.optimize(
            raw,
            OptimizationContext(appId = pkg),
        )
        val padTaps = optimized.filterIsInstance<RecordedAction.Tap>()
            .filter { MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) }
        assertEquals(6, padTaps.size)
        val yaml = MaestroYamlExporter.export(pkg, "PIN pad", optimized)
        assertEquals(6, yaml.split("tapOn").size - 1)
    }

    @Test
    fun sanitizeForPlay_keepsSixPadTapsDespiteWaitsBetween() {
        val enriched = WaitPlanner.enrich(
            (1..6).map { i ->
                RecordedAction.Tap(
                    pkg,
                    viewId = "$pkg:id/uno",
                    text = "1",
                    timestampMs = 2_000L + i * 150L,
                )
            },
            pkg,
            null,
            null,
        )
        val play = FlowOptimizationPipeline.sanitizeForPlay(enriched, pkg)
        val padTaps = play.filterIsInstance<RecordedAction.Tap>()
            .filter { MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) }
        assertEquals(6, padTaps.size)
    }

    @Test
    fun pipeline_yamlHasSingleEdit1InputWhenFullCodeOnSlots() {
        val raw = listOf(
            RecordedAction.LaunchApp(pkg),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit1", timestampMs = 1_000L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit2", timestampMs = 1_100L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit3", timestampMs = 1_200L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit4", timestampMs = 1_300L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit5", timestampMs = 1_400L),
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit6", timestampMs = 1_500L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/uno", text = "1", timestampMs = 2_000L),
            RecordedAction.Tap(pkg, text = "CONTINUA", timestampMs = 3_000L),
        )
        val optimized = FlowOptimizationPipeline.optimize(
            raw,
            OptimizationContext(appId = pkg),
        )
        val slotInputs = optimized.filterIsInstance<RecordedAction.InputText>()
            .filter { MaestroSelectorHeuristics.isPinPadDigitSlot(it.viewId) }
        assertEquals(1, slotInputs.size)
        assertEquals("123456", slotInputs.single().text)
        assertTrue(
            optimized.none {
                it is RecordedAction.Tap && it.viewId?.endsWith("/uno") == true
            },
        )

        val yaml = MaestroYamlExporter.export(pkg, "PIN test", optimized)
        assertTrue(yaml.contains("123456"))
        assertTrue(yaml.contains("edit1"))
        assertFalse(yaml.contains("uno"))
    }

    @Test
    fun sanitizeForPlay_keepsSlotInputDropsPadAfterFullCode() {
        val raw = listOf(
            RecordedAction.InputText(pkg, "123456", viewId = "$pkg:id/edit1", timestampMs = 1L),
            RecordedAction.Tap(pkg, viewId = "$pkg:id/uno", text = "1", timestampMs = 2L),
        )
        val play = FlowOptimizationPipeline.sanitizeForPlay(raw, pkg)
        assertEquals(1, play.count { it is RecordedAction.InputText })
        assertTrue(
            play.none {
                it is RecordedAction.Tap && it.viewId?.endsWith("/uno") == true
            },
        )
    }

    @Test
    fun dedupeTaps_doesNotCollapseRepeatedPinPadDigit() {
        val taps = (1..6).map { i ->
            RecordedAction.Tap(
                pkg,
                viewId = "$pkg:id/uno",
                text = "1",
                timestampMs = 1_000L + i * 100L,
            )
        }
        val deduped = FlowOptimizationPipeline.dedupeTaps(taps)
        assertEquals(6, deduped.size)
    }
}
