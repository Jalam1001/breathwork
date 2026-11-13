import { ALL, byId, Techniques as T, DURATIONS, HYPER_WARNING, makeConfig, Timeline, timingText, cardTiming, sessionSummary,
  formatClock, ceilSeconds, Pacer, PhaseKind as K, Streaks, epochDay, dayToDate, SessionController, phaseGroup } from './domain.js';
import { Cues } from './cues.js';
import { Store } from './store.js';
import { Aurora, PALETTES } from './bg.js';
import { themeOf, icon } from './theme.js';

const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const app = $('#app');
const cues = new Cues();
cues.settings = Store.cues;
window.__bwCues = cues; // test hook (read-only use in automated checks)
const aurora = new Aurora($('#bg'));
aurora.start();

// ---------- Theme & low-light ----------
let forceDark = false;
const isDark = () => forceDark || Store.theme === 'dark' || (Store.theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
function applyTheme() {
  document.documentElement.dataset.theme = Store.theme;
  document.documentElement.dataset.night = Store.night ? 'on' : 'off';
  const dark = isDark();
  $('meta[name=theme-color]').setAttribute('content', dark ? (Store.night ? '#02040A' : '#060A18') : '#EEF1FB');
  aurora.setMode({ dark, night: Store.night });
}
applyTheme();
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);

// ---------- Session engine ----------
let rafId = 0, intervalId = 0, wakeLock = null;
const controller = new SessionController({
  clock: () => performance.now(),
  cues,
  onComplete: (s) => {
    const secs = Math.floor(s.elapsedMs / 1000);
    if (secs >= 10) Store.add({ epochDay: epochDay(), techniqueId: s.timeline.config.technique.id, seconds: secs, completed: s.completed });
    stopLoops(); releaseWake(); renderSession();
  },
});

async function requestWake() {
  try { if ('wakeLock' in navigator && document.visibilityState === 'visible') { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener?.('release', () => { wakeLock = null; }); } }
  catch (e) { /* not allowed (e.g. low battery); session still works */ }
}
function releaseWake() { try { wakeLock?.release(); } catch (e) { /* ignore */ } wakeLock = null; }
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && controller.isActive && !wakeLock) requestWake();
  if (document.visibilityState === 'visible') controller.tick();
});

function startLoops() {
  stopLoops();
  intervalId = setInterval(() => controller.tick(), 50);
  const frame = () => { controller.tick(); drawSession(); rafId = requestAnimationFrame(frame); };
  rafId = requestAnimationFrame(frame);
}
function stopLoops() { clearInterval(intervalId); cancelAnimationFrame(rafId); intervalId = 0; rafId = 0; }

function startSession(cfg) {
  cues.settings = Store.cues;
  cues.unlock(); // inside the click handler: required for iOS audio and speech
  controller.start(cfg);
  requestWake();
  sessionShell();
  startLoops();
}

// ---------- Router ----------
function setChrome({ palette = PALETTES.home, dark = false } = {}) {
  forceDark = dark; applyTheme(); aurora.setPalette(palette); aurora.setEnergy(0.5);
}
function route() {
  if (controller.isActive || controller.state.run === 'finished') {
    if (controller.state.run === 'running' || controller.state.run === 'lead_in') controller.pause();
    renderSession(); return;
  }
  const h = location.hash.replace(/^#\/?/, '');
  window.scrollTo(0, 0);
  if (h.startsWith('t/')) { const t = byId(h.slice(2)); setChrome({ palette: themeOf(t.id).aurora }); renderDetail(t); }
  else { setChrome(); if (h === 'history') renderHistory(); else if (h === 'about') renderAbout(); else renderHome(); }
  const h1 = $('h1', app); if (h1 && document.activeElement === document.body) h1.focus({ preventScroll: true });
}
window.addEventListener('hashchange', route);

const ICON = {
  back: '<path d="M15.5 4.5 8 12l7.5 7.5"/>',
  theme: '<circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16z" class="fill"/>',
  night: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  history: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M9 3v4M15 3v4"/>',
  about: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/>',
  ear: '<path d="M6 15a7 7 0 1 1 12-5c0 3-3 4-3 7a3 3 0 0 1-6 0"/><path d="M9.5 10a2.5 2.5 0 0 1 5 0"/>',
};
const svgI = (n) => `<svg viewBox="0 0 24 24" aria-hidden="true">${ICON[n]}</svg>`;
function bar(title, { back = false, actions = '', gradient = false } = {}) {
  return `<header class="bar">
    ${back ? `<a class="icon-btn" href="#/" aria-label="Back to techniques">${svgI('back')}</a>` : ''}
    <h1 tabindex="-1" class="${gradient ? 'brand' : ''}">${esc(title)}</h1><div class="actions">${actions}</div></header>`;
}
const purposeChip = (p) => `<span class="chip">${esc(p)}</span>`;

function themeButton() {
  const next = { system: 'dark', dark: 'light', light: 'system' };
  const label = { system: 'Theme: system', dark: 'Theme: dark', light: 'Theme: light' };
  return `<button class="icon-btn" id="themeBtn" type="button" aria-label="${label[Store.theme]}. Change theme" title="${label[Store.theme]}" data-next="${next[Store.theme]}">${svgI('theme')}</button>`;
}
const nightButton = (id = 'nightBtn') => `<button class="icon-btn night-btn" id="${id}" type="button" aria-pressed="${Store.night}" aria-label="Low-light mode" title="Low-light mode">${svgI('night')}</button>`;
function bindChrome() {
  $('#themeBtn')?.addEventListener('click', (e) => { Store.setTheme(e.currentTarget.dataset.next); applyTheme(); route(); });
  $('#nightBtn')?.addEventListener('click', toggleNight);
}
function toggleNight() {
  Store.setNight(!Store.night); applyTheme();
  document.querySelectorAll('.night-btn').forEach((b) => b.setAttribute('aria-pressed', String(Store.night)));
}

// ---------- Home ----------
function stats() {
  const h = Store.history, today = epochDay();
  return { h, streak: Streaks.current(new Set(h.map((e) => e.epochDay)), today), todayMin: Math.floor(h.filter((e) => e.epochDay === today).reduce((a, e) => a + e.seconds, 0) / 60) };
}
const tech = (id) => `data-tech="${esc(id)}"`;
function renderHome() {
  const { streak, todayMin } = stats();
  app.innerHTML = `${bar('BreathWork', { gradient: true, actions: `<a class="lang-link" href="https://jalam1001.github.io/breathwork-hindi/" hreflang="hi" lang="hi" aria-label="हिन्दी संस्करण (Hindi version)">हिन्दी</a>${nightButton()}${themeButton()}
      <a class="icon-btn" href="#/history" aria-label="History">${svgI('history')}</a>
      <a class="icon-btn" href="#/about" aria-label="About and sources">${svgI('about')}</a>` })}
  <main class="content">
    <p class="lead">Breathing as practice. Pick a technique, set a length, then close your eyes and follow the sound.</p>
    <div class="stats"><span class="stat">🔥 Streak: ${streak} day${streak === 1 ? '' : 's'}</span><span class="stat">Today: ${todayMin} min</span></div>
    <ul class="cards" role="list">
      ${ALL.map((t) => `<li><a class="card" href="#/t/${t.id}" data-testid="tech-${t.id}" ${tech(t.id)}>
        <span class="card-head"><span class="card-icon">${icon(themeOf(t.id).icon)}</span><span class="card-title">${esc(t.name)}</span>${purposeChip(t.purpose)}</span>
        <span class="card-sum">${esc(t.summary)}</span>
        <span class="card-timing">${esc(cardTiming(t))}</span></a></li>`).join('')}
    </ul>
    <p class="fine">Techniques discussed on the Huberman Lab podcast and in Balban et al. 2023 (Cell Reports Medicine).
      BreathWork is independent and <strong>not affiliated with or endorsed by Huberman Lab</strong>. Not medical advice.
      <a href="#/about">Sources &amp; about</a> · <a href="https://jalam1001.github.io/breathwork-hindi/" lang="hi" hreflang="hi">हिन्दी में पढ़ें</a></p>
  </main>`;
  bindChrome();
}

// ---------- Detail ----------
const detailState = {};
function renderDetail(t) {
  const st = detailState[t.id] ||= { variant: 0, minutes: t.defaultMinutes, custom: false, customText: '', rounds: t.defaultRounds, breaths: 30, hold: 15 };
  const c = Store.cues, th = themeOf(t.id);
  const isHyper = t.id === T.hyper.id;
  const radios = (name, items, sel, legend) => `<fieldset class="chips"><legend>${legend}</legend>
    ${items.map(([v, label, aria]) => `<label class="chip-opt"><input type="radio" name="${name}" value="${v}" ${String(sel) === String(v) ? 'checked' : ''} ${aria ? `aria-label="${aria}"` : ''}><span>${esc(label)}</span></label>`).join('')}</fieldset>`;
  const toggle = (key, label, hint = '') => `<label class="toggle"><span>${label}${hint ? `<small>${hint}</small>` : ''}</span>
    <input type="checkbox" role="switch" data-cue="${key}" ${c[key] ? 'checked' : ''}><span class="track" aria-hidden="true"></span></label>`;
  const slider = (key, label) => `<label class="slider"><span>${label} <output data-out="${key}">${Math.round(c[key] * 100)}%</output></span>
    <input type="range" min="0" max="100" step="5" value="${Math.round(c[key] * 100)}" data-vol="${key}" aria-label="${label} volume"></label>`;
  app.innerHTML = `${bar(t.name, { back: true, actions: nightButton() })}
  <main class="content detail" ${tech(t.id)}>
    <section class="hero"><span class="card-icon big">${icon(th.icon)}</span>
      <div><p class="row">${purposeChip(t.purpose)}</p><p class="hero-sum">${esc(t.summary)}</p></div></section>
    <section class="panel"><h2>How to</h2><p>${esc(t.howTo)}</p>
      <p class="src">Source: ${esc(t.source)}</p></section>
    <label class="eyes-card" for="eyesSwitch">
      <span class="eyes-icon">${svgI('ear')}</span>
      <span class="eyes-text"><strong>Eyes-closed mode</strong>
        <small>Sound guides every breath: a tone rising for the whole inhale, a warm tone falling for the whole exhale, a soft hum while you hold. Bowl bells at start and end.</small></span>
      <input id="eyesSwitch" type="checkbox" role="switch" data-cue="eyesClosed" ${c.eyesClosed ? 'checked' : ''}><span class="track" aria-hidden="true"></span>
    </label>
    <form id="cfg" class="panel" novalidate>
      ${t.variants.length > 1 ? radios('variant', t.variants.map((v, i) => [i, v.name]), st.variant, 'Pattern') : ''}
      ${!isHyper ? `<p class="timing" id="timing"></p>` : ''}
      ${t.mode === 'timed'
        ? radios('minutes', [...DURATIONS.map((m) => [m, `${m} min`, `${m} minutes`]), ['custom', 'Custom']], st.custom ? 'custom' : st.minutes, 'Duration') +
          `<div class="custom ${st.custom ? '' : 'hidden'}" id="customWrap"><label for="customMin">Minutes (1–120)</label>
           <input id="customMin" inputmode="numeric" pattern="[0-9]*" maxlength="3" value="${esc(st.customText)}" aria-describedby="customErr">
           <span id="customErr" class="err" role="alert"></span></div>`
        : radios('rounds', [1, 2, 3, 4, 5].map((r) => [r, String(r), `${r} ${isHyper ? 'rounds' : 'sighs'}`]), st.rounds, isHyper ? 'Rounds' : 'Sighs') +
          (isHyper ? radios('breaths', [20, 25, 30].map((b) => [b, `${b} breaths`]), st.breaths, 'Breaths per round') +
                     radios('hold', [15, 30].map((h) => [h, `${h} s hold`]), st.hold, 'Empty-lung hold') : '')}
      <p class="summary" id="summary" aria-live="polite"></p>
    </form>
    <section class="panel" id="cuesPanel">
      <h2>Sound &amp; cues</h2>
      ${toggle('tones', 'Tones', 'Short cue tones when eyes-closed mode is off')}
      ${toggle('holdTicks', 'Soft tick each second of a hold')}
      ${toggle('ambient', 'Ambient ocean bed', 'Soft noise that swells as you breathe in and recedes as you breathe out')}
      ${toggle('bells', 'Start and end bowl bell')}
      ${toggle('halfway', 'Halfway chime')}
      ${toggle('voice', 'Spoken cues', cues.canSpeak ? '“Breathe in”, “Hold”, “Breathe out”, rounds, “Session complete”' : 'Not supported in this browser')}
      ${toggle('voiceCounts', 'Spoken counts during holds')}
      ${toggle('haptics', 'Vibration', cues.canVibrate ? '1 long = in, 2 short = out' : 'Not supported on this device/browser (e.g. iPhone)')}
      <h3>Volume</h3>
      ${slider('master', 'Master')}${slider('toneVol', 'Tones & bells')}${slider('ambientVol', 'Ambient')}${slider('voiceVol', 'Voice')}
      <button type="button" class="test-btn" id="testCue">▶ Test sound</button>
    </section>
  </main>
  <div class="start-bar"><button class="primary big" id="startBtn" type="button" ${tech(t.id)}>Start ${esc(t.name)}</button></div>`;
  bindChrome();

  const form = $('#cfg');
  const cfgOf = () => {
    const customMin = parseInt(st.customText, 10);
    const minutes = st.custom ? Math.min(120, Math.max(1, Number.isFinite(customMin) ? customMin : 1)) : st.minutes;
    return makeConfig(t, { variantIndex: st.variant, minutes, rounds: st.rounds, hyper: { breaths: st.breaths, holdSeconds: st.hold } });
  };
  const validCustom = () => { const n = Number(st.customText); return /^\d+$/.test(st.customText) && n >= 1 && n <= 120; };
  const update = () => {
    const cfg = cfgOf();
    if ($('#timing')) $('#timing').textContent = timingText(cfg.variant.steps);
    $('#summary').textContent = sessionSummary(new Timeline(cfg));
    $('#startBtn').disabled = t.mode === 'timed' && st.custom && !validCustom();
    const bad = !!(st.custom && st.customText && !validCustom());
    const err = $('#customErr'); if (err) err.textContent = bad ? 'Choose 1–120 minutes' : '';
    $('#customMin')?.setAttribute('aria-invalid', String(bad));
  };
  const syncCues = () => {
    const s = Store.cues;
    const dis = { tones: s.eyesClosed, holdTicks: !(s.tones || s.eyesClosed), voiceCounts: !s.voice, voice: !cues.canSpeak, haptics: !cues.canVibrate };
    document.querySelectorAll('#cuesPanel [data-cue]').forEach((el) => {
      const d = !!dis[el.dataset.cue]; el.disabled = d; el.closest('label').classList.toggle('disabled', d);
      if (el.dataset.cue === 'tones') el.checked = s.eyesClosed || s.tones;
    });
  };
  form.addEventListener('change', (e) => {
    const el = e.target;
    if (el.name === 'variant') st.variant = +el.value;
    else if (el.name === 'minutes') { st.custom = el.value === 'custom'; if (!st.custom) st.minutes = +el.value; $('#customWrap').classList.toggle('hidden', !st.custom); if (st.custom) $('#customMin').focus(); }
    else if (el.name === 'rounds') st.rounds = +el.value;
    else if (el.name === 'breaths') st.breaths = +el.value;
    else if (el.name === 'hold') st.hold = +el.value;
    update();
  });
  $('main.detail').addEventListener('change', (e) => {
    const el = e.target;
    if (!el.dataset?.cue) return;
    if (el.dataset.cue === 'tones' && Store.cues.eyesClosed) return;
    Store.setCues({ [el.dataset.cue]: el.checked }); cues.settings = Store.cues; syncCues();
  });
  document.querySelectorAll('[data-vol]').forEach((el) => el.addEventListener('input', () => {
    Store.setCues({ [el.dataset.vol]: el.value / 100 }); $(`[data-out=${el.dataset.vol}]`).textContent = `${el.value}%`;
    cues.settings = Store.cues; cues.applyVolumes();
  }));
  $('#customMin')?.addEventListener('input', (e) => { e.target.value = e.target.value.replace(/\D/g, '').slice(0, 3); st.customText = e.target.value; update(); });
  $('#testCue').addEventListener('click', () => { cues.settings = Store.cues; cues.unlock(); cues.applyVolumes(); cues.test(); });
  form.addEventListener('submit', (e) => e.preventDefault());
  $('#startBtn').addEventListener('click', () => {
    if (t.needsWarning && !Store.hyperAck) showWarning(() => startSession(cfgOf()));
    else startSession(cfgOf());
  });
  syncCues(); update();
}

function showWarning(onConfirm) {
  const d = document.createElement('dialog');
  d.className = 'dialog';
  d.setAttribute('aria-labelledby', 'warnTitle');
  d.innerHTML = `<form method="dialog">
    <h2 id="warnTitle">⚠️ ${HYPER_WARNING.title}</h2>
    <p>${HYPER_WARNING.lead}</p>
    <ul>${HYPER_WARNING.points.map((p) => `<li>${esc(p)}</li>`).join('')}</ul>
    <label class="check"><input type="checkbox" id="ackBox"> <span>${HYPER_WARNING.confirm}</span></label>
    <div class="dialog-actions"><button value="cancel" class="text-btn" id="warnCancel">Cancel</button>
    <button value="ok" class="primary" id="warnStart" disabled>Start</button></div></form>`;
  document.body.append(d);
  const box = $('#ackBox', d), ok = $('#warnStart', d);
  box.addEventListener('change', () => { ok.disabled = !box.checked; });
  ok.addEventListener('click', (e) => { e.preventDefault(); if (!box.checked) return; Store.acknowledgeHyper(); d.close(); onConfirm(); });
  d.addEventListener('close', () => d.remove());
  d.showModal();
  box.focus();
}

// ---------- Session ----------
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const R = 120, C = 150;
function sessionShell() {
  const tl = controller.state.timeline, t = tl.config.technique, th = themeOf(t.id);
  const hasTopUp = tl.config.cycleSteps.some((s) => s.kind === K.TOP_UP);
  const square = t.visual === 'square';
  const side = R * 1.55, x0 = C - side / 2;
  setChrome({ palette: th.aurora, dark: true });
  const ec = Store.cues.eyesClosed;
  app.innerHTML = `<main class="session" data-phase="ready" ${tech(t.id)} aria-label="${esc(t.name)} session">
    <div class="s-top"><p class="s-title">${esc(t.name)}</p>
      ${ec ? `<span class="ec-pill" title="Eyes-closed audio guidance is on">${svgI('ear')} Eyes-closed audio</span>` : ''}
      ${nightButton('nightBtnS')}</div>
    <div class="pacer-wrap">
      <svg class="pacer" viewBox="0 0 300 300" role="img" aria-labelledby="phaseLabel">
        <defs>
          <radialGradient id="orbGrad" cx="38%" cy="32%" r="75%"><stop offset="0" class="s1"/><stop offset=".5" class="s2"/><stop offset="1" class="s3"/></radialGradient>
          <radialGradient id="haloGrad"><stop offset=".45" class="h1"/><stop offset="1" class="h2"/></radialGradient>
          <linearGradient id="edgeGrad" x1="0" y1="0" x2="1" y2="1"><stop offset="0" class="e1"/><stop offset="1" class="e2"/></linearGradient>
        </defs>
        <circle id="halo" cx="${C}" cy="${C}" r="${R * 1.5}" fill="url(#haloGrad)"/>
        <g id="ripples"></g>
        ${square ? `
          <rect x="${x0}" y="${x0}" width="${side}" height="${side}" rx="10" class="track" stroke-width="8" fill="none"/>
          <line id="sideHi" class="side-hi" stroke-width="8" stroke-linecap="round"/>
          ${[[0, 1], [0, 0], [1, 0], [1, 1]].map(([a, b]) => `<circle cx="${x0 + a * side}" cy="${x0 + b * side}" r="6" class="corner"/>`).join('')}
          <circle id="breath" cx="${C}" cy="${C}" r="10" fill="url(#orbGrad)" class="orb"/>
          <circle id="dotHalo" r="26" class="dot-halo"/><circle id="dot" r="13" class="dot"/>` : `
          <circle cx="${C}" cy="${C}" r="${R}" class="track" stroke-width="2" fill="none"/>
          ${hasTopUp ? `<circle cx="${C}" cy="${C}" r="${R * Pacer.MID}" class="ring-mid" stroke-width="2" fill="none" stroke-dasharray="4 6"/>` : ''}
          <circle cx="${C}" cy="${C}" r="${R * Pacer.MIN}" class="track faint" stroke-width="2" fill="none"/>
          <circle id="breath" cx="${C}" cy="${C}" r="${R * Pacer.MIN}" fill="url(#orbGrad)" class="orb" stroke="url(#edgeGrad)" stroke-width="3"/>`}
      </svg>
      <div class="phase"><div id="phaseLabel" class="phase-label" aria-live="polite"></div><div id="phaseCount" class="phase-count" aria-hidden="true"></div></div>
    </div>
    <p id="counter" class="counter"></p>
    <div class="progress" role="progressbar" aria-label="Session progress" aria-valuemin="0" aria-valuemax="100" id="prog"><div id="progFill"></div></div>
    <p id="left" class="left"></p>
    <div class="controls">
      <button type="button" class="tonal big" id="pauseBtn">Pause</button>
      <button type="button" class="outlined big" id="endBtn">End</button>
    </div>
  </main>`;
  $('#nightBtnS').addEventListener('click', toggleNight);
  $('#pauseBtn').addEventListener('click', () => {
    if (controller.state.run === 'paused') { cues.unlock(); controller.resume(); requestWake(); } else controller.pause();
    drawSession(true);
  });
  $('#endBtn').addEventListener('click', () => controller.end());
  lastSegKey = '';
  drawSession(true);
}
let lastLabel = '', lastCount = '', lastCounter = '', lastLeft = '', lastPct = -1, lastRun = '', lastSegKey = '', lastPhase = '';
function ripple(r) {
  if (reducedMotion.matches) return;
  const g = $('#ripples'); if (!g) return;
  const c = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  c.setAttribute('cx', C); c.setAttribute('cy', C); c.setAttribute('r', r); c.setAttribute('class', 'ripple');
  c.addEventListener('animationend', () => c.remove());
  g.append(c);
  while (g.childNodes.length > 4) g.firstChild.remove();
}
function drawSession(force = false) {
  const s = controller.state, tl = s.timeline;
  if (!tl || s.run === 'finished' || s.run === 'idle' || !$('.pacer')) return;
  const t = tl.config.technique, seg = controller.segment, lead = s.run === 'lead_in';
  const hasTopUp = tl.config.cycleSteps.some((x) => x.kind === K.TOP_UP);
  const label = lead ? 'Get ready' : s.run === 'paused' ? `Paused · ${seg?.step.label ?? ''}` : seg?.step.label ?? '';
  const count = String(lead ? ceilSeconds(s.leadInMs) : ceilSeconds(controller.phaseRemainingMs));
  const kind = lead ? K.HOLD_EMPTY : seg?.step.kind ?? K.HOLD_EMPTY;
  const frac = lead ? 0 : controller.phaseFraction;
  const phase = lead ? 'ready' : phaseGroup(kind);
  let orbR;
  if (t.visual === 'square') {
    const side = R * 1.55, x0 = C - side / 2, idx = lead ? 0 : seg?.indexInCycle ?? 0;
    const [a, b] = Pacer.boxPoint(idx, 0), [c, d] = Pacer.boxPoint(idx, 1), [px, py] = Pacer.boxPoint(idx, frac);
    const hi = $('#sideHi');
    hi.setAttribute('x1', x0 + a * side); hi.setAttribute('y1', x0 + b * side); hi.setAttribute('x2', x0 + c * side); hi.setAttribute('y2', x0 + d * side);
    for (const id of ['#dot', '#dotHalo']) { const el = $(id); el.setAttribute('cx', x0 + px * side); el.setAttribute('cy', x0 + py * side); }
    const sc = lead ? Pacer.MIN : Pacer.scale(kind, frac, false);
    orbR = (side / 2.3) * sc; aurora.setEnergy(sc);
  } else {
    const sc = Pacer.scale(kind, frac, hasTopUp);
    orbR = R * sc; aurora.setEnergy(sc);
  }
  $('#breath').setAttribute('r', orbR.toFixed(2));
  $('#halo').setAttribute('r', (orbR * 1.55 + 10).toFixed(2));
  const segKey = lead ? 'lead' : `${s.index}`;
  if (segKey !== lastSegKey) { if (lastSegKey && !lead && s.run === 'running') ripple(orbR); lastSegKey = segKey; }
  if (force || phase !== lastPhase) {
    $('.session').dataset.phase = phase; lastPhase = phase;
    aurora.setPalette(phase === 'ready' ? themeOf(t.id).aurora : PALETTES[phase]);
  }
  if (force || label !== lastLabel) { $('#phaseLabel').textContent = label; lastLabel = label; }
  if (force || count !== lastCount) { $('#phaseCount').textContent = count; lastCount = count; }
  let counter = '';
  if (seg && !lead) {
    if (t.id === T.hyper.id) { const breath = Math.floor(seg.indexInCycle / 2) + 1, n = tl.config.hyper.breaths;
      counter = breath <= n ? `Round ${seg.cycle + 1} of ${tl.cycles} · breath ${breath} of ${n}` : `Round ${seg.cycle + 1} of ${tl.cycles}`; }
    else counter = `${t.mode === 'rounds' ? 'Sigh' : 'Breath'} ${seg.cycle + 1} of ${tl.cycles}`;
  }
  if (force || counter !== lastCounter) { $('#counter').textContent = counter; lastCounter = counter; }
  const left = `${formatClock(controller.remainingMs)} left`;
  if (force || left !== lastLeft) { $('#left').textContent = left; lastLeft = left; }
  const pct = Math.floor(controller.progress * 100);
  $('#progFill').style.transform = `scaleX(${controller.progress})`;
  if (force || pct !== lastPct) { $('#prog').setAttribute('aria-valuenow', pct); $('#prog').setAttribute('aria-valuetext', `${pct} percent`); lastPct = pct; }
  if (force || s.run !== lastRun) {
    const pb = $('#pauseBtn');
    pb.textContent = s.run === 'paused' ? 'Resume' : 'Pause';
    pb.className = s.run === 'paused' ? 'primary big' : 'tonal big';
    $('.session').classList.toggle('paused', s.run === 'paused');
    lastRun = s.run;
  }
}
function renderSession() {
  const s = controller.state;
  if (s.run !== 'finished') { if (!$('.pacer')) sessionShell(); drawSession(true); return; }
  const t = s.timeline.config.technique;
  setChrome({ palette: themeOf(t.id).aurora, dark: true });
  app.innerHTML = `<main class="session done" ${tech(t.id)}>
    <span class="card-icon big">${icon(themeOf(t.id).icon)}</span>
    <p class="s-title">${esc(t.name)}</p>
    <h1 tabindex="-1">${s.completed ? 'Session complete' : 'Session ended'}</h1>
    <p class="lead">${formatClock(s.elapsedMs)} of breathing</p>
    <button type="button" class="primary big wide" id="doneBtn">Done</button></main>`;
  $('h1', app).focus();
  $('#doneBtn').addEventListener('click', () => { controller.reset(); const target = `#/t/${t.id}`; if (location.hash !== target) location.hash = target; else route(); });
}
document.addEventListener('keydown', (e) => {
  if (!controller.isActive || e.target.closest('dialog')) return;
  if (e.key === ' ' && !(e.target instanceof HTMLButtonElement)) { e.preventDefault(); $('#pauseBtn')?.click(); }
  if (e.key === 'Escape') { e.preventDefault(); if (controller.state.run !== 'paused') controller.pause(); drawSession(true); }
});

// ---------- History ----------
function renderHistory() {
  const { h, streak } = stats();
  app.innerHTML = `${bar('History', { back: true, actions: h.length ? '<button class="text-btn" id="clearBtn" type="button">Clear</button>' : '' })}
  <main class="content"><section class="panel">
    <p class="stat big">Current streak: ${streak} day${streak === 1 ? '' : 's'}</p>
    <p>Sessions: ${h.length} · Total: ${Math.floor(h.reduce((a, e) => a + e.seconds, 0) / 60)} min</p>
    <p class="src">Stored only on this device (browser storage).</p>
    ${h.length ? `<ul class="hist" role="list">${h.slice().reverse().map((e) => `<li ${tech(e.techniqueId)}><span class="card-icon sm">${icon(themeOf(e.techniqueId).icon)}</span><span><strong>${esc(byId(e.techniqueId).name)}</strong>
      <span>${dayToDate(e.epochDay)} · ${formatClock(e.seconds * 1000)}${e.completed ? '' : ' (ended early)'}</span></span></li>`).join('')}</ul>` : '<p>No sessions yet.</p>'}
  </section></main>`;
  $('#clearBtn')?.addEventListener('click', () => { if (confirm('Clear all history? This cannot be undone.')) { Store.clearHistory(); renderHistory(); } });
}

// ---------- About ----------
function renderAbout() {
  const row = (n, name, purpose, timing, sources) => `<tr><td>${n}</td><th scope="row">${name}</th><td>${purpose}</td><td>${timing}</td><td>${sources}</td></tr>`;
  const a = (href, text) => `<a href="${href}" rel="noopener" target="_blank">${text}</a>`;
  app.innerHTML = `${bar('About & sources', { back: true })}
  <main class="content about"><section class="panel">
    <p>These are breathing techniques <strong>discussed on the Huberman Lab podcast</strong> and in related published research.
    BreathWork is an independent app. <strong>Huberman Lab and Stanford are not affiliated with it and do not endorse it.</strong> Nothing here is medical advice.</p>
    <h2>Primary sources</h2>
    <ul class="sources">
      <li>Balban MY, Neri E, Kogon MM, … Spiegel D, Huberman AD. <em>Brief structured respiration practices enhance mood and reduce physiological arousal.</em> Cell Reports Medicine 4(1):100895, 2023. ${a('https://pmc.ncbi.nlm.nih.gov/articles/PMC9873947/', 'PMC9873947')} (NCT05304000). A 1-month remote RCT of daily 5-minute practices. Cyclic sighing produced the largest improvement in positive affect and the largest drop in respiratory rate, and beat mindfulness meditation on mood (p &lt; 0.05).</li>
      <li>${a('https://med.stanford.edu/news/insights/2023/02/cyclic-sighing-can-help-breathe-away-anxiety.html', 'Stanford Medicine summary')}</li>
      <li>${a('https://www.hubermanlab.com/newsletter/breathwork-protocols-for-health-focus-stress', 'Huberman Lab newsletter: "Breathwork Protocols for Health, Focus &amp; Stress"')}</li>
      <li>${a('https://www.hubermanlab.com/daily-blueprint', 'Huberman Lab Daily Blueprint')} (cyclic hyperventilation and physiological sigh, with safety notes)</li>
      <li>${a('https://www.hubermanlab.com/episode/tools-for-managing-stress-and-anxiety', 'Episode: "Tools for Managing Stress &amp; Anxiety"')} (physiological sigh; longer exhales slow the heart rate)</li>
      <li>${a('https://www.hubermanlab.com/episode/dr-jack-feldman-breathing-for-mental-physical-health-and-performance', 'Episode: "Dr. Jack Feldman: Breathing for Mental &amp; Physical Health &amp; Performance"')}</li>
      <li>${a('https://www.hubermanlab.com/episode/how-to-breathe-correctly-for-optimal-health-mood-learning-and-performance', 'Episode: "How to Breathe Correctly for Optimal Health, Mood, Learning &amp; Performance"')} (box breathing, CO₂ tolerance test)</li>
      <li>${a('https://pmc.ncbi.nlm.nih.gov/articles/PMC8924557/', 'Resonance breathing and HRV (PMC8924557)')}: 6–6.5 breaths/min optimal in that cohort; cites Lin et al. for 5.5 breaths/min.</li>
    </ul>
    <h2>Techniques and timing</h2>
    <div class="table-wrap" tabindex="0" role="region" aria-label="Techniques table"><table>
      <thead><tr><th scope="col">#</th><th scope="col">Technique</th><th scope="col">Purpose</th><th scope="col">App timing</th><th scope="col">What the sources say</th></tr></thead>
      <tbody>
      ${row(1, 'Cyclic sighing', 'Calm', 'Inhale 3 s, top-up 1 s, exhale 6 s (10 s cycle, 6/min). Slower: 3.5 / 1.5 / 7 s. Default 5 min', 'Double inhale (nose), long full exhale (mouth), 5 min (Balban 2023). Sources give no seconds. <strong>The seconds are the app\'s own choice.</strong>')}
      ${row(2, 'Physiological sigh (quick reset)', 'Calm', '1–5 sighs (default 3) at the same timing', '"1–3 physiological sighs" to calm down in real time')}
      ${row(3, 'Box breathing', 'Focus', '4 s each side (also 3, 5, 8 s)', 'Equal sides; length from the CO₂ tolerance test: &lt;20 s → 3–4 s, 25–45 s → 5–6 s, 50–75 s+ → 8–10 s')}
      ${row(4, 'Extended exhale', 'Calm', 'Inhale 4 s, exhale 8 s (also 3/6)', 'Longer exhales slow the heart rate. The 1:2 ratio is the app\'s choice.')}
      ${row(5, 'Resonance / coherent', 'Calm', '5.5 s in, 5.5 s out (≈5.45/min); also 5/5 (6/min)', '≈5.5–6 breaths/min gives the largest HRV oscillation. Not part of the Stanford trial.')}
      ${row(6, '4-7-8 breathing', 'Sleep', 'Inhale 4, hold 7, exhale 8', 'Dr. Andrew Weil\'s technique. <strong>Not</strong> from the Stanford trial or a Huberman protocol.')}
      ${row(7, 'Cyclic hyperventilation', 'Energy', 'Per round: 20/25/30 × (1.5 s in, 1 s out), exhale fully 3 s, hold empty 15 or 30 s, recovery inhale 4 s, relax 4 s. 1–5 rounds', 'Balban 2023: 30 breaths, 15 s hold, 3 rounds. Huberman: 20–25 breaths, 15–30 s hold. Never near water; sit or lie down; caution with panic or anxiety.')}
      </tbody></table></div>
    <p>Timed sessions use <strong>whole breath cycles only</strong>: the real length is floor(minutes × 60 ÷ cycle) × cycle, at least one cycle, and is shown before you start. Example: 5 min of 4-second box breathing is 18 cycles, or 4:48.</p>
    <h2>Eyes-closed audio guide</h2>
    <p>Rising tone for the whole inhale · short bright chirp for the sigh's second sip · warm falling tone for the whole exhale · soft pulsing hum (with quiet ticks) while holding · soft chime at each corner of the box · singing-bowl bell at start and end · optional halfway chime, ocean bed and spoken cues.</p>
    <p>Full notes: ${a('https://github.com/Jalam1001/breathwork/blob/main/RESEARCH.md', 'RESEARCH.md on GitHub')} · ${a('https://github.com/Jalam1001/breathwork', 'Source code')} · <a href="https://jalam1001.github.io/breathwork-hindi/" lang="hi" hreflang="hi">हिन्दी संस्करण</a> (with a Hindi essay, ${a('https://jalam1001.github.io/breathwork-hindi/essay.html', '“साँस की पुनर्खोज”')})</p>
    <h2>Privacy</h2>
    <p>No accounts, ads, analytics, or trackers. Settings and history stay in this browser's local storage. After the first visit the app works offline.</p>
    <h2>Install on your phone</h2>
    <p><strong>Android (Chrome):</strong> menu ⋮ → <em>Add to Home screen</em> / <em>Install app</em>. <strong>iPhone (Safari):</strong> Share → <em>Add to Home Screen</em>. Note: iPhones don't support web vibration; use the eyes-closed tones or voice there. On iPhone, turn off the silent switch to hear tones.</p>
  </section></main>`;
}

// ---------- Boot ----------
route();
if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  const hadController = !!navigator.serviceWorker.controller;
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch((e) => console.warn('SW registration failed', e)));
  // When an update activates, reload once to pick up the new version (never in the middle of a session).
  navigator.serviceWorker.addEventListener('controllerchange', () => { if (hadController && !controller.isActive) location.reload(); });
}
