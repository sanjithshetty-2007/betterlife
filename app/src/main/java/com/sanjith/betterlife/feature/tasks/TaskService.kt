package com.sanjith.betterlife.feature.tasks

import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.CompletionResult
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.data.repository.withDue
import com.sanjith.betterlife.domain.parser.QuickAddResult
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.toQuickAddTask
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.today

/**
 * Every task action the screens offer, followed by an alarm reschedule so reminders always match the
 * database (agent.md section 4: deleting cancels reminders, restoring re-schedules them).
 * [clock] is the app clock (the Time Machine may move it); [reschedule] uses the real one.
 */
public class TaskService(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val clock: Clock,
    private val defaults: suspend () -> ReminderDefaults = { ReminderDefaults() },
    private val reschedule: suspend () -> Unit,
) {

    /** Saves a parsed quick-add line; an unknown "@project" is created. Returns the new task id. */
    public suspend fun quickAdd(result: QuickAddResult): String {
        val parsed = result.toQuickAddTask(clock.zone, defaults())
        val projectId = parsed.projectName?.let { projects.findOrCreate(it) }
        val id = tasks.create(
            draftFor(parsed.title, parsed.due).copy(priority = parsed.priority, projectId = projectId),
            parsed.reminders.map { ReminderDraft(it.offsetMinutes, it.importance) },
        )
        reschedule()
        return id
    }

    public suspend fun create(draft: TaskDraft, reminders: List<ReminderDraft>): String {
        val id = tasks.create(draft, reminders)
        reschedule()
        return id
    }

    public suspend fun save(task: TaskEntity, reminders: List<ReminderDraft>) {
        tasks.updateWithReminders(task, reminders)
        reschedule()
    }

    /** Completes a task (a repeating one gets its next instance); keep the result for Undo. */
    public suspend fun complete(id: String): CompletionResult {
        val result = tasks.completeWithNext(id, clock.today())
        reschedule()
        return result
    }

    public suspend fun undoComplete(result: CompletionResult) {
        tasks.undoComplete(result)
        reschedule()
    }

    public suspend fun delete(id: String) {
        tasks.softDelete(id)
        reschedule()
    }

    public suspend fun undoDelete(id: String) {
        tasks.restore(id)
        reschedule()
    }

    /** Evening wrap-up: same time of day, one day later; an undated task stays undated. */
    public suspend fun moveToTomorrow(id: String) {
        val task = tasks.getAnyById(id) ?: return
        val moved = when (val due = task.due) {
            is TaskDue.At -> TaskDue.At(due.instant.atZone(due.zone).plusDays(1).toInstant(), due.zone)
            is TaskDue.OnDay -> TaskDue.OnDay(due.date.plusDays(1))
            TaskDue.None -> return
        }
        tasks.updateWithReminders(task.withDue(moved), tasks.activeReminders(id))
        reschedule()
    }

    private fun draftFor(title: String, due: TaskDue): TaskDraft = when (due) {
        is TaskDue.At -> TaskDraft(title, dueAtMillis = due.instant.toEpochMilli(), dueZoneId = due.zone)
        is TaskDue.OnDay -> TaskDraft(title, dueDate = due.date)
        TaskDue.None -> TaskDraft(title)
    }
}
