package com.sanjith.betterlife.feature.tasks

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.domain.task.TaskDue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The repeat choices the editor offers; the details come from the due date. */
public enum class RepeatKind { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

public enum class EditorError { EMPTY_TITLE, REPEAT_NEEDS_DATE }

/** A subtask line in the editor (subtasks are tasks with a parent). */
public data class SubtaskUi(val id: String, val title: String, val done: Boolean)

/** Everything the task editor shows and edits. [id] is null for a new task. */
public data class TaskEditorState(
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.NONE,
    val projectId: String? = null,
    val category: String = "",
    val repeat: RepeatKind = RepeatKind.NONE,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val repeatFromCompletion: Boolean = false,
    val importance: Importance = Importance.NORMAL,
    val reminders: List<ReminderPlan> = emptyList(),
    val subtasks: List<SubtaskUi> = emptyList(),
) {
    public fun due(zone: ZoneId): TaskDue = when {
        dueDate != null && dueTime != null -> TaskDue.At(dueDate.atTime(dueTime).atZone(zone).toInstant(), zone)
        dueDate != null -> TaskDue.OnDay(dueDate)
        else -> TaskDue.None
    }

    public fun errors(): List<EditorError> = listOfNotNull(
        EditorError.EMPTY_TITLE.takeIf { title.isBlank() },
        EditorError.REPEAT_NEEDS_DATE.takeIf { repeat != RepeatKind.NONE && dueDate == null },
    )

    /** The rule for the chosen repeat, anchored on the due date (see [repeatRule]). */
    public fun rule(): RecurrenceRule? = repeatRule(repeat, interval, weekdays, dueDate)

    public companion object {
        /** The editor fields for a stored rule. */
        public fun repeatFields(rule: RecurrenceRule?): Triple<RepeatKind, Int, Set<DayOfWeek>> = when (rule) {
            null -> Triple(RepeatKind.NONE, 1, emptySet())
            is RecurrenceRule.Daily -> Triple(RepeatKind.DAILY, rule.interval, emptySet())
            RecurrenceRule.Weekdays -> Triple(RepeatKind.WEEKDAYS, 1, emptySet())
            is RecurrenceRule.Weekly -> Triple(RepeatKind.WEEKLY, rule.interval, rule.days)
            is RecurrenceRule.Monthly -> Triple(RepeatKind.MONTHLY, rule.interval, emptySet())
            is RecurrenceRule.Yearly -> Triple(RepeatKind.YEARLY, rule.interval, emptySet())
        }
    }
}

/**
 * The rule for a repeat choice, anchored on [date]. Monthly and yearly repeat on [date]'s day (so
 * "the 31st" keeps falling on the last day of short months); weekly uses [weekdays], or [date]'s
 * weekday. Null for no repeat or no date. Shared by the task and event editors.
 */
public fun repeatRule(repeat: RepeatKind, interval: Int, weekdays: Set<DayOfWeek>, date: LocalDate?): RecurrenceRule? {
    date ?: return null
    val every = interval.coerceAtLeast(1)
    return when (repeat) {
        RepeatKind.NONE -> null
        RepeatKind.DAILY -> RecurrenceRule.Daily(every)
        RepeatKind.WEEKDAYS -> RecurrenceRule.Weekdays
        RepeatKind.WEEKLY -> RecurrenceRule.Weekly(weekdays.ifEmpty { setOf(date.dayOfWeek) }, every)
        RepeatKind.MONTHLY -> RecurrenceRule.Monthly(date.dayOfMonth, every)
        RepeatKind.YEARLY -> RecurrenceRule.Yearly(date.month, date.dayOfMonth, every)
    }
}
