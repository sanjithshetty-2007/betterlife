package com.sanjith.betterlife.data.repository

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.MilestoneLedgerEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.habit.CelebrationKind
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitEditRules
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.MilestoneDetector
import com.sanjith.betterlife.domain.habit.MilestoneEvent
import com.sanjith.betterlife.domain.habit.PausePeriod
import com.sanjith.betterlife.domain.habit.StarterHabit
import com.sanjith.betterlife.domain.habit.StreakCalculator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/** What the owner fills in for a new habit. */
public data class HabitDraft(
    val name: String,
    val colorHex: String,
    val avatar: String? = null,
    val category: HabitCategory = HabitCategory.OTHER,
    val essential: Boolean = false,
    val reminderTime: LocalTime? = null,
)

/** The result of a tick or untick. */
public sealed interface HabitEditResult {
    /** Saved. [milestone] is set when this tick reached one (FULL or QUIET). */
    public data class Saved(val milestone: MilestoneEvent? = null) : HabitEditResult

    /** The day is after today: future days cannot be edited. Nothing was written. */
    public data object FutureDay : HabitEditResult

    /** No such habit, or it is deleted. Nothing was written. */
    public data object UnknownHabit : HabitEditResult
}

/**
 * Habits and their daily logs (Prompt 14). Every write marks rows DIRTY for sync (S3). A tick and
 * its milestone check run in ONE transaction, so a milestone is never lost or recorded twice.
 * Streaks and consistency are never stored: [history] gives the calculators what they need.
 *
 * The caller passes the habit day ([LocalDate]) worked out with the rollover hour
 * (Clock.habitToday), so a tick at 01:00 lands on the previous day.
 */
public class HabitRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val habitDao = db.habitDao()
    private val logDao = db.habitLogDao()
    private val ledgerDao = db.milestoneLedgerDao()
    private val pauseDao = db.pauseRangeDao()

    public fun observeActive(): Flow<List<HabitEntity>> = habitDao.observeActive()

    public fun observeArchived(): Flow<List<HabitEntity>> = habitDao.observeArchived()

    public fun observeLogs(habitId: String): Flow<List<HabitLogEntity>> = logDao.observeActiveForHabit(habitId)

    public fun observeLogsBetween(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>> = logDao.observeBetween(from, to)

    public suspend fun getAnyById(id: String): HabitEntity? = habitDao.getAnyById(id)

    public suspend fun create(draft: HabitDraft, startDate: LocalDate): String {
        val now = nowMillis()
        val id = newId()
        db.withTransaction {
            habitDao.insert(
                HabitEntity(
                    id = id, name = draft.name.trim(), colorHex = draft.colorHex, avatar = draft.avatar,
                    category = draft.category, essential = draft.essential, archived = false,
                    reminderTime = draft.reminderTime, sortOrder = habitDao.maxSortOrder() + 1, startDate = startDate,
                    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.DIRTY, serverUpdatedAt = null,
                ),
            )
        }
        return id
    }

    /** The four starter habits; ones that already exist (even deleted by the owner) are left alone. */
    public suspend fun insertStarters(starters: List<StarterHabit>, names: (StarterHabit.Key) -> String, startDate: LocalDate) {
        val now = nowMillis()
        habitDao.insertIfAbsent(
            starters.mapIndexed { index, s ->
                HabitEntity(
                    id = s.id, name = names(s.key), colorHex = s.colorHex, avatar = s.avatar, category = s.category,
                    essential = s.essential, archived = false, reminderTime = null, sortOrder = index, startDate = startDate,
                    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.DIRTY, serverUpdatedAt = null,
                )
            },
        )
    }

    /** Rename, recolour, change category, Essential or reminder time. */
    public suspend fun update(habit: HabitEntity) {
        habitDao.update(habit.copy(name = habit.name.trim(), updatedAt = nowMillis(), syncState = SyncState.DIRTY))
    }

    public suspend fun setArchived(id: String, archived: Boolean) {
        habitDao.setArchived(id, archived, nowMillis())
    }

    /** Saves the owner's order: the first id gets sortOrder 0. */
    public suspend fun reorder(idsInOrder: List<String>) {
        val now = nowMillis()
        db.withTransaction { idsInOrder.forEachIndexed { index, id -> habitDao.setSortOrder(id, index, now) } }
    }

    /** To Trash. Its logs stay, so a restore brings the history back. */
    public suspend fun softDelete(id: String) {
        habitDao.softDelete(id, nowMillis())
    }

    public suspend fun restore(id: String) {
        habitDao.restore(id, nowMillis())
    }

    /** Logs, pause ranges and start date of one habit, for the streak and consistency rules. */
    public suspend fun history(habitId: String): HabitHistory? = db.withTransaction {
        habitDao.getAnyById(habitId)?.let { loadHistory(it) }
    }

    /**
     * Marks [date] DONE (or KEPT / PAUSED) for the habit and checks milestones in the same
     * transaction. A tick on a day before the habit's start date moves the start date back to it.
     */
    public suspend fun tick(
        habitId: String,
        date: LocalDate,
        today: LocalDate,
        status: HabitLogStatus = HabitLogStatus.DONE,
    ): HabitEditResult {
        if (!HabitEditRules.canEdit(date, today)) return HabitEditResult.FutureDay
        return db.withTransaction {
            val found = habitDao.getAnyById(habitId)?.takeIf { it.deletedAt == null }
                ?: return@withTransaction HabitEditResult.UnknownHabit
            val now = nowMillis()
            val habit = if (date.isBefore(found.startDate)) {
                found.copy(startDate = date, updatedAt = now, syncState = SyncState.DIRTY).also { habitDao.update(it) }
            } else {
                found
            }
            val existing = logDao.getForDay(habitId, date)
            when {
                existing == null -> logDao.insert(
                    HabitLogEntity(newId(), habitId, date, status, now, now, null, SyncState.DIRTY, null),
                )
                existing.deletedAt == null && existing.status == status -> return@withTransaction HabitEditResult.Saved()
                else -> logDao.update(existing.copy(status = status, deletedAt = null, updatedAt = now, syncState = SyncState.DIRTY))
            }
            HabitEditResult.Saved(checkMilestones(habit, date, today, now))
        }
    }

    /** Clears [date] (soft delete, so the untick syncs). The milestone ledger is not touched. */
    public suspend fun untick(habitId: String, date: LocalDate, today: LocalDate): HabitEditResult {
        if (!HabitEditRules.canEdit(date, today)) return HabitEditResult.FutureDay
        return db.withTransaction {
            habitDao.getAnyById(habitId)?.takeIf { it.deletedAt == null } ?: return@withTransaction HabitEditResult.UnknownHabit
            val existing = logDao.getForDay(habitId, date)
            if (existing != null && existing.deletedAt == null) {
                val now = nowMillis()
                logDao.update(existing.copy(deletedAt = now, updatedAt = now, syncState = SyncState.DIRTY))
            }
            HabitEditResult.Saved()
        }
    }

    private suspend fun checkMilestones(habit: HabitEntity, date: LocalDate, today: LocalDate, now: Long): MilestoneEvent? {
        val history = loadHistory(habit)
        val run = StreakCalculator.runAround(history, date, today) ?: return null
        val recorded = ledgerDao.recordedFor(habit.id, run.start).toSet()
        val outcome = MilestoneDetector.detect(run, recorded, HabitEditRules.isBackfill(date, today))
        val event = outcome.event ?: return null
        ledgerDao.insertAll(
            outcome.record.map { milestone ->
                MilestoneLedgerEntity(
                    id = newId(), habitId = habit.id, streakStartDate = run.start, milestone = milestone,
                    quiet = milestone != event.milestone || event.kind == CelebrationKind.QUIET,
                    celebratedAt = now, createdAt = now, updatedAt = now, deletedAt = null,
                )
            },
        )
        return event
    }

    private suspend fun loadHistory(habit: HabitEntity): HabitHistory = HabitHistory(
        startDate = habit.startDate,
        logs = logDao.getActiveForHabit(habit.id).associate { it.localDate to it.status },
        pauses = pauseDao.getActive().map { PausePeriod(it.startDate, it.endDate) },
    )
}
