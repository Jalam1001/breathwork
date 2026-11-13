// BreathWork domain logic: techniques, timeline, pacer geometry, streaks, history codec.
// Pure ES module with no DOM access, so it runs in the browser and in Node tests.
// Ported 1:1 from the Android app (domain/Techniques.kt, Timeline.kt, Pacer.kt, data/Store.kt).

export const Purpose = { CALM: 'Calm', FOCUS: 'Focus', ENERGY: 'Energy', SLEEP: 'Sleep' };

export const PhaseKind = {
  INHALE: { id: 'INHALE', spoken: 'Inhale' },
  TOP_UP: { id: 'TOP_UP', spoken: 'Inhale again' },
  HOLD_FULL: { id: 'HOLD_FULL', spoken: 'Hold' },
  EXHALE: { id: 'EXHALE', spoken: 'Exhale' },
  HOLD_EMPTY: { id: 'HOLD_EMPTY', spoken: 'Hold' },
  BREATHE_IN: { id: 'BREATHE_IN', spoken: 'In' },
  BREATHE_OUT: { id: 'BREATHE_OUT', spoken: 'Out' },
  EXHALE_ALL: { id: 'EXHALE_ALL', spoken: 'Exhale fully' },
};
const K = PhaseKind;
export const isInhale = (k) => k === K.INHALE || k === K.TOP_UP || k === K.BREATHE_IN;
export const isExhale = (k) => k === K.EXHALE || k === K.BREATHE_OUT || k === K.EXHALE_ALL;
export const isHold = (k) => k === K.HOLD_FULL || k === K.HOLD_EMPTY;

export const step = (kind, ms, label = kind.spoken) => ({ kind, ms, label });
export const fmtSec = (ms) => (ms % 1000 === 0 ? `${ms / 1000}s` : `${(ms / 1000).toFixed(1)}s`);
export const timingText = (steps) => steps.map((s) => `${s.label} ${fmtSec(s.ms)}`).join(' · ');

export const HYPER_DEFAULT = { breaths: 30, holdSeconds: 15 };
export function hyperRoundSteps({ breaths = 30, holdSeconds = 15 } = {}) {
  const out = [];
  for (let i = 0; i < breaths; i++) { out.push(step(K.BREATHE_IN, 1500)); out.push(step(K.BREATHE_OUT, 1000)); }
  out.push(step(K.EXHALE_ALL, 3000));
  out.push(step(K.HOLD_EMPTY, holdSeconds * 1000, 'Hold, lungs empty'));
  out.push(step(K.INHALE, 4000, 'Recovery inhale'));
  out.push(step(K.EXHALE, 4000, 'Relax'));
  return out;
}

const sighVariants = [
  { name: 'Standard (≈6 breaths/min)', steps: [step(K.INHALE, 3000), step(K.TOP_UP, 1000), step(K.EXHALE, 6000, 'Long exhale')] },
  { name: 'Slower (≈5 breaths/min)', steps: [step(K.INHALE, 3500), step(K.TOP_UP, 1500), step(K.EXHALE, 7000, 'Long exhale')] },
];

function technique(t) {
  return { visual: 'circle', mode: 'timed', defaultMinutes: 5, defaultRounds: 3, needsWarning: false, ...t };
}

export const Techniques = {
  cyclicSigh: technique({
    id: 'cyclic_sigh', name: 'Cyclic sighing', purpose: Purpose.CALM,
    summary: 'Two inhales through the nose, then a long, slow exhale through the mouth. Best-performing practice in the Stanford 5-minute study.',
    howTo: 'Inhale deeply through the nose. At the top, sip in a short second inhale to fully fill the lungs. Then exhale slowly through the mouth until empty. Repeat for about 5 minutes.',
    source: 'Balban et al. 2023, Cell Reports Medicine; Huberman Lab newsletter "Breathwork Protocols"',
    variants: sighVariants,
  }),
  sighReset: technique({
    id: 'sigh_reset', name: 'Physiological sigh (quick reset)', purpose: Purpose.CALM,
    summary: '1–3 double-inhale sighs to calm down in real time, in under a minute.',
    howTo: 'Big inhale through the nose, a short top-up inhale, then exhale all the way out through the mouth. One to three sighs are usually enough.',
    source: 'Huberman Lab, "Tools for Managing Stress & Anxiety"; Dr. Jack Feldman episode',
    variants: sighVariants.slice(0, 1), mode: 'rounds', defaultRounds: 3,
  }),
  box: technique({
    id: 'box', name: 'Box breathing', purpose: Purpose.FOCUS,
    summary: 'Equal inhale, hold, exhale, hold. Steadies the mind while you stay alert.',
    howTo: 'Inhale through the nose, hold full, exhale, hold empty, each for the same count. Choose the side length from your CO₂ tolerance test: short exhale (<20 s) → 3–4 s, 25–45 s → 5–6 s, 50 s+ → 8–10 s.',
    source: 'Balban et al. 2023 (protocol table); Huberman Lab, "How to Breathe Correctly"',
    visual: 'square',
    variants: [4, 5, 8, 3].map((s) => ({ name: `${s}-second sides`, steps: [step(K.INHALE, s * 1000), step(K.HOLD_FULL, s * 1000), step(K.EXHALE, s * 1000), step(K.HOLD_EMPTY, s * 1000)] })),
  }),
  extendedExhale: technique({
    id: 'extended_exhale', name: 'Extended exhale', purpose: Purpose.CALM,
    summary: 'Exhales twice as long as inhales to slow the heart rate (exhale-emphasis).',
    howTo: 'Inhale through the nose for 4, exhale slowly through the mouth or nose for 8. Longer exhales than inhales shift you toward calm.',
    source: 'Huberman Lab, "Tools for Managing Stress & Anxiety" (inhale vs. exhale and heart rate)',
    variants: [
      { name: '4 in · 8 out', steps: [step(K.INHALE, 4000), step(K.EXHALE, 8000)] },
      { name: '3 in · 6 out', steps: [step(K.INHALE, 3000), step(K.EXHALE, 6000)] },
    ],
  }),
  resonance: technique({
    id: 'resonance', name: 'Resonance (coherent) breathing', purpose: Purpose.CALM,
    summary: 'Smooth, equal breaths at about 5.5–6 per minute, the rate linked to the largest heart-rate variability.',
    howTo: 'Breathe gently through the nose: in for 5.5 seconds, out for 5.5 seconds, with no pauses.',
    source: 'Lin et al. (5.5 bpm) via PMC8924557; James Nestor on Huberman Lab',
    variants: [
      { name: '5.5 breaths/min (5.5 · 5.5)', steps: [step(K.INHALE, 5500), step(K.EXHALE, 5500)] },
      { name: '6 breaths/min (5 · 5)', steps: [step(K.INHALE, 5000), step(K.EXHALE, 5000)] },
    ],
    defaultMinutes: 10,
  }),
  fourSevenEight: technique({
    id: '478', name: '4-7-8 breathing', purpose: Purpose.SLEEP,
    summary: 'Inhale 4, hold 7, exhale 8. A long-hold, long-exhale pattern for winding down at night.',
    howTo: 'Inhale quietly through the nose for 4, hold for 7, exhale through the mouth for 8. Start with 4 cycles and build up slowly.',
    source: 'Dr. Andrew Weil (popular long-exhale technique; not part of the Stanford trial)',
    variants: [{ name: '4 · 7 · 8', steps: [step(K.INHALE, 4000), step(K.HOLD_FULL, 7000), step(K.EXHALE, 8000)] }],
    defaultMinutes: 3,
  }),
  hyper: technique({
    id: 'cyclic_hyper', name: 'Cyclic hyperventilation', purpose: Purpose.ENERGY,
    summary: 'Rounds of fast, deep breaths followed by an empty-lung hold. Raises adrenaline and alertness (similar to Wim Hof breathing).',
    howTo: 'Deep inhale through the nose, let the air fall out through the mouth. Repeat 25–30 times, exhale fully, and hold with lungs empty for 15–30 s. Repeat the rounds.',
    source: 'Balban et al. 2023 (30 breaths, 15 s hold, rounds); Huberman Lab Daily Blueprint (20–25 breaths, 15–30 s hold)',
    variants: [{ name: '30 breaths · 15 s hold', steps: hyperRoundSteps() }],
    mode: 'rounds', defaultRounds: 3, needsWarning: true,
  }),
};
const T = Techniques;
export const ALL = [T.cyclicSigh, T.box, T.extendedExhale, T.resonance, T.fourSevenEight, T.sighReset, T.hyper];
export const byId = (id) => ALL.find((t) => t.id === id) || T.cyclicSigh;
export const DURATIONS = [1, 3, 5, 10, 15, 20];
export const HYPER_WARNING = {
  title: 'Safety first',
  lead: 'Cyclic hyperventilation can cause light-headedness, tingling, or fainting.',
  points: [
    'Never do it in or near water, while driving, or standing.',
    'Sit or lie down somewhere safe.',
    'Stop at once if you feel dizzy, faint, or unwell.',
    'Not for use if you are pregnant, or have a heart condition, epilepsy or seizures, high blood pressure, or panic disorder.',
  ],
  confirm: 'I have read and understand this',
};

export function cardTiming(t) {
  return t.id === T.hyper.id ? '30 breaths (1.5s in · 1s out) · exhale · hold 15s' : timingText(t.variants[0].steps);
}

/** Session configuration (mirrors SessionConfig). */
export function makeConfig(technique, { variantIndex = 0, minutes = technique.defaultMinutes, rounds = technique.defaultRounds, hyper = HYPER_DEFAULT } = {}) {
  const vi = Math.max(0, Math.min(variantIndex, technique.variants.length - 1));
  const variant = technique.variants[vi];
  const cycleSteps = technique.id === T.hyper.id ? hyperRoundSteps(hyper) : variant.steps;
  return { technique, variantIndex: vi, variant, minutes, rounds, hyper, cycleSteps };
}

/**
 * Whole cycles only. Timed sessions use as many complete cycles as fit in the chosen minutes (at least one),
 * so a session never ends mid-breath. Rounds sessions run exactly the requested number of cycles.
 */
export class Timeline {
  constructor(config) {
    this.config = config;
    const steps = config.cycleSteps;
    this.cycleMs = steps.reduce((a, s) => a + s.ms, 0);
    this.cycles = config.technique.mode === 'timed'
      ? Math.max(1, Math.floor((config.minutes * 60000) / this.cycleMs))
      : Math.max(1, config.rounds);
    this.segments = [];
    let t = 0;
    for (let c = 0; c < this.cycles; c++) {
      steps.forEach((s, i) => { this.segments.push({ step: s, startMs: t, endMs: t + s.ms, cycle: c, indexInCycle: i, cycleSize: steps.length }); t += s.ms; });
    }
    this.totalMs = this.cycles * this.cycleMs;
  }
  indexAt(elapsedMs) {
    if (elapsedMs >= this.totalMs) return this.segments.length - 1;
    let lo = 0, hi = this.segments.length - 1;
    while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (this.segments[mid].startMs <= elapsedMs) lo = mid; else hi = mid - 1; }
    return lo;
  }
}

export const Pacer = {
  MIN: 0.35, MID: 0.8, MAX: 1.0, FAST_LO: 0.5, FAST_HI: 0.9,
  ease(f) { const x = Math.min(1, Math.max(0, f)); return 0.5 - 0.5 * Math.cos(Math.PI * x); },
  scale(kind, fraction, hasTopUp) {
    const e = Pacer.ease(fraction);
    const lerp = (a, b) => a + (b - a) * e;
    switch (kind) {
      case K.INHALE: return lerp(Pacer.MIN, hasTopUp ? Pacer.MID : Pacer.MAX);
      case K.TOP_UP: return lerp(Pacer.MID, Pacer.MAX);
      case K.HOLD_FULL: return Pacer.MAX;
      case K.EXHALE: return lerp(Pacer.MAX, Pacer.MIN);
      case K.HOLD_EMPTY: return Pacer.MIN;
      case K.BREATHE_IN: return lerp(Pacer.FAST_LO, Pacer.FAST_HI);
      case K.BREATHE_OUT: return lerp(Pacer.FAST_HI, Pacer.FAST_LO);
      case K.EXHALE_ALL: return lerp(Pacer.FAST_LO, Pacer.MIN);
      default: return Pacer.MIN;
    }
  },
  /** Box dot in unit square (0,0 top-left): inhale up the left, hold across the top, exhale down the right, hold along the bottom. */
  boxPoint(sideIndex, fraction) {
    const f = Math.min(1, Math.max(0, fraction));
    switch (sideIndex % 4) {
      case 0: return [0, 1 - f];
      case 1: return [f, 0];
      case 2: return [1, f];
      default: return [1 - f, 1];
    }
  },
};

/** Vibration patterns for navigator.vibrate ([on, off, on, ...] ms). Same timings as the Android Haptics object. */
export function hapticPattern(kind) {
  if (kind === K.TOP_UP) return [60, 60, 60];
  if (isInhale(kind)) return kind === K.BREATHE_IN ? [40] : [220];
  if (isExhale(kind)) return kind === K.BREATHE_OUT ? [] : [90, 110, 90];
  return [30];
}

/** Tone cue parameters (Hz, ms) matching ToneSynth. */
export function toneFor(kind, ms) {
  const cap = (max) => Math.max(150, Math.min(ms, max));
  if (kind === K.TOP_UP) return { type: 'glide', f0: 440, f1: 554, ms: cap(1200) };
  if (isInhale(kind)) return { type: 'glide', f0: 220, f1: 440, ms: cap(2500) };
  if (isExhale(kind)) return { type: 'glide', f0: 440, f1: 196, ms: cap(3500) };
  return { type: 'tick' };
}

/** Visual/audio phase family: 'in', 'hold' or 'out'. */
export function phaseGroup(kind) { return isInhale(kind) ? 'in' : isExhale(kind) ? 'out' : 'hold'; }

/**
 * Eyes-closed audio plan for one phase. Inhale glides UP and exhale glides DOWN for the WHOLE phase,
 * so the length of each breath is audible; holds are a soft steady drone; the sigh top-up is a short higher chirp.
 * Without eyes-closed mode the original short, capped cue tones are used (toneFor).
 */
export function audioPlan(kind, ms, eyesClosed = true) {
  if (!eyesClosed) return toneFor(kind, ms);
  const fast = kind === K.BREATHE_IN || kind === K.BREATHE_OUT;
  if (kind === K.TOP_UP) return { type: 'chirp', f0: 587, f1: 880, ms };
  if (isInhale(kind)) return { type: 'inhale', f0: fast ? 262 : 220, f1: fast ? 392 : 440, ms, fast };
  if (isExhale(kind)) return { type: 'exhale', f0: fast ? 349 : 392, f1: fast ? 233 : 196, ms, fast };
  return { type: 'hold', f: kind === K.HOLD_FULL ? 330 : 165, ms };
}

/** Calm spoken phrasing for a step. */
export function spokenFor(step) {
  switch (step.kind) {
    case K.INHALE: return step.label === 'Recovery inhale' ? 'Recovery breath in' : 'Breathe in';
    case K.TOP_UP: return 'And a little more';
    case K.HOLD_FULL: return 'Hold';
    case K.HOLD_EMPTY: return /empty/.test(step.label) ? 'Hold, lungs empty' : 'Hold';
    case K.EXHALE: return step.label === 'Relax' ? 'Relax, breathe out' : step.label === 'Long exhale' ? 'Long breath out' : 'Breathe out';
    case K.EXHALE_ALL: return 'Breathe all the way out';
    case K.BREATHE_IN: return 'In';
    case K.BREATHE_OUT: return 'Out';
    default: return step.label;
  }
}

/** Spoken round announcement for rounds-based sessions (null when there is nothing to say). */
export function roundAnnouncement(cycle, cycles) {
  if (cycles <= 1) return null;
  return cycle === cycles - 1 ? 'Last round' : `Round ${cycle + 1} of ${cycles}`;
}

export const formatClock = (ms) => { const s = Math.floor((ms + 999) / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
export const ceilSeconds = (ms) => Math.floor((ms + 999) / 1000);

export function sessionSummary(tl) {
  const t = tl.config.technique;
  const unit = t.mode === 'rounds' ? 'round' : 'breath';
  return `Session: ${tl.cycles} ${unit}${tl.cycles === 1 ? '' : 's'} · ${formatClock(tl.totalMs)}${t.mode === 'timed' ? ' (whole breaths only)' : ''}`;
}

export const Streaks = {
  /** Consecutive days with any session, ending today or yesterday. */
  current(days, today) {
    const set = days instanceof Set ? days : new Set(days);
    let d = set.has(today) ? today : today - 1;
    let n = 0;
    while (set.has(d)) { n++; d--; }
    return n;
  },
};

export const History = {
  encode: (e) => `${e.epochDay}|${e.techniqueId}|${e.seconds}|${e.completed ? 1 : 0}`,
  decode(s) {
    const p = String(s).split('|');
    if (p.length !== 4) return null;
    const day = Number(p[0]), secs = Number(p[2]);
    if (!Number.isInteger(day) || !Number.isInteger(secs)) return null;
    return { epochDay: day, techniqueId: p[1], seconds: secs, completed: p[3] === '1' };
  },
};

/** Local calendar day number (days since 1970-01-01 in local time), like LocalDate.toEpochDay(). */
export function epochDay(date = new Date()) {
  return Math.round(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
}
export function dayToDate(day) { const d = new Date(day * 86400000); return d.toISOString().slice(0, 10); }

export const LEAD_IN_MS = 3000;

/** Drives a timeline against an injectable monotonic clock (performance.now in the browser). Drift-free: state is derived from clock - anchor. */
export class SessionController {
  constructor({ clock, cues = {}, onComplete = () => {}, onChange = () => {} }) {
    this.clock = clock; this.cues = cues; this.onComplete = onComplete; this.onChange = onChange;
    this.state = { run: 'idle', timeline: null, elapsedMs: 0, leadInMs: 0, index: 0, completed: false };
    this.anchor = 0; this.lastHold = -1; this.halfDone = false;
  }
  _cue(name, ...a) { try { this.cues[name]?.(...a); } catch (e) { /* cues must never break timing */ } }
  _set(patch) { this.state = { ...this.state, ...patch }; this.onChange(this.state); }
  get segment() { return this.state.timeline?.segments[this.state.index] ?? null; }
  get totalMs() { return this.state.timeline?.totalMs ?? 0; }
  get remainingMs() { return Math.max(0, this.totalMs - this.state.elapsedMs); }
  get progress() { return this.totalMs ? Math.min(1, Math.max(0, this.state.elapsedMs / this.totalMs)) : 0; }
  get phaseElapsedMs() { const s = this.segment; return s ? Math.min(s.step.ms, Math.max(0, this.state.elapsedMs - s.startMs)) : 0; }
  get phaseRemainingMs() { const s = this.segment; return s ? s.step.ms - this.phaseElapsedMs : 0; }
  get phaseFraction() { const s = this.segment; return s ? this.phaseElapsedMs / s.step.ms : 0; }
  get isActive() { return ['lead_in', 'running', 'paused'].includes(this.state.run); }

  start(config, leadInMs = LEAD_IN_MS) {
    this._cue('stop');
    const tl = new Timeline(config);
    this.lastHold = -1; this.halfDone = false;
    this.anchor = this.clock() + leadInMs;
    this._set({ run: leadInMs > 0 ? 'lead_in' : 'running', timeline: tl, elapsedMs: 0, leadInMs, index: 0, completed: false });
    this._cue('onStart', tl);
    if (leadInMs > 0) this._cue('onLeadInTick', ceilSeconds(leadInMs)); else this._cue('onSegment', tl.segments[0], 0);
  }
  tick() {
    const s = this.state, tl = s.timeline;
    if (!tl) return;
    if (s.run === 'lead_in') {
      const left = this.anchor - this.clock();
      if (left > 0) {
        const prev = ceilSeconds(s.leadInMs), sec = ceilSeconds(left);
        this._set({ leadInMs: left });
        if (sec !== prev) this._cue('onLeadInTick', sec);
      } else {
        this._set({ run: 'running', leadInMs: 0, elapsedMs: 0, index: 0 });
        this._cue('onSegment', tl.segments[0], 0);
        this._advance();
      }
    } else if (s.run === 'running') this._advance();
  }
  _advance() {
    const s = this.state, tl = s.timeline;
    const elapsed = Math.max(0, this.clock() - this.anchor);
    if (elapsed >= tl.totalMs) { this._finish(true); return; }
    const idx = tl.indexAt(elapsed);
    this._set({ elapsedMs: elapsed, index: idx });
    const seg = tl.segments[idx];
    if (idx !== s.index) this._cue('onSegment', seg, elapsed - seg.startMs);
    if (!this.halfDone && elapsed >= tl.totalMs / 2) { this.halfDone = true; this._cue('onHalfway', tl); }
    if (isHold(seg.step.kind)) {
      const sec = Math.floor((elapsed - seg.startMs) / 1000);
      const key = seg.startMs * 1000 + sec;
      if (sec > 0 && key !== this.lastHold) { this.lastHold = key; this._cue('onHoldSecond', seg, sec); }
    }
  }
  pause() {
    const s = this.state;
    if (s.run !== 'running' && s.run !== 'lead_in') return;
    const patch = { run: 'paused' };
    if (s.run === 'running') patch.elapsedMs = Math.min(s.timeline.totalMs, Math.max(0, this.clock() - this.anchor));
    else { patch.elapsedMs = 0; patch.leadInMs = 0; }
    this._set(patch);
    this._cue('stop');
  }
  resume() {
    const s = this.state;
    if (s.run !== 'paused') return;
    this.anchor = this.clock() - s.elapsedMs;
    this._set({ run: 'running', leadInMs: 0 });
    this._cue('onResume', s.timeline);
    const seg = this.segment; if (seg) this._cue('onSegment', seg, s.elapsedMs - seg.startMs);
  }
  end() { if (this.isActive) this._finish(false); }
  reset() { this._cue('stop'); this._set({ run: 'idle', timeline: null, elapsedMs: 0, leadInMs: 0, index: 0, completed: false }); }
  _finish(completed) {
    const s = this.state;
    const elapsed = completed ? s.timeline.totalMs : s.elapsedMs;
    this._set({ run: 'finished', elapsedMs: elapsed, completed });
    this._cue('stop');
    if (completed) this._cue('onFinished');
    this.onComplete(this.state);
  }
}
