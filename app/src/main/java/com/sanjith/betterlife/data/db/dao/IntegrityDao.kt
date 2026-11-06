package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Checks for references SQLite cannot enforce (there are no foreign keys: itemType + itemId is
 * polymorphic, agent.md section 4). Each query returns the ids of the broken rows.
 */
@Dao
public interface IntegrityDao {

    /** Live reminders whose task, event or standalone item does not exist at all. */
    @Query(
        """
        SELECT r.id FROM reminders r WHERE r.deletedAt IS NULL AND (
          (r.itemType = 'TASK' AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.id = r.itemId)) OR
          (r.itemType = 'EVENT' AND NOT EXISTS (SELECT 1 FROM events e WHERE e.id = r.itemId)) OR
          (r.itemType = 'STANDALONE' AND (r.itemId IS NULL OR NOT EXISTS (SELECT 1 FROM standalone_reminders s WHERE s.id = r.itemId))))
        """,
    )
    public suspend fun orphanReminderIds(): List<String>

    /** Live reminders whose item is soft-deleted: deleting an item must delete its reminders too. */
    @Query(
        """
        SELECT r.id FROM reminders r WHERE r.deletedAt IS NULL AND (
          (r.itemType = 'TASK' AND EXISTS (SELECT 1 FROM tasks t WHERE t.id = r.itemId AND t.deletedAt IS NOT NULL)) OR
          (r.itemType = 'EVENT' AND EXISTS (SELECT 1 FROM events e WHERE e.id = r.itemId AND e.deletedAt IS NOT NULL)) OR
          (r.itemType = 'STANDALONE' AND EXISTS (SELECT 1 FROM standalone_reminders s WHERE s.id = r.itemId AND s.deletedAt IS NOT NULL)))
        """,
    )
    public suspend fun remindersOfDeletedItemIds(): List<String>

    @Query("SELECT t.id FROM tasks t WHERE t.projectId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = t.projectId)")
    public suspend fun tasksWithMissingProjectIds(): List<String>

    @Query("SELECT t.id FROM tasks t WHERE t.parentTaskId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tasks p WHERE p.id = t.parentTaskId)")
    public suspend fun tasksWithMissingParentIds(): List<String>

    /** Log rows of reminders that no longer exist. Kept on purpose (the log is history); only reported. */
    @Query("SELECT COUNT(*) FROM reminder_log l WHERE NOT EXISTS (SELECT 1 FROM reminders r WHERE r.id = l.reminderId)")
    public suspend fun logRowsWithoutReminder(): Int

    @Query("UPDATE reminders SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id IN (:ids)")
    public suspend fun softDeleteReminders(ids: List<String>, now: Long)

    @Query("UPDATE tasks SET projectId = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id IN (:ids)")
    public suspend fun clearProject(ids: List<String>, now: Long)

    @Query("UPDATE tasks SET parentTaskId = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id IN (:ids)")
    public suspend fun clearParent(ids: List<String>, now: Long)
}
