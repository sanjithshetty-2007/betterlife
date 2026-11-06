package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.habit.PauseReason
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Pause / vacation / sick mode for habits (context.md 5.4): days inside a range neither break a
 * streak nor lower consistency. Synced in S3. Quieting reminders during a range comes with the
 * habit reminders (Prompt 15 onwards).
 */
public class PauseRangeRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val dao = db.pauseRangeDao()

    public fun observeActive(): Flow<List<PauseRangeEntity>> = dao.observeActive()

    /** [endDate] null = until the owner ends it. */
    public suspend fun start(startDate: LocalDate, endDate: LocalDate?, reason: PauseReason): String {
        require(endDate == null || !endDate.isBefore(startDate)) { "A pause cannot end before it starts" }
        val now = nowMillis()
        val id = newId()
        dao.insert(PauseRangeEntity(id, startDate, endDate, reason, now, now, null, SyncState.DIRTY, null))
        return id
    }

    /** Ends an open pause on [lastPausedDay] (included). A pause that has not begun yet is removed instead. */
    public suspend fun end(id: String, lastPausedDay: LocalDate) {
        val range = dao.getAnyById(id)?.takeIf { it.deletedAt == null } ?: return
        if (lastPausedDay.isBefore(range.startDate)) {
            dao.softDelete(id, nowMillis())
        } else {
            dao.update(range.copy(endDate = lastPausedDay, updatedAt = nowMillis(), syncState = SyncState.DIRTY))
        }
    }

    public suspend fun delete(id: String) {
        dao.softDelete(id, nowMillis())
    }
}
