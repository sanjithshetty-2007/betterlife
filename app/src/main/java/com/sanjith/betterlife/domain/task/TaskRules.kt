package com.sanjith.betterlife.domain.task

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.recurrence.RecurrenceEngine
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RepeatMode
import com.sanjith.betterlife.domain.reminder.ReminderOffsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Which list a task appears in (context.md section 5.3). */
public enum class TaskBucket { OVERDUE, TODAY, UPCOMING, INBOX, PROJECT_NO_DATE, DONE }

/** When a task is due: at an exact moment, on a whole day, or not at all. */
public sealed interface TaskDue {
    public data class At(val instant: Instant, val zone: ZoneId) : TaskDue
    public data class OnDay(val date: LocalDate) : TaskDue
    public data object None : TaskDue
}

/** The parts of a task the list rules need. */
public data class TaskFacts(
    val due: TaskDue,
    val hasProject: Boolean,
    val completed: Boolean,
    val recurrence: RecurrenceRule?,
)

/** A new reminder for a task: offset in minutes before the due time, and its level. */
public data class ReminderPlan(val offsetMinutes: Int, val importance: Importance)

public object TaskRules {

    /**
     * Example (now Thu 2026-10-08 18:00): due today 17:00 -> OVERDUE; due today 21:00 -> TODAY;
     * due Fri -> UPCOMING; no date, no project -> INBOX.
     */
    public fun bucket(task: TaskFacts, now: ZonedDateTime): TaskBucket {
        if (task.completed) return TaskBucket.DONE
        val today = now.toLocalDate()
        return when (val due = task.due) {
            is TaskDue.At -> when {
                due.instant.isBefore(now.toInstant()) -> TaskBucket.OVERDUE
                due.instant.atZone(now.zone).toLocalDate() == today -> TaskBucket.TODAY
                else -> TaskBucket.UPCOMING
            }
            is TaskDue.OnDay -> when {
                due.date.isBefore(today) -> TaskBucket.OVERDUE
                due.date == today -> TaskBucket.TODAY
                else -> TaskBucket.UPCOMING
            }
            TaskDue.None -> if (task.hasProject) TaskBucket.PROJECT_NO_DATE else TaskBucket.INBOX
        }
    }

    /**
     * The date to show for an overdue repeating task: one instance only, which moves forward once it
     * has been ignored for 30 days (context.md 5.3). Non-repeating tasks keep their date.
     */
    public fun displayDate(task: TaskFacts, today: LocalDate, zone: ZoneId): LocalDate? {
        val date = dueDate(task.due, zone) ?: return null
        val rule = task.recurrence ?: return date
        return if (date.isBefore(today)) RecurrenceEngine.overdueInstanceDue(rule, date, date, today) else date
    }

    /**
     * Due of the next instance when a repeating task is completed on [completedOn]: same time of day,
     * next date by the rule. Null for tasks that do not repeat or have no date.
     *
     * Example: daily task due Thu 18:00, completed Thu -> Fri 18:00.
     */
    public fun nextInstanceDue(due: TaskDue, rule: RecurrenceRule?, completedOn: LocalDate, mode: RepeatMode): TaskDue? {
        rule ?: return null
        return when (due) {
            is TaskDue.At -> {
                val local = due.instant.atZone(due.zone)
                val next = RecurrenceEngine.nextDue(rule, local.toLocalDate(), local.toLocalDate(), completedOn, mode)
                TaskDue.At(next.atTime(local.toLocalTime()).atZone(due.zone).toInstant(), due.zone)
            }
            is TaskDue.OnDay -> TaskDue.OnDay(RecurrenceEngine.nextDue(rule, due.date, due.date, completedOn, mode))
            TaskDue.None -> null
        }
    }

    /**
     * Reminders a new task gets unless the owner changes them (owner-editable later): a timed task
     * gets "1 day before" as a notification and "at the time" as a pop-up; a whole-day task gets one
     * notification on the day (at the all-day time, 09:00); an undated task gets none.
     */
    public fun defaultReminders(due: TaskDue): List<ReminderPlan> = when (due) {
        is TaskDue.At -> listOf(
            ReminderPlan(ReminderOffsets.DAY_1, Importance.NORMAL),
            ReminderPlan(ReminderOffsets.AT_TIME, Importance.FULL_SCREEN),
        )
        is TaskDue.OnDay -> listOf(ReminderPlan(ReminderOffsets.AT_TIME, Importance.NORMAL))
        TaskDue.None -> emptyList()
    }

    /** Higher priority first, then earlier due; undated last. Used by Today and Overdue. */
    public val PRIORITY_ORDER: List<Priority> = listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW, Priority.NONE)

    private fun dueDate(due: TaskDue, zone: ZoneId): LocalDate? = when (due) {
        is TaskDue.At -> due.instant.atZone(zone).toLocalDate()
        is TaskDue.OnDay -> due.date
        TaskDue.None -> null
    }
}
