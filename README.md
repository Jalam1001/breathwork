# BreathWork 1.0

Offline Android app for guided breathing practice. Package `com.javed.breathwork`, minSdk 26, target/compile SDK 36. Built with Kotlin, Jetpack Compose, Material 3 and DataStore. It has no INTERNET permission, no accounts, ads, analytics or trackers.

Techniques (see `RESEARCH.md` for timing and sources): Cyclic sighing, Box breathing, Extended exhale, Resonance breathing, 4-7-8, Physiological sigh (quick reset), Cyclic hyperventilation (with a required safety acknowledgment).

## Features
- Technique list with purpose (Calm / Focus / Energy / Sleep) and timing.
- Detail screen: pattern variant, duration (1, 3, 5, 10, 15, 20 min or custom 1–120), or rounds, breaths per round and hold length for hyperventilation. Shows the exact session length before you start.
- Session: 3-second lead-in, then an animated pacer. The circle grows on the inhale, has a two-stage ring for the sigh's top-up inhale, stays full on holds and shrinks on the exhale. Box breathing shows a dot tracing a square. Also shows the phase label, a per-phase seconds countdown, breath/round counter, time left, a progress bar, and Pause/Resume/End.
- Cues, each toggleable: rising tone on inhale, falling tone on exhale, soft tick each second of a hold, spoken "Inhale / Hold / Exhale" (device TTS), vibration (1 long = inhale, 2 short = exhale, short tap = hold), and a volume control.
- Keeps the screen on during a session. A foreground `mediaPlayback` service with a notification (Pause/Resume/End), a partial wake lock while running, audio focus, and pause on headphone unplug keep cues going with the screen off.
- On-device history and daily streak. Clear it any time.
- Adaptive and themed icon, dark/light theme, TalkBack labels, live-region phase announcements.

## Build
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

## Tests
- `TimingTest`: every pattern's step timings, exact session totals, whole-cycle and continuity checks for every technique and duration, pacer geometry, tone and haptic generation, streak and history codec.
- `ControllerTest`: lead-in, phase order, hold ticks, pause freezing time, ending early, drift-free late ticks.
- `UiFlowTest` (Robolectric): opens and starts each of the 7 techniques, including the safety dialog, pauses and resumes, ends, opens history, and saves screenshots.

## Privacy
No data leaves the device. Settings and history live in app storage (DataStore). Spoken cues use the device text-to-speech engine. Permissions: notifications, foreground service (media playback), wake lock, vibrate.

## Disclaimer
Techniques discussed on the Huberman Lab podcast and in Balban et al. 2023. The app is not affiliated with or endorsed by Huberman Lab or Stanford. Not medical advice. Do not practice cyclic hyperventilation in or near water, while driving or standing, or if pregnant, or with a heart condition, epilepsy or panic disorder.
