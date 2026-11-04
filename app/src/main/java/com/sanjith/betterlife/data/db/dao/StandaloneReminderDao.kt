package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface StandaloneReminderDao {

    /** Not deleted and not done, soonest first. */
    @Query("SELECT * FROM standalone_reminders WHERE deletedAt IS NULL AND doneAt IS NULL ORDER BY atMillis")
    public fun observeActive(): Flow<List<StandaloneReminderEntity>>

    @Query("SELECT * FROM standalone_reminders WHERE id = :id AND deletedAt IS NULL")
    public fun observeById(id: String): Flow<StandaloneReminderEntity?>

    @Query("SELECT * FROM standalone_reminders WHERE id = :id")
    public suspend fun getAnyById(id: String): StandaloneReminderEntity?

    /** Includes deleted rows. Callers keep [ids] under SQLite's variable limit (chunks of 500). */
    @Query("SELECT * FROM standalone_reminders WHERE id IN (:ids)")
    public suspend fun getAnyByIds(ids: List<String>): List<StandaloneReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(item: StandaloneReminderEntity)

    @Update
    public suspend fun update(item: StandaloneReminderEntity)

    @Query("UPDATE standalone_reminders SET doneAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id AND doneAt IS NULL")
    public suspend fun markDone(id: String, now: Long)

    @Query("UPDATE standalone_reminders SET doneAt = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun markNotDone(id: String, now: Long)

    @Query("UPDATE standalone_reminders SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE standalone_reminders SET deletedAt = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun restore(id: String, now: Long)
}
