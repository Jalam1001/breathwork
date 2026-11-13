# BreathWork 1.0

**Use it in your browser, no install or Play Store needed: https://jalam1001.github.io/breathwork/**

**हिन्दी संस्करण (Hindi version):** https://jalam1001.github.io/breathwork-hindi/ · source: https://github.com/Jalam1001/breathwork-hindi

## Web app (PWA)
The web version has the same seven techniques, timings, safety warning and session flow as the Android app. It is a static site in [`docs/`](docs/) (plain HTML, CSS and JavaScript, no build step, no backend, no trackers, no external CDNs) served by GitHub Pages.

- Pick a technique, pattern, and duration (1, 3, 5, 10, 15, 20 min or custom 1–120) or rounds; the exact whole-breath length is shown before you start.
- 3-second lead-in, animated pacer (growing/shrinking circle, two-stage ring for sighs, dot tracing a square for box breathing), phase label and countdown, breath/round counter, time left, progress bar, Pause/Resume/End (Space and Esc work too).
- **Eyes-closed mode (on by default):** every phase is clear by ear. The inhale is a tone gliding up for the whole inhale, the exhale a warmer tone gliding down for the whole exhale, holds a soft pulsing hum with quiet ticks, the sigh's second sip a short bright chirp, and each side of the box a soft chime. A synthesized singing-bowl bell marks start and end, with an optional halfway chime. Everything is generated with Web Audio, with smooth fades and no clicks.
- Optional ambient "ocean" bed (filtered brown noise that swells as you breathe in and recedes as you breathe out), calm spoken cues ("Breathe in", "Hold", "Breathe out", round and "Last round" announcements, "Session complete") with optional counts during holds, and vibration (Android browsers only; iPhones don't support web vibration). Master, tones, ambient and voice volumes are saved.
- **Colourful, high-contrast design:** an animated aurora background that shifts colour with the breath (teal/cyan breathing in, violet holding, coral/amber breathing out), floating particles, a glowing gradient orb with ripples on each phase change, and a vivid colour theme per technique. A low-light mode dims everything while keeping colours visible. With "reduce motion" turned on, the background stays still.
- Keeps the screen awake during a session (Screen Wake Lock API). Timing is drift-free, based on `performance.now()`.
- History and daily streak stored only in your browser (localStorage), with a Clear button. Works offline after the first visit.

### Install it like an app
- **Android (Chrome):** open the link, tap ⋮ → **Install app** (or **Add to Home screen**).
- **iPhone/iPad (Safari):** open the link, tap **Share** → **Add to Home Screen**.
- **Desktop (Chrome/Edge):** click the install icon in the address bar.

It then opens full-screen from your home screen and works without internet.

### Limits compared with the Android app
- Browsers pause timers when the phone is locked or the tab is in the background, so keep the screen on (the app requests a wake lock) for cues to keep playing. There is no lock-screen notification.
- Vibration is not available on iPhone; spoken cues depend on the voices your browser has.

### Develop and test the web app
```bash
cd web
npm test                 # timing unit tests (Node 20+), same totals as the Kotlin TimingTest/ControllerTest
npm install && npm run e2e   # headless Chrome check of every technique (needs Chrome; set CHROME=/path/to/chrome)
npm run serve            # then open http://localhost:8080
```

## Android app
Offline Android app for guided breathing practice. Package `com.javed.breathwork`, minSdk 26, target/compile SDK 36. Built with Kotlin, Jetpack Compose, Material 3 and DataStore. It has no INTERNET permission, no accounts, ads, analytics or trackers.

Techniques (see `RESEARCH.md` for timing and sources): Cyclic sighing, Box breathing, Extended exhale, Resonance breathing, 4-7-8, Physiological sigh (quick reset), Cyclic hyperventilation (with a required safety acknowledgment).

### Features
- Technique list with purpose (Calm / Focus / Energy / Sleep) and timing.
- Detail screen: pattern variant, duration (1, 3, 5, 10, 15, 20 min or custom 1–120), or rounds, breaths per round and hold length for hyperventilation. Shows the exact session length before you start.
- Session: 3-second lead-in, then an animated pacer. The circle grows on the inhale, has a two-stage ring for the sigh's top-up inhale, stays full on holds and shrinks on the exhale. Box breathing shows a dot tracing a square. Also shows the phase label, a per-phase seconds countdown, breath/round counter, time left, a progress bar, and Pause/Resume/End.
- Cues, each toggleable: rising tone on inhale, falling tone on exhale, soft tick each second of a hold, spoken "Inhale / Hold / Exhale" (device TTS), vibration (1 long = inhale, 2 short = exhale, short tap = hold), and a volume control.
- Keeps the screen on during a session. A foreground `mediaPlayback` service with a notification (Pause/Resume/End), a partial wake lock while running, audio focus, and pause on headphone unplug keep cues going with the screen off.
- On-device history and daily streak. Clear it any time.
- Adaptive and themed icon, dark/light theme, TalkBack labels, live-region phase announcements.

### Build
Requires JDK 17+ (tested with JDK 21) and the Android SDK with platform 36. The Gradle wrapper downloads Gradle 8.13.
```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew clean test assembleDebug lint
./gradlew bundleRelease   # needs keystore.properties, below
```
Release signing reads `keystore.properties` (not included):
```
storeFile=/abs/path/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```
Without that file, `bundleRelease` produces an unsigned bundle.

### Tests
- `TimingTest`: every pattern's step timings, exact session totals, whole-cycle and continuity checks for every technique and duration, pacer geometry, tone and haptic generation, streak and history codec.
- `ControllerTest`: lead-in, phase order, hold ticks, pause freezing time, ending early, drift-free late ticks.
- `UiFlowTest` (Robolectric): opens and starts each of the 7 techniques, including the safety dialog, pauses and resumes, ends, opens history, and saves screenshots.

### Privacy
No data leaves the device. Settings and history live in app storage (DataStore). Spoken cues use the device text-to-speech engine. Permissions: notifications, foreground service (media playback), wake lock, vibrate.

### Disclaimer
Techniques discussed on the Huberman Lab podcast and in Balban et al. 2023. The app is not affiliated with or endorsed by Huberman Lab or Stanford. Not medical advice. Do not practice cyclic hyperventilation in or near water, while driving or standing, or if pregnant, or with a heart condition, epilepsy or panic disorder.
