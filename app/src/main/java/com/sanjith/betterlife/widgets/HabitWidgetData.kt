package com.sanjith.betterlife.widgets

import com.sanjith.betterlife.data.repository.HabitEditResult
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.widget.WidgetHabit
import com.sanjith.betterlife.feature.habits.HabitService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

/** Everything the habit widget needs, read from the database and settings (Prompt 19). */
public data class HabitWidgetInput(
    val habits: List<WidgetHabit>,
    val now: Instant,
    val zone: ZoneId,
    val rolloverHour: Int,
    val hideDetails: Boolean,
)

/**
 * The widget's data: active habits in the owner's order with their history, and the settings it
 * follows. A Room flow, so any tick (app, widget, reminder Done, sync pull) shows up by itself.
 */
public class HabitWidgetSource(
    private val habits: HabitRepository,
    private val settings: Flow<AppSettings>,
    private val clock: Clock,
) {
    public fun observe(): Flow<HabitWidgetInput> = combine(
        habits.observeActive(),
        habits.observeAllLogs(),
        habits.observePauses(),
        settings,
    ) { active, logs, pauses, s ->
        val byHabit = logs.groupBy { it.habitId }
        HabitWidgetInput(
            habits = active.map { h ->
                WidgetHabit(h.id, h.name, h.colorHex, HabitHistory(h.startDate, byHabit[h.id].orEmpty().associate { it.localDate to it.status }, pauses))
            },
            now = clock.now(),
            zone = clock.zone,
            rolloverHour = s.rolloverHour,
            hideDetails = s.hideWidgetDetails,
        )
    }
}

/**
 * A tap on a widget box: today's box is ticked, or cleared when it is already done. It goes through
 * the same HabitService as the app (one transaction with the milestone check, and the celebration
 * through the existing queue), so the widget can never disagree with the Habits screen.
 */
public class HabitWidgetToggle(private val service: HabitService, private val habits: HabitRepository) {
    public suspend fun toggle(habitId: String): HabitEditResult {
        val today = service.today()
        val state = habits.history(habitId)?.state(today, today) ?: return HabitEditResult.UnknownHabit
        return when (state) {
            DayState.DONE, DayState.KEPT -> service.untick(habitId, today)
            // A paused day is settled by the pause range; the box is not tappable, so nothing to do.
            DayState.PAUSED -> HabitEditResult.Saved()
            else -> service.tick(habitId, today)
        }
    }
}
