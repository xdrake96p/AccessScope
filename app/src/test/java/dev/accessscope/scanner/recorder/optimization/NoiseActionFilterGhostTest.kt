package dev.accessscope.scanner.recorder.optimization.noise

import dev.accessscope.scanner.recorder.RecordedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseActionFilterGhostTest {

    @Test
    fun dropsPointOnlyTapAfterScroll() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Scroll(pkg, timestampMs = 1_000L),
            RecordedAction.Tap(
                pkg,
                pointPercentX = 50f,
                pointPercentY = 50f,
                timestampMs = 1_100L,
            ),
            RecordedAction.Tap(pkg, text = "OK", timestampMs = 2_000L),
        )
        val out = NoiseActionFilter.dropGhostTapsAfterScrollOrIme(raw)
        assertEquals(2, out.size)
        assertTrue(out.last() is RecordedAction.Tap && (out.last() as RecordedAction.Tap).text == "OK")
    }

    @Test
    fun dropsDuplicateSectionTapsAcrossWaits() {
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 1_000L),
            RecordedAction.WaitForAnimation(pkg, timeoutMs = 500L, timestampMs = 1_100L),
            RecordedAction.Wait(pkg, timeoutMs = 2_000L, timestampMs = 1_200L),
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 1_500L),
        )
        val out = NoiseActionFilter.dropDuplicateTapsAcrossWaits(raw)
        assertEquals(3, out.size)
        assertEquals(1, out.count { it is RecordedAction.Tap })
    }

    @Test
    fun pinPadDigitTaps_notCollapsedAcrossWaits() {
        val pkg = "it.nexi.bff"
        val raw = (1..6).flatMap { i ->
            listOf(
                RecordedAction.Tap(pkg, viewId = "$pkg:id/uno", text = "1", timestampMs = 1_000L + i * 150L),
                RecordedAction.WaitForAnimation(pkg, timeoutMs = 500L, timestampMs = 1_050L + i * 150L),
            )
        }
        val out = NoiseActionFilter.dropDuplicateTapsAcrossWaits(raw)
        assertEquals(6, out.count { it is RecordedAction.Tap })
    }

    @Test
    fun sameTextTaps_withinGap_areMerged_matchesRealAxaRecording() {
        // Gap reale osservato su un flusso AXA registrato: doppi tap umani per incertezza sul
        // primo, tutti a 0.8-1.8s di distanza — ampiamente sotto il tetto di 4s.
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 1_000L),
            RecordedAction.WaitForAnimation(pkg, timeoutMs = 500L, timestampMs = 1_100L),
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 2_758L),
        )
        val out = NoiseActionFilter.dropDuplicateTapsAcrossWaits(raw)
        assertEquals(1, out.count { it is RecordedAction.Tap })
    }

    @Test
    fun sameTextTaps_beyondGap_bothKept() {
        // Senza un tetto, due tap sullo stesso testo separati da un caricamento lento verrebbero
        // uniti come un doppio tap umano — un rischio strutturale, anche se non osservato su
        // questo flusso specifico.
        val pkg = "com.app"
        val raw = listOf(
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 1_000L),
            RecordedAction.Wait(pkg, timeoutMs = 10_000L, timestampMs = 1_100L),
            RecordedAction.Tap(pkg, text = "Le mie garanzie", timestampMs = 10_000L),
        )
        val out = NoiseActionFilter.dropDuplicateTapsAcrossWaits(raw)
        assertEquals(2, out.count { it is RecordedAction.Tap })
    }
}
