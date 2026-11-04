package com.sanjith.betterlife.data.repository

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.task.TaskRules
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** What completing a task changed: the task, and the next instance a repeating task got. */
public data class CompletionResult(val taskId: String, val nextInstanceId: String?)

/**
 * Tasks and their reminders. Every change that touches both runs in one transaction, and every write
 * marks the row DIRTY for sync. Callers reschedule alarms afterwards (TaskService does).
 */
public class TaskRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val taskDao = db.taskDao()
    private val reminderDao = db.reminderDao()

    public fun observeActive(): Flow<List<TaskEntity>> = taskDao.observeActive()

    public fun observeById(id: String): Flow<TaskEntity?> = taskDao.observeById(id)

    public fun observeActiveCount(): Flow<Int> = taskDao.observeActiveCount()

    public suspend fun activeIdsInCategory(category: String): List<String> = taskDao.getActiveIdsByCategory(category)

    /** Creates the task and its reminders together; returns the new task id. */
    public suspend fun create(draft: TaskDraft, reminders: List<ReminderDraft> = emptyList()): String {
        val now = nowMillis()
        val task = draft.toEntity(id = newId(), now = now)
        db.withTransaction {
            validateReferences(task)
            taskDao.insert(task)
            reminderDao.insertAll(reminders.map { it.toEntity(newId(), ReminderItemType.TASK, task.id, now) })
        }
        return task.id
    }

    public suspend fun update(task: TaskEntity) {
        db.withTransaction {
            validateReferences(task)
            taskDao.update(task.copy(updatedAt = nowMillis(), syncState = SyncState.DIRTY))
        }
    }

    public suspend fun getAnyById(id: String): TaskEntity? = taskDao.getAnyById(id)

    /** The task's current (not deleted) reminders. */
    public suspend fun activeReminders(taskId: String): List<ReminderDraft> =
        reminderDao.getAllForItemIncludingDeleted(ReminderItemType.TASK, taskId)
            .filter { it.deletedAt == null }
            .map { ReminderDraft(it.offsetMinutes, it.importance) }

    /** Saves an edited task and replaces its reminders, all in one transaction. */
    public suspend fun updateWithReminders(task: TaskEntity, reminders: List<ReminderDraft>) {
        val now = nowMillis()
        db.withTransaction {
            validateReferences(task)
            taskDao.update(task.copy(updatedAt = now, syncState = SyncState.DIRTY))
            reminderDao.softDeleteForItem(ReminderItemType.TASK, task.id, now)
            reminderDao.insertAll(reminders.map { it.toEntity(newId(), ReminderItemType.TASK, task.id, now) })
        }
    }

    /** Marks the task done. Its reminders stop at the next reschedule (the item is no longer active). */
    public suspend fun complete(id: String) {
        taskDao.complete(id, nowMillis())
    }

    /**
     * Completes a task; a repeating task also gets its next instance (with the same reminders) in the
     * same transaction. The result is what [undoComplete] needs to put everything back.
     */
    public suspend fun completeWithNext(id: String, completedOn: LocalDate): CompletionResult = db.withTransaction {
        val task = taskDao.getAnyById(id) ?: return@withTransaction CompletionResult(id, nextInstanceId = null)
        val now = nowMillis()
        taskDao.complete(id, now)
        val nextDue = TaskRules.nextInstanceDue(task.due, task.facts.recurrence, completedOn, task.repeatMode)
            ?: return@withTransaction CompletionResult(id, nextInstanceId = null)
        val next = task.withDue(nextDue).copy(
            id = newId(),
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.DIRTY,
            serverUpdatedAt = null,
        )
        taskDao.insert(next)
        reminderDao.insertAll(activeReminders(id).map { it.toEntity(newId(), ReminderItemType.TASK, next.id, now) })
        CompletionResult(id, next.id)
    }

    /** Undo of [completeWithNext]: the task is open again and the next instance is removed. */
    public suspend fun undoComplete(result: CompletionResult) {
        val now = nowMillis()
        db.withTransaction {
            taskDao.uncomplete(result.taskId, now)
            result.nextInstanceId?.let { nextId ->
                taskDao.softDelete(nextId, now)
                reminderDao.softDeleteForItem(ReminderItemType.TASK, nextId, now)
            }
        }
    }

    /** Soft-deletes the task and its reminders (agent.md section 4: deleting cancels reminders). */
    public suspend fun softDelete(id: String) {
        val now = nowMillis()
        db.withTransaction {
            taskDao.softDelete(id, now)
            reminderDao.softDeleteForItem(ReminderItemType.TASK, id, now)
        }
    }

    /** Restores the task and exactly the reminders that were deleted with it. */
    public suspend fun restore(id: String) {
        db.withTransaction {
            val deletedAt = taskDao.getAnyById(id)?.deletedAt ?: return@withTransaction
            val now = nowMillis()
            taskDao.restore(id, now)
            reminderDao.restoreForItem(ReminderItemType.TASK, id, deletedAt, now)
        }
    }

    // Polymorphic and optional references are checked here because there are no foreign keys.
    private suspend fun validateReferences(task: TaskEntity) {
        // A missing row and a deleted row are both rejected; `row?.deletedAt == null` alone would let a
        // missing row through, because null?.deletedAt is also null.
        task.projectId?.let { projectId ->
            val project = db.projectDao().getAnyById(projectId)
            require(project != null && project.deletedAt == null) { "Unknown project $projectId" }
        }
        task.parentTaskId?.let { parentId ->
            require(parentId != task.id) { "A task cannot be its own parent" }
            val parent = taskDao.getAnyById(parentId)
            require(parent != null && parent.deletedAt == null) { "Unknown parent task $parentId" }
        }
    }

    private fun TaskDraft.toEntity(id: String, now: Long) = TaskEntity(
        id = id,
        title = title.trim(),
        notes = notes,
        dueAtMillis = dueAtMillis,
        dueZoneId = dueZoneId,
        dueDate = dueDate,
        priority = priority,
        projectId = projectId,
        parentTaskId = parentTaskId,
        category = category,
        recurrenceRule = recurrenceRule,
        repeatFromCompletion = repeatFromCompletion,
        importance = importance,
        completedAt = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        syncState = SyncState.DIRTY,
        serverUpdatedAt = null,
    )
}
