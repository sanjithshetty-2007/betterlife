package com.sanjith.betterlife.domain.widget

import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.ConsistencyCalculator
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.time.habitDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One active habit for the widget, in the owner's order. */
public data class WidgetHabit(val id: String, val name: String, val colorHex: String, val history: HabitHistory)

/**
 * One box. [name] is null when details are hidden (the widget then says "Habit [number]").
 * [done] shows the tick (DONE or KEPT); a paused habit is settled but cannot be tapped.
 */
public data class WidgetBox(
    val habitId: String,
    val number: Int,
    val name: String?,
    val colorHex: String,
    val done: Boolean,
    val tappable: Boolean,
)

/** What the habit widget shows (Prompt 19). */
public data class HabitWidgetModel(
    val today: LocalDate,
    val consistency: Consistency,
    val boxes: List<WidgetBox>,
    /** Habits beyond the cap: a "+N more" box that opens the app. */
    val more: Int,
    val settled: Int,
    val total: Int,
    val detailsHidden: Boolean,
) {
    val isEmpty: Boolean get() = total == 0
}

/**
 * Builds the habit widget's content. The habit day follows the rollover hour, so at 01:00 the
 * widget still shows (and ticks) the previous day, exactly as the Habits screen does.
 *
 * Example: 6 habits, cap 4, 2 done -> 3 boxes and a "+3 more" box, "2 of 6 done"; details hidden -> no names.
 */
public object HabitWidgetBuilder {
    /** 2x2: two rows of two boxes. */
    public const val COMPACT_CAP: Int = 4

    /** 4x2: one row of up to six boxes, each a 48 dp target, in about 330 dp of width. */
    public const val WIDE_CAP: Int = 6

    public fun build(habits: List<WidgetHabit>, now: Instant, zone: ZoneId, rolloverHour: Int, hideDetails: Boolean, cap: Int): HabitWidgetModel {
        require(cap > 0) { "cap must be positive" }
        val today = habitDay(now, zone, rolloverHour)
        val states = habits.map { it to it.history.state(today, today) }
        // With more habits than room, the last box becomes "+N more", so only cap - 1 habits show.
        val shown = if (habits.size > cap) cap - 1 else cap
        val boxes = states.take(shown).mapIndexed { index, (habit, state) ->
            WidgetBox(
                habitId = habit.id,
                number = index + 1,
                name = if (hideDetails) null else habit.name,
                colorHex = habit.colorHex,
                done = state == DayState.DONE || state == DayState.KEPT,
                tappable = state != DayState.PAUSED,
            )
        }
        return HabitWidgetModel(
            today = today,
            consistency = ConsistencyCalculator.overall(habits.map { it.history }, today),
            boxes = boxes,
            more = habits.size - boxes.size,
            settled = states.count { (_, s) -> s == DayState.DONE || s == DayState.KEPT || s == DayState.PAUSED },
            total = habits.size,
            detailsHidden = hideDetails,
        )
    }
}
