package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.sanjith.betterlife.data.db.entity.ProteinDayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
public interface ProteinDao {
    @Query("SELECT * FROM protein_days WHERE deletedAt IS NULL ORDER BY localDate")
    public fun observeAll(): Flow<List<ProteinDayEntity>>

    @Query("SELECT * FROM protein_days WHERE localDate = :date AND deletedAt IS NULL")
    public fun observeDay(date: LocalDate): Flow<ProteinDayEntity?>

    @Query("SELECT * FROM protein_days WHERE localDate = :date")
    public suspend fun getAnyByDate(date: LocalDate): ProteinDayEntity?

    @Insert
    public suspend fun insert(day: ProteinDayEntity)

    @Query("UPDATE protein_days SET grams = :grams, targetGrams = :targetGrams, updatedAt = :now, deletedAt = NULL WHERE id = :id")
    public suspend fun update(id: String, grams: Int, targetGrams: Int, now: Long)

    @Query("UPDATE protein_days SET targetGrams = :targetGrams, updatedAt = :now WHERE localDate = :date AND deletedAt IS NULL")
    public suspend fun retarget(date: LocalDate, targetGrams: Int, now: Long)

    /** Saves a day's amount: updates the day's row if there is one (never a second row for a date). */
    @Transaction
    public suspend fun upsertDay(date: LocalDate, grams: Int, targetGrams: Int, now: Long, newId: () -> String) {
        val existing = getAnyByDate(date)
        if (existing == null) {
            insert(ProteinDayEntity(newId(), date, grams, targetGrams, now, now, deletedAt = null))
        } else {
            update(existing.id, grams, targetGrams, now)
        }
    }
}
