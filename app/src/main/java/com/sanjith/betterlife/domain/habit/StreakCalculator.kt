package com.sanjith.betterlife.domain.habit

import java.time.LocalDate

/** A run of counted days: [start] and [end] are the first and last counted days, [length] counts them. */
public data class StreakRun(val start: LocalDate, val end: LocalDate, val length: Int)

/** The streak numbers a habit card shows. A broken streak shows "Best: N. Restarting." never a bare 0. */
public data class StreakSummary(val current: Int, val best: Int) {
    public val restarting: Boolean get() = current == 0 && best > 0
}

/**
 * Streak rules (context.md 5.4):
 * - a streak is the consecutive counted days (DONE or KEPT) ending today or yesterday;
 * - today still pending does not break it;
 * - PAUSED days bridge the gap but add nothing;
 * - the best streak is the longest run ever, kept after a break.
 *
 * Example: DONE 1..7 Nov, today 8 Nov not ticked yet: current = 7. Today 9 Nov: current = 0, best = 7.
 */
public object StreakCalculator {

    public fun summary(history: HabitHistory, today: LocalDate): StreakSummary =
        StreakSummary(current(history, today), best(history, today))

    public fun current(history: HabitHistory, today: LocalDate): Int {
        // Today counts if done; otherwise it is pending or paused and is simply skipped.
        var count = if (history.state(today, today).counts) 1 else 0
        var day = today.minusDays(1)
        while (true) {
            when (history.state(day, today)) {
                DayState.DONE, DayState.KEPT -> count++
                DayState.PAUSED -> Unit
                else -> return count
            }
            day = day.minusDays(1)
        }
    }

    public fun best(history: HabitHistory, today: LocalDate): Int {
        var best = 0
        var run = 0
        var day = history.firstDay
        while (!day.isAfter(today)) {
            when (history.state(day, today)) {
                DayState.DONE, DayState.KEPT -> {
                    run++
                    if (run > best) best = run
                }
                DayState.PAUSED -> Unit
                // Today not done yet is pending, not a break (and it is the last day anyway).
                DayState.MISSED -> if (day != today) run = 0
                DayState.BEFORE_START, DayState.FUTURE -> run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    /**
     * The run that [date] belongs to, or null when [date] is not a counted day. Paused days inside
     * the run are bridged; the run never reaches past [today].
     */
    public fun runAround(history: HabitHistory, date: LocalDate, today: LocalDate): StreakRun? {
        if (!history.state(date, today).counts) return null
        var start = date
        var end = date
        var length = 1
        var day = date.minusDays(1)
        while (true) {
            val state = history.state(day, today)
            if (state.counts) {
                length++
                start = day
            } else if (state != DayState.PAUSED) {
                break
            }
            day = day.minusDays(1)
        }
        day = date.plusDays(1)
        while (!day.isAfter(today)) {
            val state = history.state(day, today)
            if (state.counts) {
                length++
                end = day
            } else if (state != DayState.PAUSED) {
                break
            }
            day = day.plusDays(1)
        }
        return StreakRun(start, end, length)
    }
}
