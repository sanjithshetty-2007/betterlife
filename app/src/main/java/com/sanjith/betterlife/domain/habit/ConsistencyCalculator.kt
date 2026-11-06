package com.sanjith.betterlife.domain.habit

import java.time.LocalDate
import kotlin.math.roundToInt

/** A habit's consistency: "New" until there are 7 days of data, then a percentage. */
public sealed interface Consistency {
    public data object New : Consistency

    /** [completed] of [scheduled] habit days in the window. */
    public data class Percent(val percent: Int, val completed: Int, val scheduled: Int) : Consistency
}

/**
 * Consistency % = counted days / scheduled days over the last 30 days (today included), where
 * paused days and days before the habit's start date are not scheduled. Today counts only once it
 * is done, so an unticked morning never lowers the number (context.md 5.4).
 *
 * Example: started 20 days ago, 15 done, 2 paused, today not done yet: 15 / 17 = 88 %.
 */
public object ConsistencyCalculator {
    public const val WINDOW_DAYS: Int = 30
    public const val MIN_DAYS_OF_DATA: Int = 7
    private const val PERCENT = 100.0

    public fun calculate(history: HabitHistory, today: LocalDate): Consistency {
        var scheduled = 0
        var completed = 0
        for (back in 0 until WINDOW_DAYS) {
            val day = today.minusDays(back.toLong())
            when (history.state(day, today)) {
                DayState.DONE, DayState.KEPT -> {
                    scheduled++
                    completed++
                }
                DayState.MISSED -> if (day != today) scheduled++
                DayState.PAUSED, DayState.BEFORE_START, DayState.FUTURE -> Unit
            }
        }
        if (scheduled < MIN_DAYS_OF_DATA) return Consistency.New
        return Consistency.Percent((completed * PERCENT / scheduled).roundToInt(), completed, scheduled)
    }
}
