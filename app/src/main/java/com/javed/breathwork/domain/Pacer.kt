package com.javed.breathwork.domain

import kotlin.math.PI
import kotlin.math.cos

object Pacer {
    const val MIN = 0.35f
    const val MID = 0.80f   // end of first inhale when a top-up inhale follows
    const val MAX = 1.0f
    private const val FAST_LO = 0.5f
    private const val FAST_HI = 0.9f

    fun ease(f: Float): Float = (0.5 - 0.5 * cos(PI * f.coerceIn(0f, 1f))).toFloat()

    /** Circle radius scale (MIN..MAX) for a phase at progress [fraction]. */
    fun scale(kind: PhaseKind, fraction: Float, hasTopUp: Boolean): Float {
        val e = ease(fraction)
        fun lerp(a: Float, b: Float) = a + (b - a) * e
        return when (kind) {
            PhaseKind.INHALE -> lerp(MIN, if (hasTopUp) MID else MAX)
            PhaseKind.TOP_UP -> lerp(MID, MAX)
            PhaseKind.HOLD_FULL -> MAX
            PhaseKind.EXHALE -> lerp(MAX, MIN)
            PhaseKind.HOLD_EMPTY -> MIN
            PhaseKind.BREATHE_IN -> lerp(FAST_LO, FAST_HI)
            PhaseKind.BREATHE_OUT -> lerp(FAST_HI, FAST_LO)
            PhaseKind.EXHALE_ALL -> lerp(FAST_LO, MIN)
        }
    }

    /**
     * Box dot position in unit square (0,0 top-left). Side order: inhale goes up the left side,
     * hold full across the top, exhale down the right side, hold empty along the bottom.
     */
    fun boxPoint(sideIndex: Int, fraction: Float): Pair<Float, Float> {
        val f = fraction.coerceIn(0f, 1f)
        return when (sideIndex % 4) {
            0 -> 0f to 1f - f
            1 -> f to 0f
            2 -> 1f to f
            else -> 1f - f to 1f
        }
    }
}
