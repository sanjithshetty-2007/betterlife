package com.sanjith.betterlife.domain.habit

import java.time.LocalDate

/** A habit's group on the dashboard (context.md 5.4). */
public enum class HabitCategory { HEALTH, SELF_CARE, OTHER }

/**
 * What a habit log says about one habit day. DONE and KEPT (a Minimum Day) count; PAUSED neither
 * counts nor breaks a streak. A day with no log is simply not done.
 */
public enum class HabitLogStatus { DONE, KEPT, PAUSED }

/** Why habits were paused for a date range (pause / vacation / sick mode). */
public enum class PauseReason { PAUSE, VACATION, SICK }

/** How one day looks for one habit, after logs, pause ranges and the start date are combined. */
public enum class DayState {
    DONE,
    KEPT,
    PAUSED,

    /** Not done. For today it only means "still pending": today never breaks a streak. */
    MISSED,

    /** Before the habit existed: ends a streak walk and is left out of consistency. */
    BEFORE_START,

    /** After today: cannot be edited and is never counted. */
    FUTURE,
    ;

    /** Whether the day adds to a streak and to consistency. */
    public val counts: Boolean get() = this == DONE || this == KEPT
}

/** A pause range in habit days, both ends included. [end] null = still going. */
public data class PausePeriod(val start: LocalDate, val end: LocalDate?) {
    public fun contains(date: LocalDate): Boolean = !date.isBefore(start) && (end == null || !date.isAfter(end))
}

/**
 * Everything the streak and consistency rules need about one habit. Streaks and consistency are
 * always derived from this, never stored (context.md 5.4).
 *
 * Example: start 2026-11-01, logs {11-01 DONE, 11-03 KEPT}, no pauses: 11-02 is MISSED, 10-31 is
 * BEFORE_START, and the day after today is FUTURE.
 */
public class HabitHistory(
    public val startDate: LocalDate,
    private val logs: Map<LocalDate, HabitLogStatus>,
    private val pauses: List<PausePeriod> = emptyList(),
) {
    /** The first day worth looking at: the start date, or an earlier logged day. */
    public val firstDay: LocalDate = logs.keys.minOrNull()?.takeIf { it.isBefore(startDate) } ?: startDate

    public fun state(date: LocalDate, today: LocalDate): DayState = when {
        date.isAfter(today) -> DayState.FUTURE
        else -> when (logs[date]) {
            HabitLogStatus.DONE -> DayState.DONE
            HabitLogStatus.KEPT -> DayState.KEPT
            HabitLogStatus.PAUSED -> DayState.PAUSED
            null -> when {
                date.isBefore(startDate) -> DayState.BEFORE_START
                pauses.any { it.contains(date) } -> DayState.PAUSED
                else -> DayState.MISSED
            }
        }
    }
}

/**
 * Only today's habit day can be ticked or cleared (owner's change, 2026-11-08): past days are a record
 * and future days have not happened. "Today" follows the rollover hour, so at 01:00 last night's day
 * can still be ticked.
 */
public object HabitEditRules {
    public fun canEdit(date: LocalDate, today: LocalDate): Boolean = date == today

    /** A day before today. No tick on the phone can do this now; a milestone from such a log (a pull in S3) is quiet. */
    public fun isBackfill(date: LocalDate, today: LocalDate): Boolean = date.isBefore(today)
}
