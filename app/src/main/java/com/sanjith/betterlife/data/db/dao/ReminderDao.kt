package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderState
import kotlinx.coroutines.flow.Flow

@Dao
public interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE itemType = :itemType AND itemId = :itemId AND deletedAt IS NULL")
    public fun observeForItem(itemType: ReminderItemType, itemId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE itemType = :itemType AND itemId = :itemId")
    public suspend fun getAllForItemIncludingDeleted(itemType: ReminderItemType, itemId: String): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE deletedAt IS NULL")
    public fun observeActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE id = :id")
    public suspend fun getAnyById(id: String): ReminderEntity?

    /** Reminders the engine may schedule: not deleted and not switched off. */
    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL AND state != 'CANCELLED'")
    public suspend fun getSchedulable(): List<ReminderEntity>

    /** Deleted or switched-off reminders that still have an alarm set: their alarms must be cancelled. */
    @Query("SELECT id FROM reminders WHERE (deletedAt IS NOT NULL OR state = 'CANCELLED') AND scheduledFireAt IS NOT NULL")
    public suspend fun getIdsToUnschedule(): List<String>

    /** Local schedule state only: does not touch updatedAt or syncState. */
    @Query("UPDATE reminders SET scheduledFireAt = :fireAt WHERE id = :id")
    public suspend fun setScheduledFireAt(id: String, fireAt: Long?)

    /**
     * Records that the alarm for [fireAt] was delivered. Returns 0 if it already was, which is how a
     * duplicate alarm is ignored (one atomic UPDATE, so two receivers cannot both win).
     */
    @Query(
        """
        UPDATE reminders SET lastFiredFireAt = :fireAt, state = :state
        WHERE id = :id AND (lastFiredFireAt IS NULL OR lastFiredFireAt != :fireAt)
        """,
    )
    public suspend fun markFired(id: String, fireAt: Long, state: ReminderState): Int

    @Query("SELECT * FROM reminders WHERE scheduledFireAt IS NOT NULL ORDER BY scheduledFireAt")
    public fun observeScheduled(): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET state = :state, updatedAt = :now WHERE id = :id")
    public suspend fun setState(id: String, state: ReminderState, now: Long)

    /** Soft-deletes an item's reminders; [now] is also the marker used to restore exactly these rows. */
    @Query(
        """
        UPDATE reminders SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY'
        WHERE itemType = :itemType AND itemId = :itemId AND deletedAt IS NULL
        """,
    )
    public suspend fun softDeleteForItem(itemType: ReminderItemType, itemId: String, now: Long)

    /** Restores only the reminders deleted together with the item (same deletedAt), not older ones. */
    @Query(
        """
        UPDATE reminders SET deletedAt = NULL, updatedAt = :now, syncState = 'DIRTY'
        WHERE itemType = :itemType AND itemId = :itemId AND deletedAt = :deletedAt
        """,
    )
    public suspend fun restoreForItem(itemType: ReminderItemType, itemId: String, deletedAt: Long, now: Long)

    /** Debug only: removes reminders whose item no longer exists after a seed wipe. */
    @Query(
        """
        DELETE FROM reminders WHERE
        (itemType = 'TASK' AND itemId NOT IN (SELECT id FROM tasks)) OR
        (itemType = 'EVENT' AND itemId NOT IN (SELECT id FROM events))
        """,
    )
    public suspend fun hardDeleteOrphans(): Int
}
