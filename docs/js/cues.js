// Audio, voice, and vibration cues. Tones are generated with the Web Audio API (no audio files).
import { PhaseKind as K, isInhale, isExhale, toneFor, hapticPattern } from './domain.js';

export class Cues {
  constructor() {
    this.settings = { tones: true, holdTicks: true, voice: false, haptics: true, volume: 0.7 };
    this.ctx = null; this.master = null; this.live = new Set();
    this.canVibrate = typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function';
    this.canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window;
    this.voice = null;
  }

  /** Must be called from a user gesture (tap/click) so iOS and Chrome allow audio and speech. */
  unlock() {
    try {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (AC && !this.ctx) { this.ctx = new AC(); this.master = this.ctx.createGain(); this.master.connect(this.ctx.destination); }
      if (this.ctx && this.ctx.state !== 'running') this.ctx.resume();
      if (this.ctx) { // play a silent buffer to fully unlock iOS
        const b = this.ctx.createBuffer(1, 1, 22050), s = this.ctx.createBufferSource();
        s.buffer = b; s.connect(this.master); s.start(0);
      }
    } catch (e) { console.warn('Audio unavailable', e); }
    if (this.canSpeak && this.settings.voice) {
      try {
        const u = new SpeechSynthesisUtterance(' '); u.volume = 0; speechSynthesis.speak(u);
        this._pickVoice();
      } catch (e) { /* ignore */ }
    }
  }
  _pickVoice() {
    const vs = speechSynthesis.getVoices();
    const lang = (navigator.language || 'en').toLowerCase();
    this.voice = vs.find((v) => v.localService && v.lang.toLowerCase() === lang) || vs.find((v) => v.lang.toLowerCase().startsWith(lang.slice(0, 2))) || null;
  }

  _env(t0, dur, fade) {
    const g = this.ctx.createGain();
    const peak = 0.35 * this.settings.volume;
    g.gain.setValueAtTime(0, t0);
    g.gain.linearRampToValueAtTime(peak, t0 + fade);
    g.gain.setValueAtTime(peak, Math.max(t0 + fade, t0 + dur - fade));
    g.gain.linearRampToValueAtTime(0, t0 + dur);
    g.connect(this.master);
    return g;
  }
  _track(node, until) { this.live.add(node); node.onended = () => this.live.delete(node); node.stop(until); }

  glide(f0, f1, ms) {
    if (!this.ctx || this.settings.volume <= 0) return;
    this.stopTones();
    const t0 = this.ctx.currentTime + 0.01, dur = ms / 1000, fade = Math.min(0.12, dur / 2);
    const o = this.ctx.createOscillator(); o.type = 'sine';
    o.frequency.setValueAtTime(f0, t0); o.frequency.linearRampToValueAtTime(f1, t0 + dur);
    o.connect(this._env(t0, dur, fade)); o.start(t0); this._track(o, t0 + dur + 0.02);
  }
  tick() {
    if (!this.ctx || this.settings.volume <= 0) return;
    const t0 = this.ctx.currentTime + 0.005;
    const o = this.ctx.createOscillator(), g = this.ctx.createGain();
    o.frequency.value = 1200;
    g.gain.setValueAtTime(0.25 * this.settings.volume, t0); g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.06);
    o.connect(g); g.connect(this.master); o.start(t0); this._track(o, t0 + 0.07);
  }
  chime(freq = 660) {
    if (!this.ctx || this.settings.volume <= 0) return;
    const t0 = this.ctx.currentTime + 0.01;
    [[freq, 1], [freq * 2.01, 0.4]].forEach(([f, a]) => {
      const o = this.ctx.createOscillator(), g = this.ctx.createGain();
      o.frequency.value = f;
      g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(0.3 * a / 1.4 * this.settings.volume, t0 + 0.01);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.9);
      o.connect(g); g.connect(this.master); o.start(t0); this._track(o, t0 + 0.92);
    });
  }
  speak(text) {
    if (!this.canSpeak) return;
    try {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.volume = this.settings.volume; u.rate = 0.95;
      if (this.voice) u.voice = this.voice;
      speechSynthesis.speak(u);
    } catch (e) { /* ignore */ }
  }
  vibrate(p) {
    if (!this.canVibrate || !p.length) return;
    try { navigator.vibrate(p); } catch (e) { /* ignore */ }
  }

  // ---- Controller hooks ----
  onSegment(seg) {
    const s = this.settings, k = seg.step.kind;
    const fast = k === K.BREATHE_IN || k === K.BREATHE_OUT;
    if (s.tones) {
      const t = toneFor(k, seg.step.ms);
      if (t.type === 'glide') this.glide(t.f0, t.f1, t.ms); else this.tick();
    }
    if (s.voice) {
      if (!fast) this.speak(seg.step.label);
      else if (k === K.BREATHE_IN && seg.indexInCycle % 10 === 0) this.speak(String(seg.indexInCycle / 2 + 1));
    }
    if (s.haptics) this.vibrate(hapticPattern(k));
  }
  onHoldSecond() { if (this.settings.tones && this.settings.holdTicks) this.tick(); }
  onLeadInTick(sec) { if (this.settings.tones) this.tick(); if (this.settings.voice && sec === 3) this.speak('Get ready'); }
  onFinished() {
    if (this.settings.tones) this.chime();
    if (this.settings.voice) this.speak('Session complete');
    if (this.settings.haptics) this.vibrate([300]);
  }
  stopTones() { for (const n of this.live) { try { n.stop(); } catch (e) { /* already stopped */ } } this.live.clear(); }
  stop() {
    this.stopTones();
    if (this.canSpeak) try { speechSynthesis.cancel(); } catch (e) { /* ignore */ }
    if (this.canVibrate) try { navigator.vibrate(0); } catch (e) { /* ignore */ }
  }
}
export { isInhale, isExhale };
