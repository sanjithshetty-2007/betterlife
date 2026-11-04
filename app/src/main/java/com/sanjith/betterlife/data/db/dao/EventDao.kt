package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanjith.betterlife.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface EventDao {

    @Query(
        """
        SELECT * FROM events WHERE deletedAt IS NULL
        ORDER BY startAtMillis IS NULL, startAtMillis, startDate, createdAt
        """,
    )
    public fun observeActive(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND deletedAt IS NULL")
    public fun observeById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    public suspend fun getAnyById(id: String): EventEntity?

    @Query("SELECT COUNT(*) FROM events WHERE deletedAt IS NULL")
    public fun observeActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insertAll(events: List<EventEntity>)

    @Update
    public suspend fun update(event: EventEntity)

    @Query("UPDATE events SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE events SET deletedAt = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun restore(id: String, now: Long)

    /** Debug only: removes seeded rows for good. */
    @Query("DELETE FROM events WHERE colorCategory = :marker")
    public suspend fun hardDeleteByColorCategory(marker: String): Int
}
