package com.sanjith.betterlife.data.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One health test reminder: the "Remind me" item behind it, its reminder row, and when it is due. */
public data class HealthTest(val itemId: String, val reminderId: String, val dueMillis: Long)

/** Reminder Health's own small state: the last check time and the two test reminders (Prompt 11). */
public class HealthStore(private val dataStore: DataStore<Preferences>) {

    public val lastCheckedAt: Flow<Long?> = dataStore.data.map { it[LAST_CHECKED] }

    /** The latest "Test after closing the app", so its result can be shown later. */
    public val closedAppTest: Flow<HealthTest?> = dataStore.data.map { decode(it[CLOSED_APP_TEST]) }

    public suspend fun setLastCheckedAt(millis: Long) {
        dataStore.edit { it[LAST_CHECKED] = millis }
    }

    public suspend fun quickTest(): HealthTest? = dataStore.data.first().let { decode(it[QUICK_TEST]) }

    public suspend fun closedAppTestNow(): HealthTest? = closedAppTest.first()

    public suspend fun saveQuickTest(test: HealthTest) {
        dataStore.edit { it[QUICK_TEST] = encode(test) }
    }

    public suspend fun saveClosedAppTest(test: HealthTest) {
        dataStore.edit { it[CLOSED_APP_TEST] = encode(test) }
    }

    private fun encode(t: HealthTest): String = listOf(t.itemId, t.reminderId, t.dueMillis.toString()).joinToString(SEPARATOR)

    // A bad stored value is treated as "no test", never a crash.
    private fun decode(text: String?): HealthTest? {
        val parts = text?.split(SEPARATOR)?.takeIf { it.size == PARTS } ?: return null
        val due = parts[2].toLongOrNull() ?: return null
        return HealthTest(parts[0], parts[1], due)
    }

    private companion object {
        const val SEPARATOR = "|"
        const val PARTS = 3
        val LAST_CHECKED = longPreferencesKey("health_last_checked_at")
        val QUICK_TEST = stringPreferencesKey("health_quick_test")
        val CLOSED_APP_TEST = stringPreferencesKey("health_closed_app_test")
    }
}
