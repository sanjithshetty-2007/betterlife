package com.sanjith.betterlife.data.tracker

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanjith.betterlife.domain.tracker.TrackerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * One tracker's goal and daily reminder. [target] null = no goal yet (grams, or minutes of sleep).
 * [goalSetOn] is the day the first goal was saved (days before it are greyed, not missed). The reminder
 * starts at [reminderTime] every day while [reminderOn].
 */
public data class TrackerSettings(
    val target: Int? = null,
    val goalSetOn: LocalDate? = null,
    val reminderOn: Boolean = false,
    val reminderTime: LocalTime,
)

/**
 * Local only, in the settings DataStore. Also keeps the pending repeat, so a restart never loses it.
 * The protein keys are the ones the protein feature first wrote ("protein_target_grams" and so on).
 */
public class TrackerSettingsStore(private val dataStore: DataStore<Preferences>, kind: TrackerKind) {
    private val defaultTime = LocalTime.of(kind.defaultHour, 0)
    private val prefix = kind.name.lowercase()
    private val targetKey = intPreferencesKey(if (kind == TrackerKind.PROTEIN) "protein_target_grams" else "${prefix}_target_minutes")
    private val goalSetOnKey = stringPreferencesKey("${prefix}_goal_set_on")
    private val reminderOnKey = booleanPreferencesKey("${prefix}_reminder_on")
    private val reminderMinuteKey = intPreferencesKey("${prefix}_reminder_minute")
    private val repeatAtKey = longPreferencesKey("${prefix}_repeat_at")

    public val settings: Flow<TrackerSettings> = dataStore.data.map { p ->
        TrackerSettings(
            target = p[targetKey],
            goalSetOn = p[goalSetOnKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            reminderOn = p[reminderOnKey] ?: false,
            reminderTime = p[reminderMinuteKey]?.let { LocalTime.ofSecondOfDay((it * SECONDS_PER_MINUTE).toLong()) } ?: defaultTime,
        )
    }

    public suspend fun current(): TrackerSettings = settings.first()

    /** Saves the goal; the first goal also records [day] as the day tracking starts. */
    public suspend fun setTarget(target: Int, day: LocalDate) {
        dataStore.edit {
            it[targetKey] = target
            if (it[goalSetOnKey] == null) it[goalSetOnKey] = day.toString()
        }
    }

    public suspend fun setReminder(on: Boolean, time: LocalTime) {
        dataStore.edit {
            it[reminderOnKey] = on
            it[reminderMinuteKey] = time.toSecondOfDay() / SECONDS_PER_MINUTE
        }
    }

    public suspend fun repeatAt(): Instant? = dataStore.data.first()[repeatAtKey]?.let(Instant::ofEpochMilli)

    public suspend fun setRepeatAt(at: Instant?) {
        dataStore.edit { if (at == null) it.remove(repeatAtKey) else it[repeatAtKey] = at.toEpochMilli() }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
