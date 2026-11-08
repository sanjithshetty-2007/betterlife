package com.sanjith.betterlife.feature.habits

import androidx.compose.runtime.Immutable
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitHistory
import java.time.LocalDate
import java.time.YearMonth

/** How one of the 31 boxes looks and whether it can be tapped. */
public enum class BoxState {
    DONE,
    KEPT,
    PAUSED,

    /** A past day or today, not ticked. */
    OPEN,

    /** After today. */
    FUTURE,

    /** Day 29 to 31 in a shorter month: greyed. */
    NO_SUCH_DAY,

    /** Before the habit started: greyed. */
    BEFORE_START,
    ;

    public val filled: Boolean get() = this == DONE || this == KEPT
}

/** One box: [day] 1 to 31, its date (null for a day the month does not have) and state. */
@Immutable
public data class GridBox(val day: Int, val date: LocalDate?, val state: BoxState, val isToday: Boolean) {
    /** Only today's box can be ticked or cleared; past days are a record (owner, 2026-11-08). */
    public val tappable: Boolean get() = isToday
}

/**
 * The 31-box month grid (Prompt 15): always 31 boxes, NOT a labelled calendar. Box N is day N of the
 * month; days the month does not have and days before the habit started are greyed.
 *
 * Example: February 2026, habit started 10 Feb, today 20 Feb: boxes 1 to 9 BEFORE_START, 21 to 28
 * FUTURE, 29 to 31 NO_SUCH_DAY.
 */
public object HabitGridModel {
    public const val BOXES: Int = 31

    /** Boxes per row: 7 keeps every box a 48 dp touch target on a phone. */
    public const val COLUMNS: Int = 7

    public fun boxes(history: HabitHistory, month: YearMonth, today: LocalDate): List<GridBox> =
        (1..BOXES).map { day ->
            if (day > month.lengthOfMonth()) return@map GridBox(day, null, BoxState.NO_SUCH_DAY, isToday = false)
            val date = month.atDay(day)
            val state = when (history.state(date, today)) {
                DayState.DONE -> BoxState.DONE
                DayState.KEPT -> BoxState.KEPT
                DayState.PAUSED -> BoxState.PAUSED
                DayState.MISSED -> BoxState.OPEN
                DayState.FUTURE -> BoxState.FUTURE
                DayState.BEFORE_START -> BoxState.BEFORE_START
            }
            GridBox(day, date, state, isToday = date == today)
        }

    /** How many days of [month] were done or kept ("27 of 31 days" on a past month). */
    public fun doneCount(boxes: List<GridBox>): Int = boxes.count { it.state.filled }
}
