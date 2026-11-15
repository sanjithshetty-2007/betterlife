package com.sanjith.betterlife.domain.widget

import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.task.TaskBucket
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.TaskFacts
import com.sanjith.betterlife.domain.task.TaskRules
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One open task for the task widget. */
public data class WidgetTask(val id: String, val title: String, val due: TaskDue, val priority: Priority, val hasProject: Boolean)

/** One widget row. [title] is null when details are hidden (the widget then says "Task [number]"). */
public data class TaskWidgetRow(val taskId: String, val number: Int, val title: String?, val due: TaskDue, val overdue: Boolean, val priority: Priority)

/** What the task widget shows (Prompt 20): today's and overdue open tasks. */
public data class TaskWidgetModel(val today: LocalDate, val rows: List<TaskWidgetRow>, val detailsHidden: Boolean) {
    val isEmpty: Boolean get() = rows.isEmpty()
    val overdue: Int get() = rows.count { it.overdue }
}

/**
 * Builds the task widget's list with the Tasks screen's rules (TaskRules.bucket): overdue tasks
 * first, oldest first, then today's, timed by time and whole-day ones after them; higher priority
 * first at the same time. Upcoming and undated tasks are left to the app.
 *
 * Example: an overdue task from yesterday, today 17:00 High, today 09:00, today all day -> yesterday's,
 * 09:00, 17:00, all day.
 */
public object TaskWidgetBuilder {

    public fun build(tasks: List<WidgetTask>, now: ZonedDateTime, hideDetails: Boolean): TaskWidgetModel {
        val placed = tasks.mapNotNull { task ->
            when (TaskRules.bucket(TaskFacts(task.due, task.hasProject, completed = false, recurrence = null), now)) {
                TaskBucket.OVERDUE -> task to true
                TaskBucket.TODAY -> task to false
                else -> null
            }
        }
        val ordered = placed.sortedWith(
            compareBy<Pair<WidgetTask, Boolean>>(
                { !it.second },
                { dayOf(it.first.due, now.zone) },
                { secondOfDay(it.first.due, now.zone) },
                { -it.first.priority.ordinal },
                { it.first.title.lowercase() },
            ),
        )
        return TaskWidgetModel(
            today = now.toLocalDate(),
            rows = ordered.mapIndexed { index, (task, overdue) ->
                TaskWidgetRow(task.id, index + 1, if (hideDetails) null else task.title, task.due, overdue, task.priority)
            },
            detailsHidden = hideDetails,
        )
    }

    private fun dayOf(due: TaskDue, zone: ZoneId): LocalDate = when (due) {
        is TaskDue.At -> due.instant.atZone(zone).toLocalDate()
        is TaskDue.OnDay -> due.date
        TaskDue.None -> LocalDate.MAX
    }

    // A whole-day task sorts after every timed task of its day.
    private fun secondOfDay(due: TaskDue, zone: ZoneId): Int = when (due) {
        is TaskDue.At -> due.instant.atZone(zone).toLocalTime().toSecondOfDay()
        else -> Int.MAX_VALUE
    }
}
