package com.sanjith.betterlife.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
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
                autoSnoozeMinutes = prefs[AUTO_SNOOZE] ?: d.autoSnoozeMinutes,
                alarmSoundUri = prefs[ALARM_SOUND] ?: d.alarmSoundUri,
                popupsRingOnSilent = prefs[RING_ON_SILENT] ?: d.popupsRingOnSilent,
                quietHoursEnabled = prefs[QUIET_ENABLED] ?: d.quietHoursEnabled,
                quietHoursStart = prefs.time(QUIET_START, d.quietHoursStart),
                quietHoursEnd = prefs.time(QUIET_END, d.quietHoursEnd),
                quietHoursDays = prefs[QUIET_DAYS]?.let(::daysFromMask) ?: d.quietHoursDays,
                alarmBypassesQuietHours = prefs[ALARM_BYPASS_QUIET] ?: d.alarmBypassesQuietHours,
                fatigueMaxFullScreenPerHour = prefs[FATIGUE_MAX_PER_HOUR] ?: d.fatigueMaxFullScreenPerHour,
                fatigueGroupWindowMinutes = prefs[FATIGUE_GROUP_MINUTES] ?: d.fatigueGroupWindowMinutes,
                fatigueIgnoredLimit = prefs[FATIGUE_IGNORED_LIMIT] ?: d.fatigueIgnoredLimit,
                fatigueDowngradeEnabled = prefs[FATIGUE_DOWNGRADE] ?: d.fatigueDowngradeEnabled,
                alarmExemptFromFatigue = prefs[ALARM_EXEMPT_FATIGUE] ?: d.alarmExemptFromFatigue,
                pauseExemptsEventsAndAlarms = prefs[PAUSE_EXEMPTS] ?: d.pauseExemptsEventsAndAlarms,
                weekStart = prefs[WEEK_START]?.let(DayOfWeek::of) ?: d.weekStart,
                hideTitleOnLockScreen = prefs[HIDE_TITLE_LOCKED] ?: d.hideTitleOnLockScreen,
                morningCheckInEnabled = prefs[MORNING_ENABLED] ?: d.morningCheckInEnabled,
                morningCheckInTime = prefs.time(MORNING_TIME, d.morningCheckInTime),
                eveningWrapUpEnabled = prefs[EVENING_ENABLED] ?: d.eveningWrapUpEnabled,
                eveningWrapUpTime = prefs.time(EVENING_TIME, d.eveningWrapUpTime),
                reminderDefaults = ReminderDefaults(
                    birthday = ReminderDefaults.decode(prefs[DEFAULTS_BIRTHDAY]) ?: d.reminderDefaults.birthday,
                    deadline = ReminderDefaults.decode(prefs[DEFAULTS_DEADLINE]) ?: d.reminderDefaults.deadline,
                    event = ReminderDefaults.decode(prefs[DEFAULTS_EVENT]) ?: d.reminderDefaults.event,
                    standalone = ReminderDefaults.decode(prefs[DEFAULTS_STANDALONE]) ?: d.reminderDefaults.standalone,
                ),
                habitTickHaptics = prefs[HABIT_HAPTICS] ?: d.habitTickHaptics,
                mascotName = prefs[MASCOT_NAME]?.takeIf { it.isNotBlank() } ?: d.mascotName,
                ownerName = prefs[OWNER_NAME] ?: d.ownerName,
                habitNudgeEnabled = prefs[HABIT_NUDGE_ENABLED] ?: d.habitNudgeEnabled,
                habitNudgeTime = prefs.time(HABIT_NUDGE_TIME, d.habitNudgeTime),
            )
        }.getOrDefault(d)
    }

    private fun write(prefs: MutablePreferences, s: AppSettings) {
        prefs[ROLLOVER_HOUR] = s.rolloverHour
        prefs[HABIT_HAPTICS] = s.habitTickHaptics
        prefs[MASCOT_NAME] = s.mascotName
        prefs[OWNER_NAME] = s.ownerName
        prefs[HABIT_NUDGE_ENABLED] = s.habitNudgeEnabled
        prefs[HABIT_NUDGE_TIME] = s.habitNudgeTime.toMinuteOfDay()
        prefs[SNOOZE_TONIGHT] = s.snoozeTonight.toMinuteOfDay()
        prefs[SNOOZE_TOMORROW] = s.snoozeTomorrow.toMinuteOfDay()
        prefs[BIRTHDAY_TIME] = s.birthdayFireTime.toMinuteOfDay()
        prefs[POPUP_TIMEOUT] = s.popupTimeoutMinutes
        prefs[AUTO_SNOOZE] = s.autoSnoozeMinutes
        if (s.alarmSoundUri == null) prefs.remove(ALARM_SOUND) else prefs[ALARM_SOUND] = s.alarmSoundUri
        prefs[RING_ON_SILENT] = s.popupsRingOnSilent
        prefs[QUIET_ENABLED] = s.quietHoursEnabled
        prefs[QUIET_START] = s.quietHoursStart.toMinuteOfDay()
        prefs[QUIET_END] = s.quietHoursEnd.toMinuteOfDay()
        prefs[QUIET_DAYS] = s.quietHoursDays.fold(0) { mask, day -> mask or (1 shl day.ordinal) }
        prefs[ALARM_BYPASS_QUIET] = s.alarmBypassesQuietHours
        prefs[FATIGUE_MAX_PER_HOUR] = s.fatigueMaxFullScreenPerHour
        prefs[FATIGUE_GROUP_MINUTES] = s.fatigueGroupWindowMinutes
        prefs[FATIGUE_IGNORED_LIMIT] = s.fatigueIgnoredLimit
        prefs[FATIGUE_DOWNGRADE] = s.fatigueDowngradeEnabled
        prefs[ALARM_EXEMPT_FATIGUE] = s.alarmExemptFromFatigue
        prefs[PAUSE_EXEMPTS] = s.pauseExemptsEventsAndAlarms
        prefs[WEEK_START] = s.weekStart.value
        prefs[HIDE_TITLE_LOCKED] = s.hideTitleOnLockScreen
        prefs[MORNING_ENABLED] = s.morningCheckInEnabled
        prefs[MORNING_TIME] = s.morningCheckInTime.toMinuteOfDay()
        prefs[EVENING_ENABLED] = s.eveningWrapUpEnabled
        prefs[EVENING_TIME] = s.eveningWrapUpTime.toMinuteOfDay()
        prefs[DEFAULTS_BIRTHDAY] = ReminderDefaults.encode(s.reminderDefaults.birthday)
        prefs[DEFAULTS_DEADLINE] = ReminderDefaults.encode(s.reminderDefaults.deadline)
        prefs[DEFAULTS_EVENT] = ReminderDefaults.encode(s.reminderDefaults.event)
        prefs[DEFAULTS_STANDALONE] = ReminderDefaults.encode(s.reminderDefaults.standalone)
    }

    private fun Preferences.time(key: Preferences.Key<Int>, default: LocalTime): LocalTime =
        this[key]?.let { minutes -> LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR) } ?: default

    // Bit n set = DayOfWeek.entries[n] (Monday is bit 0).
    private fun daysFromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()

    private fun LocalTime.toMinuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

    private companion object {
        const val MINUTES_PER_HOUR = 60
        val ROLLOVER_HOUR = intPreferencesKey("rollover_hour")
        val HABIT_HAPTICS = booleanPreferencesKey("habit_tick_haptics")
        val AUTO_SNOOZE = intPreferencesKey("auto_snooze_minutes")
        val ALARM_SOUND = stringPreferencesKey("alarm_sound_uri")
        val RING_ON_SILENT = booleanPreferencesKey("popups_ring_on_silent")
        val MASCOT_NAME = stringPreferencesKey("mascot_name")
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val HABIT_NUDGE_ENABLED = booleanPreferencesKey("habit_nudge_enabled")
        val HABIT_NUDGE_TIME = intPreferencesKey("habit_nudge_time")
        val SNOOZE_TONIGHT = intPreferencesKey("snooze_tonight_minute")
        val SNOOZE_TOMORROW = intPreferencesKey("snooze_tomorrow_minute")
        val BIRTHDAY_TIME = intPreferencesKey("birthday_fire_minute")
        val POPUP_TIMEOUT = intPreferencesKey("popup_timeout_minutes")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_END = intPreferencesKey("quiet_hours_end_minute")
        val QUIET_DAYS = intPreferencesKey("quiet_hours_days_mask")
        val ALARM_BYPASS_QUIET = booleanPreferencesKey("alarm_bypasses_quiet_hours")
        val FATIGUE_MAX_PER_HOUR = intPreferencesKey("fatigue_max_full_screen_per_hour")
        val FATIGUE_GROUP_MINUTES = intPreferencesKey("fatigue_group_window_minutes")
        val FATIGUE_IGNORED_LIMIT = intPreferencesKey("fatigue_ignored_limit")
        val FATIGUE_DOWNGRADE = booleanPreferencesKey("fatigue_downgrade_enabled")
        val ALARM_EXEMPT_FATIGUE = booleanPreferencesKey("alarm_exempt_from_fatigue")
        val PAUSE_EXEMPTS = booleanPreferencesKey("pause_exempts_events_and_alarms")
        val WEEK_START = intPreferencesKey("week_start_iso_day")
        val HIDE_TITLE_LOCKED = booleanPreferencesKey("hide_title_on_lock_screen")
        val MORNING_ENABLED = booleanPreferencesKey("morning_check_in_enabled")
        val MORNING_TIME = intPreferencesKey("morning_check_in_minute")
        val EVENING_ENABLED = booleanPreferencesKey("evening_wrap_up_enabled")
        val EVENING_TIME = intPreferencesKey("evening_wrap_up_minute")
        val DEFAULTS_BIRTHDAY = stringPreferencesKey("reminder_defaults_birthday")
        val DEFAULTS_DEADLINE = stringPreferencesKey("reminder_defaults_deadline")
        val DEFAULTS_EVENT = stringPreferencesKey("reminder_defaults_event")
        val DEFAULTS_STANDALONE = stringPreferencesKey("reminder_defaults_standalone")
    }
}
