package com.sanjith.betterlife.domain.tracker

import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import java.time.LocalDate

/**
 * The daily trackers (owner's features, 2026-11-21). [maxAmount] bounds one day's amount; [dayOffset]
 * is how many days the newest loggable day lies behind today; [defaultHour] is the reminder's default.
 * - PROTEIN: grams, logged for today; reminder 20:00.
 * - SLEEP: minutes, logged the morning after for the night that began the day before (a night counts
 *   for the day you went to bed); reminder 09:00.
 * Stored by name, so names never change.
 */
public enum class TrackerKind(public val maxAmount: Int, public val dayOffset: Long, public val defaultHour: Int) {
    PROTEIN(maxAmount = 999, dayOffset = 0, defaultHour = 20),
    SLEEP(maxAmount = TrackerRules.MINUTES_PER_DAY - 1, dayOffset = 1, defaultHour = 9),
}

/** One logged day. [target] is the target that applied that day (grams, or minutes of sleep). */
public data class DailyAmount(val date: LocalDate, val amount: Int, val target: Int) {
    /** A day succeeds only when the amount meets or beats that day's target. */
    public val met: Boolean get() = amount >= target
}

/**
 * The tracker rules:
 * - one amount per day: saving again replaces that day's amount;
 * - only the newest day and the one before can be logged or corrected; older days are a record;
 * - a day with no entry is never a success;
 * - "today" follows the app's rollover hour, like habits.
 *
 * Example (sleep): today Sat 21 Nov. Last night (Fri 20) and the night before (Thu 19) can be logged;
 * Wed 18 cannot, and Sat 21 (tonight) has not happened yet.
 */
public object TrackerRules {
    public const val MINUTES_PER_DAY: Int = 24 * 60
    public const val MINUTES_PER_HOUR: Int = 60
    public const val MIN_TARGET: Int = 1

    /** The newest day that can be logged: today for protein, last night (yesterday) for sleep. */
    public fun latestDay(kind: TrackerKind, today: LocalDate): LocalDate = today.minusDays(kind.dayOffset)

    public fun canEdit(date: LocalDate, latest: LocalDate): Boolean = date == latest || date == latest.minusDays(1)

    public fun validAmount(kind: TrackerKind, amount: Int): Boolean = amount in 0..kind.maxAmount

    public fun validTarget(kind: TrackerKind, target: Int): Boolean = target in MIN_TARGET..kind.maxAmount

    /** Whole grams from 0 to 999; anything else (empty, letters, too big) is null. */
    public fun parseGrams(text: String): Int? = text.trim().toIntOrNull()?.takeIf { validAmount(TrackerKind.PROTEIN, it) }

    /**
     * Hours (0 to 23) and minutes (0 to 59) as minutes of sleep. One box may be empty (it counts as 0),
     * not both. Example: "7" and "30" = 450.
     */
    public fun parseDuration(hours: String, minutes: String): Int? {
        if (hours.isBlank() && minutes.isBlank()) return null
        val h = if (hours.isBlank()) 0 else hours.trim().toIntOrNull() ?: return null
        val m = if (minutes.isBlank()) 0 else minutes.trim().toIntOrNull() ?: return null
        if (h !in 0 until HOURS_PER_DAY || m !in 0 until MINUTES_PER_HOUR) return null
        return h * MINUTES_PER_HOUR + m
    }

    /**
     * The first day a tracker counts from: the day the goal was set, or an earlier logged day. Days
     * before it are greyed, not missed. Null when there is no goal and no entry yet.
     */
    public fun firstDay(goalSetOn: LocalDate?, days: List<DailyAmount>): LocalDate? =
        listOfNotNull(goalSetOn, days.minOfOrNull { it.date }).minOrNull()

    /** The days as a habit history (met days are DONE), so habit streaks and grids work unchanged. */
    public fun history(days: List<DailyAmount>, firstDay: LocalDate): HabitHistory =
        HabitHistory(firstDay, days.filter { it.met }.associate { it.date to HabitLogStatus.DONE })

    private const val HOURS_PER_DAY = 24
}
