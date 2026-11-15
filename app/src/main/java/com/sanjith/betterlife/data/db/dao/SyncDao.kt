package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** id, updatedAt and deletedAt of a local row, for the conflict rule. */
public data class SyncMetaRow(val id: String, val updatedAt: Long, val deletedAt: Long?)

/** A local habit log's identity (habit and day) with its version, for the habit log merge (S3). */
public data class HabitLogMetaRow(val id: String, val habitId: String, val localDate: LocalDate, val updatedAt: Long, val deletedAt: Long?)

/**
 * Queries the sync engine needs (Prompts S2 and S3). Debug seed rows ('debug-seed' category, seeded
 * habits whose id starts with 'debug-seed-', their logs, and the reminders of seeded items) are never
 * pushed to the cloud. Writes from the server use @Upsert and
 * are CLEAN; markClean only succeeds while updatedAt is still the pushed value (no lost local edit).
 */
@Dao
public interface SyncDao {

    // Dirty rows

    @Query("SELECT * FROM projects WHERE syncState = 'DIRTY' LIMIT :limit")
    public suspend fun dirtyProjects(limit: Int): List<ProjectEntity>

    @Query("SELECT * FROM tasks WHERE syncState = 'DIRTY' AND (category IS NULL OR category != 'debug-seed') LIMIT :limit")
    public suspend fun dirtyTasks(limit: Int): List<TaskEntity>

    @Query("SELECT * FROM events WHERE syncState = 'DIRTY' AND (colorCategory IS NULL OR colorCategory != 'debug-seed') LIMIT :limit")
    public suspend fun dirtyEvents(limit: Int): List<EventEntity>

    @Query("SELECT * FROM standalone_reminders WHERE syncState = 'DIRTY' LIMIT :limit")
    public suspend fun dirtyStandalone(limit: Int): List<StandaloneReminderEntity>

    @Query("SELECT * FROM habits WHERE syncState = 'DIRTY' AND id NOT LIKE 'debug-seed-%' LIMIT :limit")
    public suspend fun dirtyHabits(limit: Int): List<HabitEntity>

    @Query("SELECT * FROM habit_logs WHERE syncState = 'DIRTY' AND habitId NOT LIKE 'debug-seed-%' LIMIT :limit")
    public suspend fun dirtyHabitLogs(limit: Int): List<HabitLogEntity>

    @Query("SELECT * FROM pause_ranges WHERE syncState = 'DIRTY' LIMIT :limit")
    public suspend fun dirtyPauseRanges(limit: Int): List<PauseRangeEntity>

    @Query(
        """
        SELECT * FROM reminders r WHERE r.syncState = 'DIRTY'
          AND NOT (r.itemType = 'HABIT' AND COALESCE(r.itemId, '') LIKE 'debug-seed-%')
          AND NOT EXISTS (SELECT 1 FROM tasks t WHERE r.itemType = 'TASK' AND t.id = r.itemId AND t.category = 'debug-seed')
          AND NOT EXISTS (SELECT 1 FROM events e WHERE r.itemType = 'EVENT' AND e.id = r.itemId AND e.colorCategory = 'debug-seed')
        LIMIT :limit
        """,
    )
    public suspend fun dirtyReminders(limit: Int): List<ReminderEntity>

    /** Changes waiting to be pushed (the sync indicator; Room re-runs it when any of these tables change). */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM projects WHERE syncState = 'DIRTY')
          + (SELECT COUNT(*) FROM tasks WHERE syncState = 'DIRTY' AND (category IS NULL OR category != 'debug-seed'))
          + (SELECT COUNT(*) FROM events WHERE syncState = 'DIRTY' AND (colorCategory IS NULL OR colorCategory != 'debug-seed'))
          + (SELECT COUNT(*) FROM standalone_reminders WHERE syncState = 'DIRTY')
          + (SELECT COUNT(*) FROM habits WHERE syncState = 'DIRTY' AND id NOT LIKE 'debug-seed-%')
          + (SELECT COUNT(*) FROM habit_logs WHERE syncState = 'DIRTY' AND habitId NOT LIKE 'debug-seed-%')
          + (SELECT COUNT(*) FROM pause_ranges WHERE syncState = 'DIRTY')
          + (SELECT COUNT(*) FROM reminders r WHERE r.syncState = 'DIRTY'
              AND NOT (r.itemType = 'HABIT' AND COALESCE(r.itemId, '') LIKE 'debug-seed-%')
              AND NOT EXISTS (SELECT 1 FROM tasks t WHERE r.itemType = 'TASK' AND t.id = r.itemId AND t.category = 'debug-seed')
              AND NOT EXISTS (SELECT 1 FROM events e WHERE r.itemType = 'EVENT' AND e.id = r.itemId AND e.colorCategory = 'debug-seed'))
        """,
    )
    public fun observePendingCount(): Flow<Int>

    // Mark clean only if unchanged since the push

    @Query("UPDATE projects SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanProject(id: String, updatedAt: Long): Int

    @Query("UPDATE tasks SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanTask(id: String, updatedAt: Long): Int

    @Query("UPDATE events SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanEvent(id: String, updatedAt: Long): Int

    @Query("UPDATE standalone_reminders SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanStandalone(id: String, updatedAt: Long): Int

    @Query("UPDATE reminders SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanReminder(id: String, updatedAt: Long): Int

    @Query("UPDATE habits SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanHabit(id: String, updatedAt: Long): Int

    @Query("UPDATE habit_logs SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanHabitLog(id: String, updatedAt: Long): Int

    @Query("UPDATE pause_ranges SET syncState = 'CLEAN' WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'DIRTY'")
    public suspend fun cleanPauseRange(id: String, updatedAt: Long): Int

    // Metas for the conflict rule

    @Query("SELECT id, updatedAt, deletedAt FROM projects WHERE id IN (:ids)")
    public suspend fun projectMetas(ids: List<String>): List<SyncMetaRow>

    @Query("SELECT id, updatedAt, deletedAt FROM tasks WHERE id IN (:ids)")
    public suspend fun taskMetas(ids: List<String>): List<SyncMetaRow>

    @Query("SELECT id, updatedAt, deletedAt FROM events WHERE id IN (:ids)")
    public suspend fun eventMetas(ids: List<String>): List<SyncMetaRow>

    @Query("SELECT id, updatedAt, deletedAt FROM standalone_reminders WHERE id IN (:ids)")
    public suspend fun standaloneMetas(ids: List<String>): List<SyncMetaRow>

    @Query("SELECT id, updatedAt, deletedAt FROM reminders WHERE id IN (:ids)")
    public suspend fun reminderMetas(ids: List<String>): List<SyncMetaRow>

    @Query("SELECT id, updatedAt, deletedAt FROM habits WHERE id IN (:ids)")
    public suspend fun habitMetas(ids: List<String>): List<SyncMetaRow>

    @Query("SELECT id, updatedAt, deletedAt FROM pause_ranges WHERE id IN (:ids)")
    public suspend fun pauseRangeMetas(ids: List<String>): List<SyncMetaRow>

    /** Candidates for the habit days of a pulled page; the caller keeps only the exact (habit, day) pairs. */
    @Query("SELECT id, habitId, localDate, updatedAt, deletedAt FROM habit_logs WHERE habitId IN (:habitIds) AND localDate IN (:dates)")
    public suspend fun habitLogMetas(habitIds: List<String>, dates: List<LocalDate>): List<HabitLogMetaRow>

    /**
     * The pulled version of a habit day won under another id: the phone's losing copy of that SAME
     * day is replaced (one row per habit and day, enforced by the unique index).
     */
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND localDate = :localDate AND id != :keepId")
    public suspend fun removeOtherCopyOfDay(habitId: String, localDate: LocalDate, keepId: String)

    @Query("SELECT * FROM reminders WHERE id IN (:ids)")
    public suspend fun remindersByIds(ids: List<String>): List<ReminderEntity>

    // Writes from the server

    @Upsert
    public suspend fun upsertProjects(rows: List<ProjectEntity>)

    @Upsert
    public suspend fun upsertTasks(rows: List<TaskEntity>)

    @Upsert
    public suspend fun upsertEvents(rows: List<EventEntity>)

    @Upsert
    public suspend fun upsertStandalone(rows: List<StandaloneReminderEntity>)

    @Upsert
    public suspend fun upsertReminders(rows: List<ReminderEntity>)

    @Upsert
    public suspend fun upsertHabits(rows: List<HabitEntity>)

    @Upsert
    public suspend fun upsertHabitLogs(rows: List<HabitLogEntity>)

    @Upsert
    public suspend fun upsertPauseRanges(rows: List<PauseRangeEntity>)

    // Push everything again (first sign-in, another account, a restore)

    @Query("UPDATE projects SET syncState = 'DIRTY'")
    public suspend fun dirtyAllProjects()

    @Query("UPDATE tasks SET syncState = 'DIRTY'")
    public suspend fun dirtyAllTasks()

    @Query("UPDATE events SET syncState = 'DIRTY'")
    public suspend fun dirtyAllEvents()

    @Query("UPDATE standalone_reminders SET syncState = 'DIRTY'")
    public suspend fun dirtyAllStandalone()

    @Query("UPDATE reminders SET syncState = 'DIRTY'")
    public suspend fun dirtyAllReminders()

    @Query("UPDATE habits SET syncState = 'DIRTY'")
    public suspend fun dirtyAllHabits()

    @Query("UPDATE habit_logs SET syncState = 'DIRTY'")
    public suspend fun dirtyAllHabitLogs()

    @Query("UPDATE pause_ranges SET syncState = 'DIRTY'")
    public suspend fun dirtyAllPauseRanges()

    @Query("DELETE FROM sync_cursors")
    public suspend fun clearCursors()
}
