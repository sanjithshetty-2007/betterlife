package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanjith.betterlife.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
public interface EventDao {

    @Query(
        """
        SELECT * FROM events WHERE deletedAt IS NULL
        ORDER BY startAtMillis IS NULL, startAtMillis, startDate, createdAt
        """,
    )
    public fun observeActive(): Flow<List<EventEntity>>

    /**
     * Events the calendar may show between two moments / dates (Prompt 18): every repeating event
     * (it is expanded for the visible dates only, never stored per occurrence), and one-off events
     * whose time or days touch the range.
     */
    @Query(
        """
        SELECT * FROM events WHERE deletedAt IS NULL AND (
          recurrenceRule IS NOT NULL
          OR (startAtMillis IS NOT NULL AND startAtMillis < :toMillis AND COALESCE(endAtMillis, startAtMillis) >= :fromMillis)
          OR (startAtMillis IS NULL AND startDate <= :toDate AND COALESCE(endDate, startDate) >= :fromDate)
        )
        ORDER BY startAtMillis IS NULL, startAtMillis, startDate, createdAt
        """,
    )
    public fun observeInRange(fromMillis: Long, toMillis: Long, fromDate: LocalDate, toDate: LocalDate): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND deletedAt IS NULL")
    public fun observeById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    public suspend fun getAnyById(id: String): EventEntity?

    /** Includes deleted rows. Callers keep [ids] under SQLite's variable limit (chunks of 500). */
    @Query("SELECT * FROM events WHERE id IN (:ids)")
    public suspend fun getAnyByIds(ids: List<String>): List<EventEntity>

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
