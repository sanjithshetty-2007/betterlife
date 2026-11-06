package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanjith.betterlife.data.db.entity.CelebrationMessageEntity
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.MilestoneLedgerEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
public interface HabitDao {

    /** Not deleted and not archived, in the owner's order. */
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL AND archived = 0 ORDER BY sortOrder, createdAt")
    public fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL AND archived = 1 ORDER BY sortOrder, createdAt")
    public fun observeArchived(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    public suspend fun getAnyById(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id AND deletedAt IS NULL")
    public fun observeById(id: String): Flow<HabitEntity?>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habits WHERE deletedAt IS NULL")
    public suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(habit: HabitEntity)

    /** Debug only: removes seeded habits (their ids start with the seed marker). */
    @Query("DELETE FROM habits WHERE id LIKE :idPattern")
    public suspend fun hardDeleteSeeded(idPattern: String): Int

    /** For the starter habits: a row that already exists (even deleted) is left alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertIfAbsent(habits: List<HabitEntity>)

    @Update
    public suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET archived = :archived, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE habits SET sortOrder = :sortOrder, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id AND sortOrder != :sortOrder")
    public suspend fun setSortOrder(id: String, sortOrder: Int, now: Long)

    @Query("UPDATE habits SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE habits SET deletedAt = NULL, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun restore(id: String, now: Long)
}

@Dao
public interface HabitLogDao {

    /** The row for one habit day, deleted or not (there is at most one). */
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND localDate = :date")
    public suspend fun getForDay(habitId: String, date: LocalDate): HabitLogEntity?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND deletedAt IS NULL")
    public suspend fun getActiveForHabit(habitId: String): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND deletedAt IS NULL ORDER BY localDate")
    public fun observeActiveForHabit(habitId: String): Flow<List<HabitLogEntity>>

    /** Every habit's logs (streaks need the whole history; a year of 12 habits is about 4,400 rows). */
    @Query("SELECT * FROM habit_logs WHERE deletedAt IS NULL")
    public fun observeAllActive(): Flow<List<HabitLogEntity>>

    /** Every habit's logs in a date range, both ends included (for grids and Today). */
    @Query("SELECT * FROM habit_logs WHERE deletedAt IS NULL AND localDate BETWEEN :from AND :to")
    public fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(log: HabitLogEntity)

    /** Debug seed only. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAll(logs: List<HabitLogEntity>)

    /** Debug only: removes the logs of seeded habits. */
    @Query("DELETE FROM habit_logs WHERE habitId LIKE :idPattern")
    public suspend fun hardDeleteSeeded(idPattern: String): Int

    @Update
    public suspend fun update(log: HabitLogEntity)
}

@Dao
public interface MilestoneLedgerDao {

    @Query("SELECT milestone FROM milestone_ledger WHERE habitId = :habitId AND streakStartDate = :streakStart AND deletedAt IS NULL")
    public suspend fun recordedFor(habitId: String, streakStart: LocalDate): List<Int>

    @Query("SELECT * FROM milestone_ledger WHERE habitId = :habitId AND deletedAt IS NULL ORDER BY celebratedAt")
    public suspend fun getForHabit(habitId: String): List<MilestoneLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAll(rows: List<MilestoneLedgerEntity>)
}

@Dao
public interface PauseRangeDao {

    @Query("SELECT * FROM pause_ranges WHERE deletedAt IS NULL ORDER BY startDate")
    public suspend fun getActive(): List<PauseRangeEntity>

    @Query("SELECT * FROM pause_ranges WHERE deletedAt IS NULL ORDER BY startDate")
    public fun observeActive(): Flow<List<PauseRangeEntity>>

    @Query("SELECT * FROM pause_ranges WHERE id = :id")
    public suspend fun getAnyById(id: String): PauseRangeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(range: PauseRangeEntity)

    @Update
    public suspend fun update(range: PauseRangeEntity)

    @Query("UPDATE pause_ranges SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)
}

@Dao
public interface CelebrationMessageDao {

    @Query("SELECT * FROM celebration_messages WHERE deletedAt IS NULL ORDER BY createdAt")
    public fun observeActive(): Flow<List<CelebrationMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(message: CelebrationMessageEntity)

    @Query("UPDATE celebration_messages SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)
}
