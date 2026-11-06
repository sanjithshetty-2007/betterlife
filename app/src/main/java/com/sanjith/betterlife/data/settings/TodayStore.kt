package com.sanjith.betterlife.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** What the Today screen remembers between days (Prompt 17): last open, comeback shown, reward flag. */
public data class TodayState(val lastOpened: LocalDate?, val comebackShownOn: LocalDate?, val comebackReward: Boolean)

/** Local only, in the settings DataStore. */
public class TodayStore(private val dataStore: DataStore<Preferences>) {

    public suspend fun read(): TodayState {
        val p = dataStore.data.first()
        return TodayState(p[LAST_OPENED]?.let(::date), p[COMEBACK_SHOWN]?.let(::date), p[COMEBACK_REWARD] ?: false)
    }

    /** Records an app open on [day]; returns the state from BEFORE this open (for the "away" check). */
    public suspend fun opened(day: LocalDate): TodayState {
        val before = read()
        dataStore.edit { it[LAST_OPENED] = day.toString() }
        return before
    }

    /** Comeback shown today; the bigger-reward flag stays until XP (Prompt 26) uses it. */
    public suspend fun comebackShown(day: LocalDate) {
        dataStore.edit {
            it[COMEBACK_SHOWN] = day.toString()
            it[COMEBACK_REWARD] = true
        }
    }

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private companion object {
        val LAST_OPENED = stringPreferencesKey("today_last_opened")
        val COMEBACK_SHOWN = stringPreferencesKey("today_comeback_shown")
        val COMEBACK_REWARD = booleanPreferencesKey("today_comeback_reward")
    }
}
