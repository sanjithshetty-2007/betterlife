package com.sanjith.betterlife.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.sanjith.betterlife.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Typed access to settings in Preferences DataStore. A missing or invalid stored value falls back to
 * the default in [AppSettings], so a bad value can never crash the app. Times are stored as
 * minutes since midnight, the week start as ISO day number (1 = Monday).
 */
public class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    public val settings: Flow<AppSettings> = dataStore.data.map(::read)

    public suspend fun current(): AppSettings = settings.first()

    /** Applies [change] to the current settings and saves the result. Invalid results throw before saving. */
    public suspend fun update(change: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private fun read(prefs: Preferences): AppSettings {
        val d = AppSettings()
        return runCatching {
            AppSettings(
                rolloverHour = prefs[ROLLOVER_HOUR] ?: d.rolloverHour,
                snoozeTonight = prefs.time(SNOOZE_TONIGHT, d.snoozeTonight),
                snoozeTomorrow = prefs.time(SNOOZE_TOMORROW, d.snoozeTomorrow),
                birthdayFireTime = prefs.time(BIRTHDAY_TIME, d.birthdayFireTime),
                popupTimeoutMinutes = prefs[POPUP_TIMEOUT] ?: d.popupTimeoutMinutes,
                quietHoursEnabled = prefs[QUIET_ENABLED] ?: d.quietHoursEnabled,
                quietHoursStart = prefs.time(QUIET_START, d.quietHoursStart),
                quietHoursEnd = prefs.time(QUIET_END, d.quietHoursEnd),
                alarmBypassesQuietHours = prefs[ALARM_BYPASS_QUIET] ?: d.alarmBypassesQuietHours,
                fatigueMaxFullScreenPerHour = prefs[FATIGUE_MAX_PER_HOUR] ?: d.fatigueMaxFullScreenPerHour,
                fatigueGroupWindowMinutes = prefs[FATIGUE_GROUP_MINUTES] ?: d.fatigueGroupWindowMinutes,
                fatigueIgnoredLimit = prefs[FATIGUE_IGNORED_LIMIT] ?: d.fatigueIgnoredLimit,
                fatigueDowngradeEnabled = prefs[FATIGUE_DOWNGRADE] ?: d.fatigueDowngradeEnabled,
                alarmExemptFromFatigue = prefs[ALARM_EXEMPT_FATIGUE] ?: d.alarmExemptFromFatigue,
                pauseExemptsEventsAndAlarms = prefs[PAUSE_EXEMPTS] ?: d.pauseExemptsEventsAndAlarms,
                weekStart = prefs[WEEK_START]?.let(DayOfWeek::of) ?: d.weekStart,
            )
        }.getOrDefault(d)
    }

    private fun write(prefs: MutablePreferences, s: AppSettings) {
        prefs[ROLLOVER_HOUR] = s.rolloverHour
        prefs[SNOOZE_TONIGHT] = s.snoozeTonight.toMinuteOfDay()
        prefs[SNOOZE_TOMORROW] = s.snoozeTomorrow.toMinuteOfDay()
        prefs[BIRTHDAY_TIME] = s.birthdayFireTime.toMinuteOfDay()
        prefs[POPUP_TIMEOUT] = s.popupTimeoutMinutes
        prefs[QUIET_ENABLED] = s.quietHoursEnabled
        prefs[QUIET_START] = s.quietHoursStart.toMinuteOfDay()
        prefs[QUIET_END] = s.quietHoursEnd.toMinuteOfDay()
        prefs[ALARM_BYPASS_QUIET] = s.alarmBypassesQuietHours
        prefs[FATIGUE_MAX_PER_HOUR] = s.fatigueMaxFullScreenPerHour
        prefs[FATIGUE_GROUP_MINUTES] = s.fatigueGroupWindowMinutes
        prefs[FATIGUE_IGNORED_LIMIT] = s.fatigueIgnoredLimit
        prefs[FATIGUE_DOWNGRADE] = s.fatigueDowngradeEnabled
        prefs[ALARM_EXEMPT_FATIGUE] = s.alarmExemptFromFatigue
        prefs[PAUSE_EXEMPTS] = s.pauseExemptsEventsAndAlarms
        prefs[WEEK_START] = s.weekStart.value
    }

    private fun Preferences.time(key: Preferences.Key<Int>, default: LocalTime): LocalTime =
        this[key]?.let { minutes -> LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR) } ?: default

    private fun LocalTime.toMinuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

    private companion object {
        const val MINUTES_PER_HOUR = 60
        val ROLLOVER_HOUR = intPreferencesKey("rollover_hour")
        val SNOOZE_TONIGHT = intPreferencesKey("snooze_tonight_minute")
        val SNOOZE_TOMORROW = intPreferencesKey("snooze_tomorrow_minute")
        val BIRTHDAY_TIME = intPreferencesKey("birthday_fire_minute")
        val POPUP_TIMEOUT = intPreferencesKey("popup_timeout_minutes")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_END = intPreferencesKey("quiet_hours_end_minute")
        val ALARM_BYPASS_QUIET = booleanPreferencesKey("alarm_bypasses_quiet_hours")
        val FATIGUE_MAX_PER_HOUR = intPreferencesKey("fatigue_max_full_screen_per_hour")
        val FATIGUE_GROUP_MINUTES = intPreferencesKey("fatigue_group_window_minutes")
        val FATIGUE_IGNORED_LIMIT = intPreferencesKey("fatigue_ignored_limit")
        val FATIGUE_DOWNGRADE = booleanPreferencesKey("fatigue_downgrade_enabled")
        val ALARM_EXEMPT_FATIGUE = booleanPreferencesKey("alarm_exempt_from_fatigue")
        val PAUSE_EXEMPTS = booleanPreferencesKey("pause_exempts_events_and_alarms")
        val WEEK_START = intPreferencesKey("week_start_iso_day")
    }
}
