package com.sanjith.betterlife.data.habit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.StarterHabit
import com.sanjith.betterlife.domain.habit.StarterHabits
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Adds the four starter habits once per install (new installs and the upgrade to database version 5).
 * A flag remembers it, so habits the owner deletes never come back.
 */
public class StarterHabitSeeder(
    private val dataStore: DataStore<Preferences>,
    private val habits: HabitRepository,
    private val names: (StarterHabit.Key) -> String,
    private val today: suspend () -> LocalDate,
) {
    public suspend fun seedIfNeeded() {
        if (dataStore.data.first()[SEEDED] == true) return
        habits.insertStarters(StarterHabits.ALL, names, today())
        dataStore.edit { it[SEEDED] = true }
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("starter_habits_seeded")
    }
}
