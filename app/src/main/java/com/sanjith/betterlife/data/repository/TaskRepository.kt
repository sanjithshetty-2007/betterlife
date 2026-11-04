package com.sanjith.betterlife.data.repository

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.flow.Flow

/**
 * Tasks and their reminders. Every change that touches both runs in one transaction, and every write
 * marks the row DIRTY for sync. Alarm scheduling is wired in Prompt 7.
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
