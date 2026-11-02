package com.javed.breathwork.domain

data class Segment(val step: Step, val startMs: Long, val cycle: Int, val indexInCycle: Int, val cycleSize: Int) {
    val endMs get() = startMs + step.ms
}

data class SessionConfig(
    val technique: Technique,
    val variantIndex: Int = 0,
    val minutes: Int = technique.defaultMinutes,
    val rounds: Int = technique.defaultRounds,
    val hyper: HyperParams = HyperParams(),
) {
    val variant get() = technique.variants[variantIndex.coerceIn(0, technique.variants.lastIndex)]
    val cycleSteps: List<Step> get() = if (technique.id == Techniques.hyper.id) hyper.roundSteps() else variant.steps
}

/**
 * Whole cycles only. Timed sessions use as many complete cycles as fit in the chosen minutes (at least one),
 * so a session never ends mid-breath. Rounds sessions run exactly the requested number of cycles.
 */
class Timeline(val config: SessionConfig) {
    val cycleMs: Long = config.cycleSteps.sumOf { it.ms }
    val cycles: Int = when (config.technique.mode) {
        Mode.TIMED -> maxOf(1, ((config.minutes * 60_000L) / cycleMs).toInt())
        Mode.ROUNDS -> config.rounds.coerceAtLeast(1)
    }
    val segments: List<Segment> = buildList {
        var t = 0L
        val steps = config.cycleSteps
        repeat(cycles) { c -> steps.forEachIndexed { i, s -> add(Segment(s, t, c, i, steps.size)); t += s.ms } }
    }
    val totalMs: Long = cycles * cycleMs

    fun indexAt(elapsedMs: Long): Int {
        if (elapsedMs >= totalMs) return segments.lastIndex
        var lo = 0; var hi = segments.lastIndex
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (segments[mid].startMs <= elapsedMs) lo = mid else hi = mid - 1 }
        return lo
    }
}
