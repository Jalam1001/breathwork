package com.javed.breathwork.domain

enum class Purpose(val label: String) { CALM("Calm"), FOCUS("Focus"), ENERGY("Energy"), SLEEP("Sleep") }

/** What the lungs are doing in a step; drives visual, sound, voice and haptics. */
enum class PhaseKind(val spoken: String) {
    INHALE("Inhale"), TOP_UP("Inhale again"), HOLD_FULL("Hold"), EXHALE("Exhale"), HOLD_EMPTY("Hold"),
    BREATHE_IN("In"), BREATHE_OUT("Out"), EXHALE_ALL("Exhale fully");

    val isInhale get() = this == INHALE || this == TOP_UP || this == BREATHE_IN
    val isExhale get() = this == EXHALE || this == BREATHE_OUT || this == EXHALE_ALL
    val isHold get() = this == HOLD_FULL || this == HOLD_EMPTY
}

data class Step(val kind: PhaseKind, val ms: Long, val label: String = kind.spoken)

enum class Visual { CIRCLE, SQUARE }
enum class Mode { TIMED, ROUNDS }

/** A user-selectable variant (e.g. box side length). */
data class Variant(val name: String, val steps: List<Step>)

data class Technique(
    val id: String,
    val name: String,
    val purpose: Purpose,
    val summary: String,
    val howTo: String,
    val source: String,
    val variants: List<Variant>,
    val visual: Visual = Visual.CIRCLE,
    val mode: Mode = Mode.TIMED,
    val defaultMinutes: Int = 5,
    val defaultRounds: Int = 3,
    val needsWarning: Boolean = false,
) {
    fun timingText(v: Variant = variants.first()): String =
        v.steps.joinToString(" · ") { "${it.label} ${fmtSec(it.ms)}" }
}

fun sec(s: Double) = (s * 1000).toLong()
fun fmtSec(ms: Long): String = if (ms % 1000 == 0L) "${ms / 1000}s" else "%.1fs".format(ms / 1000.0)

/** Cyclic hyperventilation round parameters (rounds mode). */
data class HyperParams(val breaths: Int = 30, val holdSeconds: Int = 15) {
    fun roundSteps(): List<Step> = buildList {
        repeat(breaths) { add(Step(PhaseKind.BREATHE_IN, 1500)); add(Step(PhaseKind.BREATHE_OUT, 1000)) }
        add(Step(PhaseKind.EXHALE_ALL, 3000))
        add(Step(PhaseKind.HOLD_EMPTY, holdSeconds * 1000L, "Hold, lungs empty"))
        add(Step(PhaseKind.INHALE, 4000, "Recovery inhale"))
        add(Step(PhaseKind.EXHALE, 4000, "Relax"))
    }
}

object Techniques {
    val cyclicSigh = Technique(
        id = "cyclic_sigh", name = "Cyclic sighing", purpose = Purpose.CALM,
        summary = "Two inhales through the nose, then a long, slow exhale through the mouth. Best-performing practice in the Stanford 5-minute study.",
        howTo = "Inhale deeply through the nose. At the top, sip in a short second inhale to fully fill the lungs. Then exhale slowly through the mouth until empty. Repeat for about 5 minutes.",
        source = "Balban et al. 2023, Cell Reports Medicine; Huberman Lab newsletter \"Breathwork Protocols\"",
        variants = listOf(
            Variant("Standard (≈6 breaths/min)", listOf(Step(PhaseKind.INHALE, 3000), Step(PhaseKind.TOP_UP, 1000), Step(PhaseKind.EXHALE, 6000, "Long exhale"))),
            Variant("Slower (≈5 breaths/min)", listOf(Step(PhaseKind.INHALE, 3500), Step(PhaseKind.TOP_UP, 1500), Step(PhaseKind.EXHALE, 7000, "Long exhale"))),
        ),
    )
    val sighReset = Technique(
        id = "sigh_reset", name = "Physiological sigh (quick reset)", purpose = Purpose.CALM,
        summary = "1–3 double-inhale sighs to calm down in real time, in under a minute.",
        howTo = "Big inhale through the nose, a short top-up inhale, then exhale all the way out through the mouth. One to three sighs are usually enough.",
        source = "Huberman Lab, \"Tools for Managing Stress & Anxiety\"; Dr. Jack Feldman episode",
        variants = cyclicSigh.variants.take(1), mode = Mode.ROUNDS, defaultRounds = 3,
    )
    val box = Technique(
        id = "box", name = "Box breathing", purpose = Purpose.FOCUS,
        summary = "Equal inhale, hold, exhale, hold. Steadies the mind while you stay alert.",
        howTo = "Inhale through the nose, hold full, exhale, hold empty, each for the same count. Choose the side length from your CO₂ tolerance test: short exhale (<20 s) → 3–4 s, 25–45 s → 5–6 s, 50 s+ → 8–10 s.",
        source = "Balban et al. 2023 (protocol table); Huberman Lab, \"How to Breathe Correctly\"",
        visual = Visual.SQUARE,
        variants = listOf(4, 5, 8, 3).map { s ->
            Variant("$s-second sides", listOf(Step(PhaseKind.INHALE, s * 1000L), Step(PhaseKind.HOLD_FULL, s * 1000L), Step(PhaseKind.EXHALE, s * 1000L), Step(PhaseKind.HOLD_EMPTY, s * 1000L)))
        },
    )
    val extendedExhale = Technique(
        id = "extended_exhale", name = "Extended exhale", purpose = Purpose.CALM,
        summary = "Exhales twice as long as inhales to slow the heart rate (exhale-emphasis).",
        howTo = "Inhale through the nose for 4, exhale slowly through the mouth or nose for 8. Longer exhales than inhales shift you toward calm.",
        source = "Huberman Lab, \"Tools for Managing Stress & Anxiety\" (inhale vs. exhale and heart rate)",
        variants = listOf(
            Variant("4 in · 8 out", listOf(Step(PhaseKind.INHALE, 4000), Step(PhaseKind.EXHALE, 8000))),
            Variant("3 in · 6 out", listOf(Step(PhaseKind.INHALE, 3000), Step(PhaseKind.EXHALE, 6000))),
        ),
    )
    val resonance = Technique(
        id = "resonance", name = "Resonance (coherent) breathing", purpose = Purpose.CALM,
        summary = "Smooth, equal breaths at about 5.5–6 per minute, the rate linked to the largest heart-rate variability.",
        howTo = "Breathe gently through the nose: in for 5.5 seconds, out for 5.5 seconds, with no pauses.",
        source = "Lin et al. (5.5 bpm) via PMC8924557; James Nestor on Huberman Lab",
        variants = listOf(
            Variant("5.5 breaths/min (5.5 · 5.5)", listOf(Step(PhaseKind.INHALE, 5500), Step(PhaseKind.EXHALE, 5500))),
            Variant("6 breaths/min (5 · 5)", listOf(Step(PhaseKind.INHALE, 5000), Step(PhaseKind.EXHALE, 5000))),
        ),
        defaultMinutes = 10,
    )
    val fourSevenEight = Technique(
        id = "478", name = "4-7-8 breathing", purpose = Purpose.SLEEP,
        summary = "Inhale 4, hold 7, exhale 8. A long-hold, long-exhale pattern for winding down at night.",
        howTo = "Inhale quietly through the nose for 4, hold for 7, exhale through the mouth for 8. Start with 4 cycles and build up slowly.",
        source = "Dr. Andrew Weil (popular long-exhale technique; not part of the Stanford trial)",
        variants = listOf(Variant("4 · 7 · 8", listOf(Step(PhaseKind.INHALE, 4000), Step(PhaseKind.HOLD_FULL, 7000), Step(PhaseKind.EXHALE, 8000)))),
        defaultMinutes = 3,
    )
    val hyper = Technique(
        id = "cyclic_hyper", name = "Cyclic hyperventilation", purpose = Purpose.ENERGY,
        summary = "Rounds of fast, deep breaths followed by an empty-lung hold. Raises adrenaline and alertness (similar to Wim Hof breathing).",
        howTo = "Deep inhale through the nose, let the air fall out through the mouth. Repeat 25–30 times, exhale fully, and hold with lungs empty for 15–30 s. Repeat the rounds.",
        source = "Balban et al. 2023 (30 breaths, 15 s hold, rounds); Huberman Lab Daily Blueprint (20–25 breaths, 15–30 s hold)",
        variants = listOf(Variant("30 breaths · 15 s hold", HyperParams().roundSteps())),
        mode = Mode.ROUNDS, defaultRounds = 3, needsWarning = true,
    )

    val all = listOf(cyclicSigh, box, extendedExhale, resonance, fourSevenEight, sighReset, hyper)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: cyclicSigh
}
