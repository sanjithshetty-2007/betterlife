package com.sanjith.betterlife.data.alarm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanjith.betterlife.domain.alarm.AlarmKind
import com.sanjith.betterlife.domain.alarm.PendingTestAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Remembers the one pending spike alarm, because Android forgets every alarm on reboot. */
public class PendingAlarmStore(private val dataStore: DataStore<Preferences>) {

    public val pending: Flow<PendingTestAlarm?> = dataStore.data.map { prefs ->
        val trigger = prefs[TRIGGER_KEY]
        val kind = prefs[KIND_KEY]?.let { name -> AlarmKind.entries.firstOrNull { it.name == name } }
        if (trigger != null && kind != null) PendingTestAlarm(trigger, kind) else null
    }

    public suspend fun current(): PendingTestAlarm? = pending.first()

    public suspend fun save(alarm: PendingTestAlarm) {
        dataStore.edit {
            it[TRIGGER_KEY] = alarm.triggerAtMillis
            it[KIND_KEY] = alarm.kind.name
        }
    }

    public suspend fun clear() {
        dataStore.edit {
            it.remove(TRIGGER_KEY)
            it.remove(KIND_KEY)
        }
    }

    private companion object {
        val TRIGGER_KEY = longPreferencesKey("spike_alarm_trigger_at")
        val KIND_KEY = stringPreferencesKey("spike_alarm_kind")
    }
}
