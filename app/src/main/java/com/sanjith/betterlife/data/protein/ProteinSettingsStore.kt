package com.sanjith.betterlife.data.protein

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The protein goal and its daily reminder. [targetGrams] null = no goal yet. [goalSetOn] is the day the
 * first goal was saved (days before it are greyed, not missed). The reminder starts at [reminderTime]
 * every day while [reminderOn].
 */
public data class ProteinSettings(
    val targetGrams: Int? = null,
    val goalSetOn: LocalDate? = null,
    val reminderOn: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_TIME,
) {
    public companion object {
        public val DEFAULT_TIME: LocalTime = LocalTime.of(20, 0)
    }
}

/** Local only, in the settings DataStore. Also keeps the pending repeat, so a restart never loses it. */
public class ProteinSettingsStore(private val dataStore: DataStore<Preferences>) {

    public val settings: Flow<ProteinSettings> = dataStore.data.map { p ->
        ProteinSettings(
            targetGrams = p[TARGET],
            goalSetOn = p[GOAL_SET_ON]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            reminderOn = p[REMINDER_ON] ?: false,
            reminderTime = p[REMINDER_MINUTE]?.let { LocalTime.ofSecondOfDay((it * SECONDS_PER_MINUTE).toLong()) } ?: ProteinSettings.DEFAULT_TIME,
        )
    }

    public suspend fun current(): ProteinSettings = settings.first()

    /** Saves the goal; the first goal also records [today] as the day tracking starts. */
    public suspend fun setTarget(grams: Int, today: LocalDate) {
        dataStore.edit {
            it[TARGET] = grams
            if (it[GOAL_SET_ON] == null) it[GOAL_SET_ON] = today.toString()
        }
    }

    public suspend fun setReminder(on: Boolean, time: LocalTime) {
        dataStore.edit {
            it[REMINDER_ON] = on
            it[REMINDER_MINUTE] = time.toSecondOfDay() / SECONDS_PER_MINUTE
        }
    }

    public suspend fun repeatAt(): Instant? = dataStore.data.first()[REPEAT_AT]?.let(Instant::ofEpochMilli)

    public suspend fun setRepeatAt(at: Instant?) {
        dataStore.edit { if (at == null) it.remove(REPEAT_AT) else it[REPEAT_AT] = at.toEpochMilli() }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        val TARGET = intPreferencesKey("protein_target_grams")
        val GOAL_SET_ON = stringPreferencesKey("protein_goal_set_on")
        val REMINDER_ON = booleanPreferencesKey("protein_reminder_on")
        val REMINDER_MINUTE = intPreferencesKey("protein_reminder_minute")
        val REPEAT_AT = longPreferencesKey("protein_repeat_at")
    }
}
