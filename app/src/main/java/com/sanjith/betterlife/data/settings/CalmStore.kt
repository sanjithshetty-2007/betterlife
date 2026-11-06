package com.sanjith.betterlife.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.sanjith.betterlife.domain.delivery.Pause
import com.sanjith.betterlife.reminders.engine.CalmState
import com.sanjith.betterlife.reminders.engine.CalmStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * The pause, the pending digest time and the fatigue counters, on disk in Preferences DataStore
 * (local only, never synced). Survives a killed process and a reboot.
 */
public class CalmStore(private val dataStore: DataStore<Preferences>) : CalmStateStore {

    public val state: Flow<CalmState> = dataStore.data.map(::read)

    override suspend fun read(): CalmState = state.first()

    override suspend fun update(change: (CalmState) -> CalmState) {
        dataStore.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private fun read(prefs: Preferences): CalmState = CalmState(
        pause = prefs[PAUSE_UNTIL]?.let { until ->
            Pause(Instant.ofEpochMilli(until), prefs[PAUSE_HOLDS_CALENDAR] ?: false, prefs[PAUSE_HOLDS_ALARM] ?: false)
        },
        digestAt = prefs[DIGEST_AT]?.let(Instant::ofEpochMilli),
        ignoreStreak = prefs[IGNORE_STREAK] ?: 0,
        calmUntil = prefs[CALM_UNTIL]?.let(Instant::ofEpochMilli),
    )

    private fun write(prefs: MutablePreferences, s: CalmState) {
        prefs.setOrRemove(PAUSE_UNTIL, s.pause?.until?.toEpochMilli())
        s.pause?.let {
            prefs[PAUSE_HOLDS_CALENDAR] = it.holdsCalendar
            prefs[PAUSE_HOLDS_ALARM] = it.holdsAlarm
        }
        prefs.setOrRemove(DIGEST_AT, s.digestAt?.toEpochMilli())
        prefs[IGNORE_STREAK] = s.ignoreStreak
        prefs.setOrRemove(CALM_UNTIL, s.calmUntil?.toEpochMilli())
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<Long>, value: Long?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val PAUSE_UNTIL = longPreferencesKey("calm_pause_until")
        val PAUSE_HOLDS_CALENDAR = booleanPreferencesKey("calm_pause_holds_calendar")
        val PAUSE_HOLDS_ALARM = booleanPreferencesKey("calm_pause_holds_alarm")
        val DIGEST_AT = longPreferencesKey("calm_digest_at")
        val IGNORE_STREAK = intPreferencesKey("calm_ignore_streak")
        val CALM_UNTIL = longPreferencesKey("calm_until")
    }
}
