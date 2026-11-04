package com.sanjith.betterlife.domain.task

import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.parser.QuickAddResult
import java.time.ZoneId

/** What quick-add saves, before ids and project lookup. */
public data class QuickAddTask(
    val title: String,
    val due: TaskDue,
    val priority: Priority,
    val projectName: String?,
    val reminders: List<ReminderPlan>,
)

/**
 * Turns a parsed quick-add line into a task. Tags have no table until Prompt 21, so they stay in the
 * title as "#tag" words: nothing typed is lost, and Prompt 21 moves them into real tags.
 *
 * Example: "report 17:30 #work @projectx" -> title "report #work", due tomorrow 17:30, project "projectx".
 */
public fun QuickAddResult.toQuickAddTask(zone: ZoneId): QuickAddTask {
    require(isReadyToSave) { "Quick-add text is not ready to save (no title or an open AM/PM question)" }
    val due = when {
        date != null && time != null -> TaskDue.At(date.atTime(time).atZone(zone).toInstant(), zone)
        date != null -> TaskDue.OnDay(date)
        else -> TaskDue.None
    }
    val fullTitle = (listOf(title) + tags.map { "#$it" }).joinToString(" ")
    return QuickAddTask(fullTitle, due, priority, project, TaskRules.defaultReminders(due))
}
