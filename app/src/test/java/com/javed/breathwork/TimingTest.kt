package com.javed.breathwork

import com.javed.breathwork.data.HistoryEntry
import com.javed.breathwork.data.Streaks
import com.javed.breathwork.domain.*
import com.javed.breathwork.engine.Haptics
import com.javed.breathwork.engine.ToneSynth
import org.junit.Assert.*
import org.junit.Test

class TimingTest {
    private fun kinds(t: Technique, v: Int = 0) = t.variants[v].steps.map { it.kind }
    private fun ms(t: Technique, v: Int = 0) = t.variants[v].steps.map { it.ms }

    @Test fun cyclicSighPattern() {
        assertEquals(listOf(PhaseKind.INHALE, PhaseKind.TOP_UP, PhaseKind.EXHALE), kinds(Techniques.cyclicSigh))
        assertEquals(listOf(3000L, 1000L, 6000L), ms(Techniques.cyclicSigh))
        assertEquals(listOf(3500L, 1500L, 7000L), ms(Techniques.cyclicSigh, 1))
        // exhale longer than both inhales combined
        Techniques.cyclicSigh.variants.forEach { v -> assertTrue(v.steps[2].ms > v.steps[0].ms + v.steps[1].ms) }
    }
    @Test fun boxPatternsEqualSides() {
        assertEquals(listOf(PhaseKind.INHALE, PhaseKind.HOLD_FULL, PhaseKind.EXHALE, PhaseKind.HOLD_EMPTY), kinds(Techniques.box))
        assertEquals(listOf(4000L, 5000L, 8000L, 3000L), Techniques.box.variants.map { v -> v.steps.map { it.ms }.distinct().single() })
    }
    @Test fun extendedExhaleIsTwiceInhale() {
        Techniques.extendedExhale.variants.forEach { v -> assertEquals(v.steps[0].ms * 2, v.steps[1].ms) }
        assertEquals(listOf(4000L, 8000L), ms(Techniques.extendedExhale))
    }
    @Test fun resonanceRate() {
        val cycle = Techniques.resonance.variants[0].steps.sumOf { it.ms }
        assertEquals(11_000L, cycle); assertEquals(5.4545, 60_000.0 / cycle, 0.01)
        assertEquals(6.0, 60_000.0 / Techniques.resonance.variants[1].steps.sumOf { it.ms }, 1e-9)
    }
    @Test fun fourSevenEight() { assertEquals(listOf(4000L, 7000L, 8000L), ms(Techniques.fourSevenEight)) }
    @Test fun hyperRound() {
        val steps = HyperParams(30, 15).roundSteps()
        assertEquals(30, steps.count { it.kind == PhaseKind.BREATHE_IN }); assertEquals(30, steps.count { it.kind == PhaseKind.BREATHE_OUT })
        assertEquals(PhaseKind.HOLD_EMPTY, steps[61].kind); assertEquals(15_000L, steps[61].ms)
        assertEquals(30 * 2500L + 3000 + 15000 + 8000, steps.sumOf { it.ms })
        assertEquals(25 * 2500L + 3000 + 30000 + 8000, HyperParams(25, 30).roundSteps().sumOf { it.ms })
    }

    private fun total(t: Technique, minutes: Int = 5, v: Int = 0, rounds: Int = t.defaultRounds) = Timeline(SessionConfig(t, v, minutes, rounds))

    @Test fun exactSessionTotals() {
        assertEquals(300_000L, total(Techniques.cyclicSigh).totalMs); assertEquals(30, total(Techniques.cyclicSigh).cycles)
        assertEquals(288_000L, total(Techniques.box).totalMs); assertEquals(18, total(Techniques.box).cycles)
        assertEquals(48_000L, total(Techniques.box, 1).totalMs)
        assertEquals(32_000L, total(Techniques.box, 1, v = 2).totalMs)   // 8 s sides: 1 min fits 1 cycle
        assertEquals(300_000L, total(Techniques.extendedExhale).totalMs)
        assertEquals(594_000L, total(Techniques.resonance, 10).totalMs)
        assertEquals(1_199_000L, total(Techniques.resonance, 20).totalMs)
        assertEquals(171_000L, total(Techniques.fourSevenEight, 3).totalMs)
        assertEquals(30_000L, total(Techniques.sighReset).totalMs)
        assertEquals(10_000L, total(Techniques.sighReset, rounds = 1).totalMs)
        assertEquals(303_000L, total(Techniques.hyper).totalMs)
        assertEquals(505_000L, total(Techniques.hyper, rounds = 5).totalMs)
    }
    @Test fun everyTechniqueEveryDurationNeverExceedsChosenTime() {
        for (t in Techniques.all.filter { it.mode == Mode.TIMED }) for (v in t.variants.indices) for (m in listOf(1, 3, 5, 10, 15, 20, 37)) {
            val tl = total(t, m, v)
            assertTrue("${t.id} $m", tl.totalMs <= m * 60_000L || tl.cycles == 1)
            assertTrue("${t.id} $m", m * 60_000L - tl.totalMs < tl.cycleMs)
            assertEquals(tl.totalMs, tl.segments.sumOf { it.step.ms })
            assertEquals(tl.totalMs, tl.segments.last().endMs)
            tl.segments.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        }
    }
    @Test fun indexAtFindsSegments() {
        val tl = total(Techniques.box, 1)
        assertEquals(0, tl.indexAt(0)); assertEquals(0, tl.indexAt(3999)); assertEquals(1, tl.indexAt(4000))
        assertEquals(4, tl.indexAt(16_000)); assertEquals(tl.segments.lastIndex, tl.indexAt(99_999))
    }
    @Test fun pacerShapes() {
        assertEquals(Pacer.MIN, Pacer.scale(PhaseKind.INHALE, 0f, true), 1e-6f)
        assertEquals(Pacer.MID, Pacer.scale(PhaseKind.INHALE, 1f, true), 1e-6f)
        assertEquals(Pacer.MAX, Pacer.scale(PhaseKind.TOP_UP, 1f, true), 1e-6f)
        assertEquals(Pacer.MAX, Pacer.scale(PhaseKind.INHALE, 1f, false), 1e-6f)
        assertEquals(Pacer.MIN, Pacer.scale(PhaseKind.EXHALE, 1f, false), 1e-6f)
        assertEquals(0f to 1f, Pacer.boxPoint(0, 0f)); assertEquals(0f to 0f, Pacer.boxPoint(0, 1f))
        assertEquals(1f to 0f, Pacer.boxPoint(1, 1f)); assertEquals(1f to 1f, Pacer.boxPoint(2, 1f)); assertEquals(0f to 1f, Pacer.boxPoint(3, 1f))
    }
    @Test fun tonesAndHaptics() {
        assertEquals(ToneSynth.RATE * 2, ToneSynth.inhale(2000).size)
        assertEquals(ToneSynth.RATE * 2500 / 1000, ToneSynth.inhale(10_000).size) // capped
        assertTrue(ToneSynth.exhale(6000).isNotEmpty() && ToneSynth.tick().isNotEmpty())
        assertEquals(2, Haptics.pattern(PhaseKind.INHALE).size); assertEquals(4, Haptics.pattern(PhaseKind.EXHALE).size)
    }
    @Test fun streaksAndHistoryCodec() {
        assertEquals(3, Streaks.current(setOf(10, 9, 8, 5), 10)); assertEquals(3, Streaks.current(setOf(9, 8, 7), 10))
        assertEquals(0, Streaks.current(setOf(7), 10))
        val e = HistoryEntry(20000, "box", 288, true); assertEquals(e, HistoryEntry.decode(e.encode())); assertNull(HistoryEntry.decode("bad"))
    }
}
