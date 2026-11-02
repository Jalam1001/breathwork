package com.javed.breathwork.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.javed.breathwork.engine.CueSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("breathwork_prefs")

data class HistoryEntry(val epochDay: Long, val techniqueId: String, val seconds: Int, val completed: Boolean) {
    fun encode() = "$epochDay|$techniqueId|$seconds|${if (completed) 1 else 0}"
    companion object {
        fun decode(s: String): HistoryEntry? = s.split('|').takeIf { it.size == 4 }?.let {
            runCatching { HistoryEntry(it[0].toLong(), it[1], it[2].toInt(), it[3] == "1") }.getOrNull()
        }
    }
}

object Streaks {
    /** Consecutive days with any session, ending today or yesterday. */
    fun current(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}

class Store(private val ds: DataStore<Preferences>) {
    private object K {
        val tones = booleanPreferencesKey("tones"); val voice = booleanPreferencesKey("voice")
        val haptics = booleanPreferencesKey("haptics"); val ticks = booleanPreferencesKey("ticks")
        val volume = floatPreferencesKey("volume"); val ack = booleanPreferencesKey("hyper_ack")
        val history = stringPreferencesKey("history")
    }

    val cues: Flow<CueSettings> = ds.data.map { p ->
        CueSettings(p[K.tones] ?: true, p[K.voice] ?: false, p[K.haptics] ?: true, p[K.ticks] ?: true, p[K.volume] ?: 0.7f)
    }
    val hyperAcknowledged: Flow<Boolean> = ds.data.map { it[K.ack] ?: false }
    val history: Flow<List<HistoryEntry>> = ds.data.map { p ->
        p[K.history].orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull(HistoryEntry::decode)
    }

    suspend fun setCues(c: CueSettings) = ds.edit {
        it[K.tones] = c.tones; it[K.voice] = c.voice; it[K.haptics] = c.haptics; it[K.ticks] = c.holdTicks; it[K.volume] = c.volume
    }
    suspend fun acknowledgeHyper() = ds.edit { it[K.ack] = true }
    suspend fun add(e: HistoryEntry) = ds.edit {
        val lines = it[K.history].orEmpty().split('\n').filter { l -> l.isNotBlank() } + e.encode()
        it[K.history] = lines.takeLast(1000).joinToString("\n")
    }
    suspend fun clearHistory() = ds.edit { it.remove(K.history) }

    companion object { fun today() = LocalDate.now().toEpochDay() }
}
