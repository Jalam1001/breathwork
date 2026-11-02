package com.javed.breathwork.engine

import com.javed.breathwork.domain.Segment
import com.javed.breathwork.domain.SessionConfig
import com.javed.breathwork.domain.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RunState { IDLE, LEAD_IN, RUNNING, PAUSED, FINISHED }

data class SessionState(
    val run: RunState = RunState.IDLE,
    val timeline: Timeline? = null,
    val elapsedMs: Long = 0,
    val leadInMs: Long = 0,
    val index: Int = 0,
    val completed: Boolean = false,
) {
    val segment: Segment? get() = timeline?.segments?.getOrNull(index)
    val totalMs get() = timeline?.totalMs ?: 0
    val remainingMs get() = (totalMs - elapsedMs).coerceAtLeast(0)
    val progress get() = if (totalMs == 0L) 0f else (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val phaseElapsedMs get() = segment?.let { (elapsedMs - it.startMs).coerceIn(0, it.step.ms) } ?: 0
    val phaseRemainingMs get() = segment?.let { it.step.ms - phaseElapsedMs } ?: 0
    val phaseFraction get() = segment?.let { phaseElapsedMs.toFloat() / it.step.ms } ?: 0f
    val isActive get() = run == RunState.LEAD_IN || run == RunState.RUNNING || run == RunState.PAUSED
}

interface Cues {
    fun onSegment(segment: Segment)
    fun onLeadInTick(secondsLeft: Int) {}
    fun onHoldSecond() {}
    fun onFinished() {}
    fun stop() {}
}

fun interface Clock { fun now(): Long }

/** Drives a timeline in real time. Timing is based on a monotonic clock, so ticks may be late without drifting. */
class SessionController(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val cues: Cues,
    private val onComplete: (SessionState) -> Unit = {},
    private val tickMs: Long = 50,
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private var job: Job? = null
    private var anchor = 0L       // clock time corresponding to elapsed = 0 (or lead-in start)
    private var lastHoldSecond = -1L

    fun start(config: SessionConfig, leadInMs: Long = LEAD_IN_MS) {
        job?.cancel(); cues.stop()
        val tl = Timeline(config)
        lastHoldSecond = -1
        anchor = clock.now() + leadInMs
        _state.value = SessionState(run = if (leadInMs > 0) RunState.LEAD_IN else RunState.RUNNING, timeline = tl, leadInMs = leadInMs)
        if (leadInMs > 0) cues.onLeadInTick(((leadInMs + 999) / 1000).toInt()) else cues.onSegment(tl.segments.first())
        job = scope.launch { while (isActive) { tick(); delay(tickMs) } }
    }

    fun tick() {
        val s = _state.value
        val tl = s.timeline ?: return
        when (s.run) {
            RunState.LEAD_IN -> {
                val left = anchor - clock.now()
                if (left > 0) {
                    val prevSec = ((s.leadInMs + 999) / 1000).toInt(); val sec = ((left + 999) / 1000).toInt()
                    _state.value = s.copy(leadInMs = left)
                    if (sec != prevSec) cues.onLeadInTick(sec)
                } else {
                    _state.value = s.copy(run = RunState.RUNNING, leadInMs = 0, elapsedMs = 0, index = 0)
                    cues.onSegment(tl.segments.first())
                    advance()
                }
            }
            RunState.RUNNING -> advance()
            else -> Unit
        }
    }

    private fun advance() {
        val s = _state.value; val tl = s.timeline ?: return
        val elapsed = (clock.now() - anchor).coerceAtLeast(0)
        if (elapsed >= tl.totalMs) { finish(true); return }
        val idx = tl.indexAt(elapsed)
        _state.value = s.copy(elapsedMs = elapsed, index = idx)
        if (idx != s.index) cues.onSegment(tl.segments[idx])
        val seg = tl.segments[idx]
        if (seg.step.kind.isHold) {
            val sec = (elapsed - seg.startMs) / 1000
            val key = seg.startMs * 1000 + sec
            if (sec > 0 && key != lastHoldSecond) { lastHoldSecond = key; cues.onHoldSecond() }
        }
    }

    fun pause() {
        val s = _state.value
        if (s.run != RunState.RUNNING && s.run != RunState.LEAD_IN) return
        if (s.run == RunState.RUNNING) _state.value = s.copy(elapsedMs = (clock.now() - anchor).coerceIn(0, s.totalMs))
        _state.value = _state.value.copy(run = RunState.PAUSED)
        cues.stop()
    }

    fun resume() {
        val s = _state.value
        if (s.run != RunState.PAUSED) return
        anchor = clock.now() - s.elapsedMs
        _state.value = s.copy(run = RunState.RUNNING, leadInMs = 0)
        s.segment?.let { cues.onSegment(it) }
    }

    fun end() { if (_state.value.isActive) finish(false) }

    fun reset() { job?.cancel(); cues.stop(); _state.value = SessionState() }

    private fun finish(completed: Boolean) {
        job?.cancel()
        val s = _state.value
        val elapsed = if (completed) s.totalMs else s.elapsedMs
        val fin = s.copy(run = RunState.FINISHED, elapsedMs = elapsed, completed = completed)
        _state.value = fin
        cues.stop()
        if (completed) cues.onFinished()
        onComplete(fin)
    }

    companion object { const val LEAD_IN_MS = 3000L }
}
