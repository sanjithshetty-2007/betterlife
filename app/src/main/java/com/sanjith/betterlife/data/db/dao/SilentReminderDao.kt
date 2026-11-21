package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sanjith.betterlife.data.db.entity.SilentReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface SilentReminderDao {
    @Query("SELECT * FROM silent_reminders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    public fun observeActive(): Flow<List<SilentReminderEntity>>

    @Query("SELECT * FROM silent_reminders WHERE deletedAt IS NULL")
    public suspend fun getActive(): List<SilentReminderEntity>

    @Query("SELECT * FROM silent_reminders WHERE id = :id")
    public suspend fun getAnyById(id: String): SilentReminderEntity?

    @Insert
    public suspend fun insert(reminder: SilentReminderEntity)

    @Update
    public suspend fun update(reminder: SilentReminderEntity)

    @Query("UPDATE silent_reminders SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    public suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE silent_reminders SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE silent_reminders SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    public suspend fun restore(id: String, now: Long)
}
