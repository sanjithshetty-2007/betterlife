package com.sanjith.betterlife.feature.habits

import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.habit.CelebrationCenter
import com.sanjith.betterlife.data.habit.CelebrationEvent
import com.sanjith.betterlife.domain.habit.CelebrationKind
import com.sanjith.betterlife.data.repository.HabitDraft
import com.sanjith.betterlife.data.repository.HabitEditResult
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import java.time.LocalDate

/**
 * Every habit action the screens offer. Changes to a habit itself are followed by an alarm
 * reschedule, so its daily reminder always matches; ticks are not (a tick only changes whether the
 * next reminder is needed, which the engine checks when it fires). A tick that reaches a milestone
 * hands it to [celebrations] (full screen, or a quiet banner after a backfill).
 */
public class HabitService(
    private val habits: HabitRepository,
    private val habitToday: suspend () -> LocalDate,
    private val reschedule: suspend () -> Unit,
    private val celebrations: CelebrationCenter? = null,
) {
    public suspend fun today(): LocalDate = habitToday()

    public suspend fun tick(habitId: String, date: LocalDate, status: HabitLogStatus = HabitLogStatus.DONE): HabitEditResult {
        val result = habits.tick(habitId, date, habitToday(), status)
        val milestone = (result as? HabitEditResult.Saved)?.milestone
        if (milestone != null && celebrations != null) {
            val name = habits.getAnyById(habitId)?.name.orEmpty()
            celebrations.publish(CelebrationEvent(habitId, name, milestone.milestone, quiet = milestone.kind == CelebrationKind.QUIET))
        }
        return result
    }

    public suspend fun untick(habitId: String, date: LocalDate): HabitEditResult = habits.untick(habitId, date, habitToday())

    public suspend fun create(draft: HabitDraft): String {
        val id = habits.create(draft, habitToday())
        reschedule()
        return id
    }

    public suspend fun update(habit: HabitEntity) {
        habits.update(habit)
        reschedule()
    }

    public suspend fun setArchived(id: String, archived: Boolean) {
        habits.setArchived(id, archived)
        reschedule()
    }

    public suspend fun delete(id: String) {
        habits.softDelete(id)
        reschedule()
    }

    public suspend fun restore(id: String) {
        habits.restore(id)
        reschedule()
    }
}
