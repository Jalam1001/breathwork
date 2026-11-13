// Settings and history in localStorage. Nothing leaves the device.
import { History } from './domain.js';

const KEY = 'breathwork.v1';
const DEFAULTS = { cues: { tones: true, holdTicks: true, voice: false, haptics: true, volume: 0.7 }, hyperAck: false, theme: 'system', history: [] };

function load() {
  try { const raw = JSON.parse(localStorage.getItem(KEY) || '{}'); return { ...DEFAULTS, ...raw, cues: { ...DEFAULTS.cues, ...(raw.cues || {}) } }; }
  catch (e) { return structuredClone(DEFAULTS); }
}
let data = load();
function save() { try { localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) { console.warn('Could not save', e); } }

export const Store = {
  get cues() { return { ...data.cues }; },
  setCues(c) { data.cues = { ...data.cues, ...c }; save(); },
  get hyperAck() { return !!data.hyperAck; },
  acknowledgeHyper() { data.hyperAck = true; save(); },
  get theme() { return data.theme; },
  setTheme(t) { data.theme = t; save(); },
  get history() { return (data.history || []).map(History.decode).filter(Boolean); },
  add(e) { data.history = [...(data.history || []), History.encode(e)].slice(-1000); save(); },
  clearHistory() { data.history = []; save(); },
};
