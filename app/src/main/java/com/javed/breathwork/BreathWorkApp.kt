package com.javed.breathwork

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.javed.breathwork.data.HistoryEntry
import com.javed.breathwork.data.Store
import com.javed.breathwork.data.dataStore
import com.javed.breathwork.domain.SessionConfig
import com.javed.breathwork.engine.AndroidCues
import com.javed.breathwork.engine.SessionController
import com.javed.breathwork.service.BreathService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = Store(app.dataStore)
    val cues = AndroidCues(app)
    /** Tests turn this off to avoid starting the foreground service under Robolectric. */
    var serviceEnabled = true
    val controller = SessionController(scope, { SystemClock.elapsedRealtime() }, cues, onComplete = { s ->
        val tl = s.timeline ?: return@SessionController
        val secs = (s.elapsedMs / 1000).toInt()
        if (secs >= 10) scope.launch { store.add(HistoryEntry(Store.today(), tl.config.technique.id, secs, s.completed)) }
    })

    init { scope.launch { store.cues.collectLatest { cues.settings = it } } }

    fun start(config: SessionConfig) {
        cues.prepare()
        controller.start(config)
        if (serviceEnabled) runCatching { ContextCompat.startForegroundService(app, BreathService.intent(app, BreathService.ACTION_START)) }
    }
}

class BreathWorkApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as BreathWorkApp).container
