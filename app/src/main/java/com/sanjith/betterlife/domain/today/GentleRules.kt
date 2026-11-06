package com.sanjith.betterlife.domain.today

import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.PausePeriod
import com.sanjith.betterlife.domain.time.habitDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A habit as the gentle features see it today. */
public data class GentleHabit(
    val id: String,
    val name: String,
    val essential: Boolean,
    val selfCare: Boolean,
    val currentStreak: Int,
    /** Done, kept or paused today: nothing more to do. */
    val settledToday: Boolean,
)

/** Minimum Day's three things; any of them may be missing when the data has none. */
public data class MinimumDayPick(val taskId: String?, val habitId: String?, val selfCareId: String?) {
    val count: Int get() = listOfNotNull(taskId, habitId, selfCareId).size
}

/**
 * Minimum Day (context.md 5.4, Prompt 17): one tap picks three things.
 * - the most important task: the first task on the Today ranking;
 * - one habit: Essential first, else the one with the longest streak at risk (not done today);
 * - one Self-care habit that is not the habit above.
 * Habits already settled today are only picked when nothing else qualifies.
 */
public object MinimumDay {
    public fun pick(topTaskId: String?, habits: List<GentleHabit>): MinimumDayPick {
        val open = habits.filter { !it.settledToday }
        val habit = open.firstOrNull { it.essential }
            ?: open.filter { it.currentStreak > 0 }.maxByOrNull { it.currentStreak }
            ?: open.firstOrNull()
            ?: habits.firstOrNull { it.essential }
            ?: habits.firstOrNull()
        val selfCare = (open + habits).firstOrNull { it.selfCare && it.id != habit?.id }
        return MinimumDayPick(topTaskId, habit?.id, selfCare?.id)
    }
}

/** Why comeback mode opened. */
public enum class ComebackTrigger { MISSED_HABIT_DAYS, AWAY_FROM_APP }

/**
 * Comeback mode (context.md 5.4): after 3 or more missed days in a row on a habit (paused days do
 * not count and do not end the run), or 2 or more days without opening the app. Shown at most once
 * a day. It never says which days were missed.
 */
public object Comeback {
    public const val MISSED_DAYS: Int = 3
    public const val AWAY_DAYS: Long = 2

    public fun trigger(
        today: LocalDate,
        lastOpened: LocalDate?,
        shownOn: LocalDate?,
        habits: List<HabitHistory>,
    ): ComebackTrigger? {
        if (shownOn == today) return null
        if (lastOpened != null && ChronoUnit.DAYS.between(lastOpened, today) >= AWAY_DAYS) return ComebackTrigger.AWAY_FROM_APP
        if (habits.any { missedRun(it, today) >= MISSED_DAYS }) return ComebackTrigger.MISSED_HABIT_DAYS
        return null
    }

    /** Missed days in a row ending yesterday (today is still pending). */
    public fun missedRun(history: HabitHistory, today: LocalDate): Int {
        var count = 0
        var day = today.minusDays(1)
        while (true) {
            when (history.state(day, today)) {
                DayState.MISSED -> count++
                DayState.PAUSED -> Unit
                else -> return count
            }
            day = day.minusDays(1)
        }
    }

    /** The mission: two habits still to do (Essential first) and the most important task. */
    public fun mission(topTaskId: String?, habits: List<GentleHabit>): List<String> {
        val open = habits.filter { !it.settledToday }.sortedByDescending { it.essential }
        return open.take(2).map { it.id } + listOfNotNull(topTaskId)
    }
}

/** The evening nudge (Prompt 17): only when a habit is still not done, kept or paused today. */
public object HabitNudge {
    public fun shouldNudge(habits: List<GentleHabit>): Boolean = habits.any { !it.settledToday }
}

/**
 * Habit pause ranges and reminders (Prompt 17): while today's habit day is inside a range, the
 * engine treats it like "I'm busy" until the range ends (the rollover hour after its last day). An
 * open range is re-checked every day.
 */
public object PauseRangeRules {
    public fun activeUntil(ranges: List<PausePeriod>, now: Instant, zone: ZoneId, rolloverHour: Int): Instant? {
        val day = habitDay(now, zone, rolloverHour)
        val active = ranges.filter { it.contains(day) }
        if (active.isEmpty()) return null
        val lastDay = if (active.any { it.end == null }) day else active.maxOf { it.end!! }
        return lastDay.plusDays(1).atTime(rolloverHour, 0).atZone(zone).toInstant()
    }
}
