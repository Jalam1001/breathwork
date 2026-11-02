package com.javed.breathwork.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.javed.breathwork.MainActivity
import com.javed.breathwork.R
import com.javed.breathwork.container
import com.javed.breathwork.engine.RunState
import com.javed.breathwork.engine.SessionState
import com.javed.breathwork.ui.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** mediaPlayback foreground service: keeps tones, voice and haptic cues running with the screen off. */
class BreathService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var pausedForFocus = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var noisyRegistered = false
    private val controller get() = container.controller

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS) hasFocus = false
                if (controller.state.value.run == RunState.RUNNING) { pausedForFocus = true; controller.pause() }
            }
            AudioManager.AUDIOFOCUS_GAIN -> { hasFocus = true; if (pausedForFocus) { pausedForFocus = false; controller.resume() } }
        }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (i.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) controller.pause() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_desc); setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, NOTIF_ID, build(controller.state.value), type)
        } catch (e: Exception) { Log.w(TAG, "startForeground failed", e); stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_END -> controller.end()
        }
        if (observer == null) observer = scope.launch {
            controller.state.distinctUntilChangedBy { it.run to it.elapsedMs / 1000 }.collect { onState(it) }
        }
        if (!controller.state.value.isActive) stopAll()
        return START_NOT_STICKY
    }

    private fun onState(s: SessionState) {
        if (!s.isActive) { stopAll(); return }
        if (s.run == RunState.PAUSED) releaseWake() else { requestFocus(); acquireWake(s.remainingMs + 10_000); registerNoisy() }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(s))
    }

    private fun requestFocus() {
        if (hasFocus) return
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener(focusListener).build().also { focusRequest = it }
        hasFocus = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun acquireWake(ms: Long) {
        val wl = wakeLock ?: getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BreathWork:session")
            .apply { setReferenceCounted(false) }.also { wakeLock = it }
        if (!wl.isHeld) wl.acquire(ms)
    }
    private fun releaseWake() { wakeLock?.takeIf { it.isHeld }?.release() }
    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    private fun stopAll() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }; hasFocus = false
        releaseWake()
        if (noisyRegistered) { runCatching { unregisterReceiver(noisy) }; noisyRegistered = false }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pending(action: String, code: Int) =
        PendingIntent.getService(this, code, intent(this, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(s: SessionState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val title = s.timeline?.config?.technique?.name ?: getString(R.string.app_name)
        val text = if (s.run == RunState.PAUSED) getString(R.string.notif_paused, formatClock(s.remainingMs))
            else getString(R.string.notif_remaining, s.segment?.step?.label ?: "", formatClock(s.remainingMs))
        val b = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_breath)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.run == RunState.PAUSED) b.addAction(R.drawable.ic_play, getString(R.string.resume), pending(ACTION_RESUME, 2))
        else b.addAction(R.drawable.ic_pause, getString(R.string.pause), pending(ACTION_PAUSE, 1))
        b.addAction(R.drawable.ic_stop, getString(R.string.end), pending(ACTION_END, 3))
        return b.build()
    }

    override fun onDestroy() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        releaseWake(); if (noisyRegistered) runCatching { unregisterReceiver(noisy) }
        scope.cancel(); super.onDestroy()
    }

    companion object {
        private const val TAG = "BreathWork.Service"
        const val CHANNEL_ID = "session"; const val NOTIF_ID = 7
        const val ACTION_START = "com.javed.breathwork.START"; const val ACTION_PAUSE = "com.javed.breathwork.PAUSE"
        const val ACTION_RESUME = "com.javed.breathwork.RESUME"; const val ACTION_END = "com.javed.breathwork.END"
        fun intent(ctx: Context, action: String) = Intent(ctx, BreathService::class.java).setAction(action)
    }
}
