package com.javed.breathwork.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.javed.breathwork.domain.PhaseKind
import com.javed.breathwork.domain.Segment
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

data class CueSettings(
    val tones: Boolean = true,
    val voice: Boolean = false,
    val haptics: Boolean = true,
    val holdTicks: Boolean = true,
    val volume: Float = 0.7f,
)

/** Pure PCM synthesis for cue sounds; unit-tested. */
object ToneSynth {
    const val RATE = 22_050

    /** Smooth sine glide from f0 to f1 Hz with soft attack/release. Length is capped so cues stay gentle. */
    fun glide(f0: Double, f1: Double, ms: Long, maxMs: Long = 2500): ShortArray {
        val dur = min(ms, maxMs).coerceAtLeast(150)
        val n = (RATE * dur / 1000).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        val fade = (RATE * 0.12).toInt().coerceAtMost(n / 2)
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val f = f0 + (f1 - f0) * p
            phase += 2 * PI * f / RATE
            val env = when { i < fade -> i.toDouble() / fade; i > n - fade -> (n - i).toDouble() / fade; else -> 1.0 }
            out[i] = (0.35 * env * sin(phase) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Short, soft woodblock-like tick for holds. */
    fun tick(): ShortArray {
        val n = (RATE * 0.06).toInt()
        return ShortArray(n) { i -> val t = i.toDouble() / RATE; (0.25 * exp(-t * 70) * sin(2 * PI * 1200 * t) * Short.MAX_VALUE).toInt().toShort() }
    }

    /** Two-tone chime for session end / lead-in. */
    fun chime(freq: Double = 660.0, ms: Long = 900): ShortArray {
        val n = (RATE * ms / 1000).toInt()
        return ShortArray(n) { i -> val t = i.toDouble() / RATE; val a = min(1.0, t / 0.01) * exp(-t * 3.5)
            (0.3 * a * (sin(2 * PI * freq * t) + 0.4 * sin(2 * PI * freq * 2.01 * t)) / 1.4 * Short.MAX_VALUE).toInt().toShort() }
    }

    fun inhale(ms: Long) = glide(220.0, 440.0, ms)
    fun topUp(ms: Long) = glide(440.0, 554.0, ms, 1200)
    fun exhale(ms: Long) = glide(440.0, 196.0, ms, 3500)
}

/** Haptic patterns (timings in ms, alternating off/on) per phase kind. */
object Haptics {
    fun pattern(kind: PhaseKind): LongArray = when {
        kind == PhaseKind.TOP_UP -> longArrayOf(0, 60, 60, 60)
        kind.isInhale -> if (kind == PhaseKind.BREATHE_IN) longArrayOf(0, 40) else longArrayOf(0, 220)
        kind.isExhale -> if (kind == PhaseKind.BREATHE_OUT) longArrayOf(0, 0) else longArrayOf(0, 90, 110, 90)
        else -> longArrayOf(0, 30)
    }
}

class AndroidCues(context: Context) : Cues {
    private val app = context.applicationContext
    @Volatile var settings = CueSettings()
    @Volatile var enabled = true
    private var track: AudioTrack? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
    } catch (e: Exception) { null }

    private fun ensureTts() {
        if (tts != null) return
        tts = try {
            TextToSpeech(app) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale.getDefault()
            }
        } catch (e: Exception) { Log.w(TAG, "TTS unavailable", e); null }
    }

    private fun play(pcm: ShortArray) {
        if (!enabled) return
        try {
            track?.let { runCatching { it.stop() }; it.release() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(ToneSynth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            t.write(pcm, 0, pcm.size)
            t.setVolume(settings.volume.coerceIn(0f, 1f))
            t.play()
            track = t
        } catch (e: Exception) { Log.w(TAG, "tone failed", e) }
    }

    private fun vibrate(p: LongArray) {
        if (!enabled || p.size < 2 || p.drop(1).all { it == 0L }) return
        try { vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(p, -1)) } catch (e: Exception) { Log.w(TAG, "vibrate", e) }
    }

    private fun speak(text: String) {
        if (!enabled) return
        ensureTts()
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }, "cue")
    }

    fun prepare() { if (settings.voice) ensureTts() }

    override fun onSegment(segment: Segment) {
        val s = settings; val k = segment.step.kind; val ms = segment.step.ms
        val fast = k == PhaseKind.BREATHE_IN || k == PhaseKind.BREATHE_OUT
        if (s.tones) when {
            k == PhaseKind.TOP_UP -> play(ToneSynth.topUp(ms))
            k.isInhale -> play(ToneSynth.inhale(ms))
            k.isExhale -> play(ToneSynth.exhale(ms))
            else -> play(ToneSynth.tick())
        }
        // Fast breaths are too quick for speech; count them instead every 5th breath.
        if (s.voice) {
            if (!fast) speak(segment.step.label)
            else if (k == PhaseKind.BREATHE_IN && segment.indexInCycle % 10 == 0) speak("${segment.indexInCycle / 2 + 1}")
        }
        if (s.haptics) vibrate(Haptics.pattern(k))
    }

    override fun onHoldSecond() { if (settings.tones && settings.holdTicks) play(ToneSynth.tick()) }
    override fun onLeadInTick(secondsLeft: Int) { if (settings.tones) play(ToneSynth.tick()); if (settings.voice && secondsLeft == 3) speak("Get ready") }
    override fun onFinished() { if (settings.tones) play(ToneSynth.chime()); if (settings.voice) speak("Session complete"); if (settings.haptics) vibrate(longArrayOf(0, 300)) }
    override fun stop() { runCatching { track?.stop() }; runCatching { tts?.stop() }; runCatching { vibrator?.cancel() } }

    companion object { private const val TAG = "BreathWork.Cues" }
}
