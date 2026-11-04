package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanjith.betterlife.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface TaskDao {

    /** Not-deleted tasks, soonest first; undated tasks last. */
    @Query(
        """
        SELECT * FROM tasks WHERE deletedAt IS NULL
        ORDER BY dueAtMillis IS NULL, dueAtMillis, dueDate IS NULL, dueDate, createdAt
        """,
    )
    public fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    public fun observeById(id: String): Flow<TaskEntity?>

    /** Includes deleted rows, for restore and validation. */
    @Query("SELECT * FROM tasks WHERE id = :id")
    public suspend fun getAnyById(id: String): TaskEntity?

    /** Includes deleted rows. Callers keep [ids] under SQLite's variable limit (chunks of 500). */
    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    public suspend fun getAnyByIds(ids: List<String>): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL")
    public fun observeActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    public suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id AND completedAt IS NULL")
    public suspend fun complete(id: String, now: Long)

    @Query("UPDATE tasks SET completedAt = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun uncomplete(id: String, now: Long)

    @Query("UPDATE tasks SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE tasks SET deletedAt = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun restore(id: String, now: Long)

    @Query("SELECT id FROM tasks WHERE category = :category AND deletedAt IS NULL")
    public suspend fun getActiveIdsByCategory(category: String): List<String>

    /** Debug only: removes seeded rows for good (agent.md allows no hard delete of real data). */
    @Query("DELETE FROM tasks WHERE category = :marker")
    public suspend fun hardDeleteByCategory(marker: String): Int
}
