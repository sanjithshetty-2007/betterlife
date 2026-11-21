package com.sanjith.betterlife.data.tracker

import com.sanjith.betterlife.data.db.dao.ProteinDao
import com.sanjith.betterlife.data.db.dao.SleepDao
import com.sanjith.betterlife.domain.tracker.DailyAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** One tracker's day table, seen the same way for every tracker (protein_days, sleep_days). */
public interface TrackerDayTable {
    public fun observeAll(): Flow<List<DailyAmount>>
    public fun observeDay(date: LocalDate): Flow<DailyAmount?>
    public suspend fun day(date: LocalDate): DailyAmount?
    public suspend fun upsert(date: LocalDate, amount: Int, target: Int, now: Long, newId: () -> String)
    public suspend fun retarget(date: LocalDate, target: Int, now: Long)
}

/** protein_days: grams. */
public class ProteinTable(private val dao: () -> ProteinDao) : TrackerDayTable {
    override fun observeAll(): Flow<List<DailyAmount>> = dao().observeAll().map { rows -> rows.map { DailyAmount(it.localDate, it.grams, it.targetGrams) } }
    override fun observeDay(date: LocalDate): Flow<DailyAmount?> = dao().observeDay(date).map { it?.let { r -> DailyAmount(r.localDate, r.grams, r.targetGrams) } }
    override suspend fun day(date: LocalDate): DailyAmount? =
        dao().getAnyByDate(date)?.takeIf { it.deletedAt == null }?.let { DailyAmount(it.localDate, it.grams, it.targetGrams) }
    override suspend fun upsert(date: LocalDate, amount: Int, target: Int, now: Long, newId: () -> String) = dao().upsertDay(date, amount, target, now, newId)
    override suspend fun retarget(date: LocalDate, target: Int, now: Long) = dao().retarget(date, target, now)
}

/** sleep_days: minutes, on the day you went to bed. */
public class SleepTable(private val dao: () -> SleepDao) : TrackerDayTable {
    override fun observeAll(): Flow<List<DailyAmount>> = dao().observeAll().map { rows -> rows.map { DailyAmount(it.localDate, it.minutes, it.targetMinutes) } }
    override fun observeDay(date: LocalDate): Flow<DailyAmount?> = dao().observeDay(date).map { it?.let { r -> DailyAmount(r.localDate, r.minutes, r.targetMinutes) } }
    override suspend fun day(date: LocalDate): DailyAmount? =
        dao().getAnyByDate(date)?.takeIf { it.deletedAt == null }?.let { DailyAmount(it.localDate, it.minutes, it.targetMinutes) }
    override suspend fun upsert(date: LocalDate, amount: Int, target: Int, now: Long, newId: () -> String) = dao().upsertDay(date, amount, target, now, newId)
    override suspend fun retarget(date: LocalDate, target: Int, now: Long) = dao().retarget(date, target, now)
}
