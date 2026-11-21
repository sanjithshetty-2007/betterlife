package com.sanjith.betterlife.domain.tracker

import com.sanjith.betterlife.domain.habit.StreakCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Average and goal days for a week or month. [countedDays] are the days averaged: every calendar day
 * from the period's start (or the first protein day, if later) through yesterday, plus today once
 * logged. A counted day with no entry counts as 0. [average] is null when no day is counted.
 * For sleep, "today" is the newest loggable night (TrackerRules.latestDay).
 */
public data class PeriodStats(
    val start: LocalDate,
    val end: LocalDate,
    val countedDays: Int,
    val average: Int?,
    val goalDays: Int,
    val days: List<DayBar>,
)

/** One day of a period for the overview: null [amount] = nothing logged; [counted] = part of the average. */
public data class DayBar(val date: LocalDate, val amount: Int?, val target: Int?, val met: Boolean, val counted: Boolean)

public data class TrackerStreaks(val current: Int, val best: Int, val totalSuccessDays: Int)

/**
 * Example: week Mon 16 to Sun 22 Nov, today Thu 19, first day 1 Nov, logged Mon 120, Wed 90 (target
 * 120), today not logged yet: counted Mon to Wed = 3 days, average (120 + 0 + 90) / 3 = 70, goal days 1.
 */
public object TrackerStats {

    public fun week(days: Map<LocalDate, DailyAmount>, today: LocalDate, weekStart: DayOfWeek, firstDay: LocalDate?): PeriodStats {
        val start = today.with(TemporalAdjusters.previousOrSame(weekStart))
        return period(days, start, start.plusDays(DAYS_IN_WEEK - 1), today, firstDay)
    }

    public fun month(days: Map<LocalDate, DailyAmount>, today: LocalDate, firstDay: LocalDate?): PeriodStats {
        val start = today.withDayOfMonth(1)
        return period(days, start, today.with(TemporalAdjusters.lastDayOfMonth()), today, firstDay)
    }

    public fun period(days: Map<LocalDate, DailyAmount>, start: LocalDate, end: LocalDate, today: LocalDate, firstDay: LocalDate?): PeriodStats {
        val bars = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { date ->
            val day = days[date]
            val counted = firstDay != null && !date.isBefore(firstDay) && (date.isBefore(today) || (date == today && day != null))
            DayBar(date, day?.amount, day?.target, day?.met == true, counted)
        }.toList()
        val counted = bars.filter { it.counted }
        val average = if (counted.isEmpty()) null else Math.round(counted.sumOf { it.amount ?: 0 }.toDouble() / counted.size).toInt()
        return PeriodStats(start, end, counted.size, average, counted.count { it.met }, bars)
    }

    /** Current and best streak with the habit streak rules (today still pending never breaks it). */
    public fun streaks(days: List<DailyAmount>, today: LocalDate, firstDay: LocalDate?): TrackerStreaks {
        if (firstDay == null) return TrackerStreaks(0, 0, 0)
        val history = TrackerRules.history(days, firstDay)
        val summary = StreakCalculator.summary(history, today)
        return TrackerStreaks(summary.current, summary.best, days.count { it.met && !it.date.isAfter(today) })
    }

    private const val DAYS_IN_WEEK = 7L
}
