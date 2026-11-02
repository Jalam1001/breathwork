package com.javed.breathwork

import com.javed.breathwork.domain.*
import com.javed.breathwork.engine.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test

class ControllerTest {
    private var now = 0L
    private val seen = mutableListOf<PhaseKind>(); private var holds = 0; private var finished = 0
    private val cues = object : Cues {
        override fun onSegment(segment: Segment) { seen += segment.step.kind }
        override fun onHoldSecond() { holds++ }
        override fun onFinished() { finished++ }
    }
    private var done: SessionState? = null
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val c = SessionController(scope, { now }, cues, { done = it }, tickMs = 1_000_000)

    private fun run(ms: Long, step: Long = 100) { var t = 0L; while (t < ms) { now += step; t += step; c.tick() } }

    @Test fun boxRunsInOrderWithLeadInAndFinishes() {
        c.start(SessionConfig(Techniques.box, 0, 1))
        assertEquals(RunState.LEAD_IN, c.state.value.run)
        run(3000); assertEquals(RunState.RUNNING, c.state.value.run)
        run(48_000)
        assertEquals(RunState.FINISHED, c.state.value.run); assertTrue(c.state.value.completed)
        assertEquals(12, seen.size)
        assertEquals(listOf(PhaseKind.INHALE, PhaseKind.HOLD_FULL, PhaseKind.EXHALE, PhaseKind.HOLD_EMPTY), seen.take(4))
        assertEquals(1, finished); assertEquals(48_000L, done?.elapsedMs)
        assertEquals(3 * 2 * 3, holds)   // 3 ticks per 4 s hold (seconds 1..3), 2 holds per cycle, 3 cycles
    }
    @Test fun pauseFreezesTime() {
        c.start(SessionConfig(Techniques.extendedExhale, 0, 1), leadInMs = 0)
        run(5000); val e = c.state.value.elapsedMs
        c.pause(); now += 60_000; c.tick(); assertEquals(e, c.state.value.elapsedMs)
        c.resume(); run(1000); assertEquals(e + 1000, c.state.value.elapsedMs)
        assertEquals(PhaseKind.EXHALE, c.state.value.segment!!.step.kind)
    }
    @Test fun endEarlyRecordsElapsed() {
        c.start(SessionConfig(Techniques.cyclicSigh, 0, 5), leadInMs = 0)
        run(20_000); c.end()
        assertFalse(done!!.completed); assertEquals(20_000L, done!!.elapsedMs); assertEquals(0, finished)
    }
    @Test fun lateTicksDoNotDrift() {
        c.start(SessionConfig(Techniques.resonance, 0, 1), leadInMs = 0)
        now += 16_600; c.tick()
        assertEquals(16_600L, c.state.value.elapsedMs); assertEquals(PhaseKind.EXHALE, c.state.value.segment!!.step.kind)
        assertEquals(3, c.state.value.index)
    }
}
