package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.data.db.entity.SyncCursorEntity
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.flow.Flow

@Dao
public interface ProjectDao {

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND archived = 0 ORDER BY sortOrder, name")
    public fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    public suspend fun getAnyById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(project: ProjectEntity)

    @Query("UPDATE projects SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND lower(name) = lower(:name) LIMIT 1")
    public suspend fun findActiveByName(name: String): ProjectEntity?

    /** Each active project with its count of top-level tasks and how many are done. */
    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM tasks t WHERE t.projectId = p.id AND t.deletedAt IS NULL AND t.parentTaskId IS NULL) AS total,
          (SELECT COUNT(*) FROM tasks t WHERE t.projectId = p.id AND t.deletedAt IS NULL AND t.parentTaskId IS NULL
             AND t.completedAt IS NOT NULL) AS done
        FROM projects p WHERE p.deletedAt IS NULL AND p.archived = 0 ORDER BY p.sortOrder, p.name
        """,
    )
    public fun observeWithProgress(): Flow<List<ProjectWithProgress>>
}

/** A project and its task progress, for the Projects screen. */
public data class ProjectWithProgress(
    @Embedded val project: ProjectEntity,
    val total: Int,
    val done: Int,
)

/** The reminder log is append-only: there is deliberately no update or delete here (purge comes in Prompt 11). */
@Dao
public interface ReminderLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(entry: ReminderLogEntity)

    @Query("SELECT * FROM reminder_log ORDER BY firedAt DESC LIMIT :limit")
    public fun observeRecent(limit: Int): Flow<List<ReminderLogEntity>>

    @Query("SELECT COUNT(*) FROM reminder_log")
    public fun observeCount(): Flow<Int>

    /**
     * Log rows since [since], newest first, each with its reminder's item (task, event or standalone)
     * and that item's title. Deleted reminders and items are included: the history stays readable.
     */
    @Query(
        """
        SELECT l.*, r.itemType AS itemType, r.itemId AS itemId,
          COALESCE(t.title, e.title, s.title, h.name) AS itemTitle,
          COALESCE(t.deletedAt, e.deletedAt, s.deletedAt, h.deletedAt) AS itemDeletedAt
        FROM reminder_log l
        LEFT JOIN reminders r ON r.id = l.reminderId
        LEFT JOIN tasks t ON r.itemType = 'TASK' AND t.id = r.itemId
        LEFT JOIN events e ON r.itemType = 'EVENT' AND e.id = r.itemId
        LEFT JOIN standalone_reminders s ON r.itemType = 'STANDALONE' AND s.id = r.itemId
        LEFT JOIN habits h ON r.itemType = 'HABIT' AND h.id = r.itemId
        WHERE l.firedAt >= :since
        ORDER BY l.firedAt DESC
        LIMIT :limit
        """,
    )
    public fun observeCenter(since: Long, limit: Int): Flow<List<CenterLogRow>>

    /**
     * Pop-ups that started a group since [since] (fatigue protection, Prompt 12): FIRED rows whose
     * detail is the pop-up path, leaving out pop-ups that only joined a group.
     */
    @Query(
        """
        SELECT firedAt FROM reminder_log
        WHERE status = 'FIRED' AND firedAt >= :since AND detail IN ('OVERLAY', 'FULL_SCREEN_INTENT')
          AND (reasonCode IS NULL OR reasonCode != 'FATIGUE_GROUPED')
        ORDER BY firedAt DESC
        """,
    )
    public suspend fun popupStartsSince(since: Long): List<Long>

    /** The 60-day purge (Prompt 11). Returns how many rows were removed. */
    @Query("DELETE FROM reminder_log WHERE firedAt < :cutoff")
    public suspend fun deleteFiredBefore(cutoff: Long): Int

    /** Rows for one reminder, oldest first (the health tests read their result here). */
    @Query("SELECT * FROM reminder_log WHERE reminderId = :reminderId ORDER BY firedAt")
    public fun observeForReminder(reminderId: String): Flow<List<ReminderLogEntity>>
}

/** A log row with what it belongs to, for the Reminder Center. Item fields are null if the reminder is gone. */
public data class CenterLogRow(
    @Embedded val log: ReminderLogEntity,
    val itemType: ReminderItemType?,
    val itemId: String?,
    val itemTitle: String?,
    val itemDeletedAt: Long?,
)

@Dao
public interface SyncCursorDao {

    @Query("SELECT lastPulledAt FROM sync_cursors WHERE tableName = :tableName")
    public suspend fun lastPulledAt(tableName: String): Long?

    @Upsert
    public suspend fun save(cursor: SyncCursorEntity)
}
