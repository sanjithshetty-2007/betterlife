package com.sanjith.betterlife.domain.protein

import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import java.time.LocalDate

/** One logged day. [targetGrams] is the target that applied that day. */
public data class ProteinDay(val date: LocalDate, val grams: Int, val targetGrams: Int) {
    /** A day succeeds only when the amount meets or beats that day's target. */
    public val met: Boolean get() = grams >= targetGrams
}

/**
 * The protein rules (owner's feature, 2026-11-21):
 * - one amount per day: saving again replaces that day's amount;
 * - only today and yesterday can be logged or corrected; older days are a record;
 * - a day with no entry is never a success;
 * - "today" follows the app's rollover hour, like habits (logging at 01:00 counts for the day before).
 *
 * Example: today 21 Nov. 21 Nov and 20 Nov can be edited; 19 Nov cannot.
 */
public object ProteinRules {
    public const val MAX_GRAMS: Int = 999
    public const val MIN_TARGET: Int = 1

    public fun canEdit(date: LocalDate, today: LocalDate): Boolean = date == today || date == today.minusDays(1)

    /** Whole grams from 0 to [MAX_GRAMS]; anything else (empty, letters, too big) is null. */
    public fun parseGrams(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_GRAMS }

    /** A target from [MIN_TARGET] to [MAX_GRAMS] grams. */
    public fun parseTarget(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN_TARGET..MAX_GRAMS }

    /**
     * The first day protein counts from: the day the goal was set, or an earlier logged day. Days
     * before it are greyed, not missed. Null when there is no goal and no entry yet.
     */
    public fun firstDay(goalSetOn: LocalDate?, days: List<ProteinDay>): LocalDate? =
        listOfNotNull(goalSetOn, days.minOfOrNull { it.date }).minOrNull()

    /** The days as a habit history (met days are DONE), so habit streaks and grids work unchanged. */
    public fun history(days: List<ProteinDay>, firstDay: LocalDate): HabitHistory =
        HabitHistory(firstDay, days.filter { it.met }.associate { it.date to HabitLogStatus.DONE })
}
