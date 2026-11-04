package com.sanjith.betterlife.domain.settings

import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Every owner-editable setting with its default (context.md sections 5 and 13). Times are local
 * wall-clock times; the defaults here are the only place these numbers are written down.
 */
public data class AppSettings(
    /** Habits, Today and the daily score switch days at this hour (03:00: ticking at 01:00 counts for yesterday). */
    val rolloverHour: Int = 3,
    val snoozeTonight: LocalTime = LocalTime.of(20, 0),
    val snoozeTomorrow: LocalTime = LocalTime.of(9, 0),
    val birthdayFireTime: LocalTime = LocalTime.of(9, 0),
    val popupTimeoutMinutes: Int = 5,
    val quietHoursEnabled: Boolean = false,
    /** May cross midnight: 22:00 to 07:00 is valid. */
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),
    val alarmBypassesQuietHours: Boolean = true,
    val fatigueMaxFullScreenPerHour: Int = 3,
    val fatigueGroupWindowMinutes: Int = 10,
    val fatigueIgnoredLimit: Int = 3,
    val fatigueDowngradeEnabled: Boolean = true,
    val alarmExemptFromFatigue: Boolean = true,
    /** Calendar events, birthdays and ALARM level keep firing during Pause unless this is turned off. */
    val pauseExemptsEventsAndAlarms: Boolean = true,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Lock-screen privacy: pop-ups and their notification show "Reminder" instead of the title. */
    val hideTitleOnLockScreen: Boolean = false,
    /** Morning check-in (pick today's top 3) and evening wrap-up nudges; both off by default (Prompt 9). */
    val morningCheckInEnabled: Boolean = false,
    val morningCheckInTime: LocalTime = LocalTime.of(8, 0),
    val eveningWrapUpEnabled: Boolean = false,
    val eveningWrapUpTime: LocalTime = LocalTime.of(21, 0),
    /** Reminders new items get (Prompt 10); editable in the Reminder defaults screen. */
    val reminderDefaults: ReminderDefaults = ReminderDefaults(),
) {
    init {
        require(rolloverHour in 0..MAX_ROLLOVER_HOUR) { "rolloverHour must be 0 to $MAX_ROLLOVER_HOUR" }
        require(popupTimeoutMinutes > 0) { "popupTimeoutMinutes must be positive" }
        require(fatigueMaxFullScreenPerHour > 0) { "fatigueMaxFullScreenPerHour must be positive" }
        require(fatigueGroupWindowMinutes >= 0) { "fatigueGroupWindowMinutes cannot be negative" }
        require(fatigueIgnoredLimit > 0) { "fatigueIgnoredLimit must be positive" }
    }

    public companion object {
        /** A rollover later than 05:00 would make early-morning ticks land on the wrong day. */
        public const val MAX_ROLLOVER_HOUR: Int = 5
    }
}
