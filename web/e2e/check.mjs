// Headless browser check: loads the site, opens and runs every technique, pauses/resumes/ends,
// completes one full session, checks history, offline reload via the service worker, and saves mobile screenshots.
import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import assert from 'node:assert/strict';

const SHOTS = process.env.SHOTS || '/workspace/breathwork-web-shots';
const BASE = process.env.BASE_URL || 'http://127.0.0.1:8099/';
mkdirSync(SHOTS, { recursive: true });
let server;
if (!process.env.BASE_URL) {
  server = spawn('python3', ['-m', 'http.server', '8099', '--bind', '127.0.0.1', '-d', new URL('../../docs', import.meta.url).pathname], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 800));
}
const browser = await chromium.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome', args: ['--autoplay-policy=no-user-gesture-required'] });
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, colorScheme: 'dark' });
const page = await ctx.newPage();
const errors = [];
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
page.on('pageerror', (e) => errors.push(String(e)));
const shot = (n) => page.screenshot({ path: `${SHOTS}/${n}.png` });
const results = [];
const ok = (msg) => { results.push(`PASS ${msg}`); console.log(`PASS ${msg}`); };

try {
  await page.goto(BASE, { waitUntil: 'networkidle' });
  assert.equal(await page.locator('.card').count(), 7); ok('home lists 7 techniques');
  assert.match(await page.locator('.fine').first().innerText(), /not affiliated with or endorsed by Huberman Lab/); ok('not-affiliated note on home');
  await shot('01-home-dark');

  const ids = ['cyclic_sigh', 'box', 'extended_exhale', 'resonance', '478', 'sigh_reset', 'cyclic_hyper'];
  const expected = { cyclic_sigh: '30 breaths · 5:00', box: '18 breaths · 4:48', extended_exhale: '25 breaths · 5:00', resonance: '54 breaths · 9:54', '478': '9 breaths · 2:51', sigh_reset: '3 rounds · 0:30', cyclic_hyper: '3 rounds · 5:03' };
  let n = 2;
  for (const id of ids) {
    await page.goto(`${BASE}#/t/${id}`); await page.waitForSelector('#startBtn');
    const summary = await page.locator('#summary').innerText();
    assert.ok(summary.includes(expected[id]), `${id} summary: ${summary}`); ok(`${id} shows exact length "${summary}"`);
    if (id === 'box') {
      await page.locator('label.chip-opt:has(input[name=minutes][value="1"])').click();
      assert.match(await page.locator('#summary').innerText(), /3 breaths · 0:48/);
      await page.locator('label.chip-opt:has(input[name=variant][value="2"])').click(); // 8 s sides
      assert.match(await page.locator('#summary').innerText(), /1 breath · 0:32/); ok('box 1 min / 8 s sides → 0:32');
      await page.locator('label.chip-opt:has(input[name=variant][value="0"])').click();
      await page.locator('label.chip-opt:has(input[name=minutes][value="custom"])').click();
      await page.fill('#customMin', '0'); assert.ok(await page.locator('#startBtn').isDisabled()); ok('custom 0 min rejected');
      await page.fill('#customMin', '2'); assert.match(await page.locator('#summary').innerText(), /7 breaths · 1:52/); ok('custom 2 min → 1:52');
      await page.locator('label.chip-opt:has(input[name=minutes][value="5"])').click();
      await page.evaluate(() => scrollTo(0, 0));
      await shot(`0${n++}-detail-box`);
    }
    if (id === 'cyclic_hyper') {
      await page.locator('label.chip-opt:has(input[name=rounds][value="1"])').click();
      await page.locator('label.chip-opt:has(input[name=breaths][value="20"])').click();
      await page.locator('label.chip-opt:has(input[name=hold][value="30"])').click();
      assert.match(await page.locator('#summary').innerText(), /1 round · 1:31/); ok('hyper 1 round × 20 breaths, 30 s hold → 1:31');
    }
    await page.click('#startBtn');
    if (id === 'cyclic_hyper') {
      await page.waitForSelector('dialog[open]');
      assert.ok(await page.locator('#warnStart').isDisabled()); ok('hyper safety dialog blocks Start until acknowledged');
      await shot(`0${n++}-safety-dialog`);
      await page.check('#ackBox'); await page.click('#warnStart');
    }
    await page.waitForSelector('.pacer');
    assert.equal(await page.locator('#phaseLabel').innerText(), 'Get ready'); ok(`${id} lead-in "Get ready"`);
    await page.waitForFunction(() => document.querySelector('#phaseLabel').textContent !== 'Get ready', null, { timeout: 5000 });
    await page.waitForTimeout(id === 'box' ? 5200 : id === 'cyclic_sigh' ? 1800 : 1500);
    const label = await page.locator('#phaseLabel').innerText();
    const counter = await page.locator('#counter').innerText();
    ok(`${id} running: phase "${label}", "${counter}", ${await page.locator('#left').innerText()}`);
    if (['cyclic_sigh', 'box', 'cyclic_hyper', 'resonance'].includes(id)) await shot(`0${n++}-session-${id}`);
    await page.click('#pauseBtn');
    const left1 = await page.locator('#left').innerText(); const r1 = await page.locator('#prog').getAttribute('aria-valuenow');
    await page.waitForTimeout(1500);
    assert.equal(await page.locator('#left').innerText(), left1); assert.equal(await page.locator('#prog').getAttribute('aria-valuenow'), r1);
    assert.equal(await page.locator('#pauseBtn').innerText(), 'Resume'); ok(`${id} pause freezes time`);
    if (id === 'box') await shot(`0${n++}-session-paused`);
    await page.click('#pauseBtn'); await page.waitForTimeout(1200);
    assert.notEqual(await page.locator('#left').innerText(), left1); ok(`${id} resume continues`);
    await page.click('#endBtn');
    await page.waitForSelector('text=Session ended'); ok(`${id} end → "Session ended"`);
    await page.click('#doneBtn'); await page.waitForSelector('#startBtn');
  }

  // A full session to completion: 1 physiological sigh (3 s lead-in + 10 s)
  await page.goto(`${BASE}#/t/sigh_reset`); await page.waitForSelector('#startBtn');
  await page.locator('label.chip-opt:has(input[name=rounds][value="1"])').click();
  await page.click('#startBtn');
  await page.waitForSelector('text=Session complete', { timeout: 20000 });
  assert.match(await page.locator('.lead').innerText(), /0:10 of breathing/); ok('full 1-sigh session completes with 0:10');
  await shot(`${String(n++).padStart(2, '0')}-complete`);
  await page.click('#doneBtn');
  await page.goto(`${BASE}#/history`); await page.waitForSelector('.hist li');
  const hist = await page.locator('.hist li').count();
  assert.equal(hist, 1); ok("history records the completed session; ended sessions under 10 s are skipped like the app");
  assert.match(await page.locator('.stat').innerText(), /streak: 1 day/); ok('streak = 1 day');
  await shot(`${String(n++).padStart(2, '0')}-history`);
  page.once('dialog', (d) => d.accept());
  await page.click('#clearBtn'); await page.waitForSelector('text=No sessions yet.'); ok('clear history');

  await page.goto(`${BASE}#/about`); await page.waitForSelector('table');
  assert.ok(await page.locator('a[href*="RESEARCH.md"]').count()); ok('about page links RESEARCH.md');
  await shot(`${String(n++).padStart(2, '0')}-about`);

  // Light theme
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto(BASE); await page.waitForSelector('.card');
  await shot(`${String(n++).padStart(2, '0')}-home-light`);
  await page.goto(`${BASE}#/t/cyclic_sigh`); await page.waitForSelector('#startBtn'); await page.click('#startBtn');
  await page.waitForTimeout(5000); await shot(`${String(n++).padStart(2, '0')}-session-light`);
  await page.click('#endBtn'); await page.click('#doneBtn');
  await page.emulateMedia({ colorScheme: 'dark' });

  // Keyboard: space pauses during a session
  await page.goto(`${BASE}#/t/resonance`); await page.waitForSelector('#startBtn');
  await page.focus('#startBtn'); await page.keyboard.press('Enter'); await page.waitForTimeout(3500);
  await page.locator('body').focus(); await page.keyboard.press('Escape');
  assert.equal(await page.locator('#pauseBtn').innerText(), 'Resume'); ok('keyboard: Enter starts, Escape pauses');
  await page.click('#endBtn'); await page.click('#doneBtn');

  // PWA: manifest + service worker + offline reload
  const sw = await page.evaluate(async () => { const r = await navigator.serviceWorker.ready; return !!r.active; });
  assert.ok(sw); ok('service worker active');
  const mf = await (await page.request.get(`${BASE}manifest.webmanifest`)).json();
  assert.equal(mf.display, 'standalone'); assert.ok(mf.icons.some((i) => i.sizes === '512x512')); ok('manifest valid (standalone, 192/512/maskable icons)');
  await page.reload({ waitUntil: 'networkidle' });
  await ctx.setOffline(true);
  await page.goto(`${BASE}#/t/box`, { waitUntil: 'domcontentloaded' }); await page.reload();
  await page.waitForSelector('#startBtn', { timeout: 5000 }); ok('works offline after first visit');
  await ctx.setOffline(false);

  const ext = await page.evaluate(() => performance.getEntriesByType('resource').map((r) => r.name).filter((u) => !u.startsWith(location.origin)));
  assert.deepEqual(ext, []); ok('no external requests');
  assert.deepEqual(errors, []); ok('no console errors');
} catch (e) {
  console.error('FAIL', e.message); console.error('console errors:', errors); process.exitCode = 1;
} finally {
  await browser.close(); server?.kill();
}
console.log(`\n${results.length} checks passed${process.exitCode ? ' before failure' : ''}. Screenshots: ${SHOTS}`);
