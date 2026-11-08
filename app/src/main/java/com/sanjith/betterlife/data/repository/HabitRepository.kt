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
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /** The day is not today: only today can be edited. Nothing was written. */
    public data object NotToday : HabitEditResult

    /** No such habit, or it is deleted. Nothing was written. */
    public data object UnknownHabit : HabitEditResult
}

/**
 * Habits and their daily logs (Prompt 14). Every write marks rows DIRTY for sync (S3). A tick and
 * its milestone check run in ONE transaction, so a milestone is never lost or recorded twice.
 * Streaks and consistency are never stored: [history] gives the calculators what they need.
 * A habit with a reminder time owns one NORMAL reminder row (item type HABIT, offset 0); callers
 * reschedule alarms afterwards (HabitService does).
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
    private val reminderDao = db.reminderDao()

    public fun observeActive(): Flow<List<HabitEntity>> = habitDao.observeActive()

    public fun observeArchived(): Flow<List<HabitEntity>> = habitDao.observeArchived()

    public fun observeLogs(habitId: String): Flow<List<HabitLogEntity>> = logDao.observeActiveForHabit(habitId)

    public fun observeAllLogs(): Flow<List<HabitLogEntity>> = logDao.observeAllActive()

    public fun observeById(id: String): Flow<HabitEntity?> = habitDao.observeById(id)

    public fun observePauses(): Flow<List<PausePeriod>> = pauseDao.observeActive().map { list -> list.map { PausePeriod(it.startDate, it.endDate) } }

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
            if (draft.reminderTime != null) insertReminder(id, now)
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

    /** Rename, recolour, change category, Essential or reminder time (its reminder row is replaced only when the time changes). */
    public suspend fun update(habit: HabitEntity) {
        val now = nowMillis()
        db.withTransaction {
            val before = habitDao.getAnyById(habit.id)
            habitDao.update(habit.copy(name = habit.name.trim(), updatedAt = now, syncState = SyncState.DIRTY))
            if (before?.reminderTime != habit.reminderTime) {
                reminderDao.softDeleteForItem(ReminderItemType.HABIT, habit.id, now)
                if (habit.reminderTime != null) insertReminder(habit.id, now)
            }
        }
    }

    public suspend fun setArchived(id: String, archived: Boolean) {
        habitDao.setArchived(id, archived, nowMillis())
    }

    /** Saves the owner's order: the first id gets sortOrder 0. */
    public suspend fun reorder(idsInOrder: List<String>) {
        val now = nowMillis()
        db.withTransaction { idsInOrder.forEachIndexed { index, id -> habitDao.setSortOrder(id, index, now) } }
    }

    /** To Trash with its reminder. Its logs stay, so a restore brings the history back. */
    public suspend fun softDelete(id: String) {
        val now = nowMillis()
        db.withTransaction {
            habitDao.softDelete(id, now)
            reminderDao.softDeleteForItem(ReminderItemType.HABIT, id, now)
        }
    }

    /** Restores the habit and exactly the reminder deleted with it. */
    public suspend fun restore(id: String) {
        db.withTransaction {
            val deletedAt = habitDao.getAnyById(id)?.deletedAt ?: return@withTransaction
            val now = nowMillis()
            habitDao.restore(id, now)
            reminderDao.restoreForItem(ReminderItemType.HABIT, id, deletedAt, now)
        }
    }

    private suspend fun insertReminder(habitId: String, now: Long) {
        reminderDao.insertAll(listOf(ReminderDraft(0).toEntity(newId(), ReminderItemType.HABIT, habitId, now)))
    }

    /** Logs, pause ranges and start date of one habit, for the streak and consistency rules. */
    public suspend fun history(habitId: String): HabitHistory? = db.withTransaction {
        habitDao.getAnyById(habitId)?.let { loadHistory(it) }
    }

    /**
     * Marks [date], which must be [today], DONE (or KEPT / PAUSED) for the habit and checks milestones
     * in the same transaction.
     */
    public suspend fun tick(
        habitId: String,
        date: LocalDate,
        today: LocalDate,
        status: HabitLogStatus = HabitLogStatus.DONE,
    ): HabitEditResult {
        if (!HabitEditRules.canEdit(date, today)) return HabitEditResult.NotToday
        return db.withTransaction {
            val found = habitDao.getAnyById(habitId)?.takeIf { it.deletedAt == null }
                ?: return@withTransaction HabitEditResult.UnknownHabit
            val now = nowMillis()
            val habit = found
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
        if (!HabitEditRules.canEdit(date, today)) return HabitEditResult.NotToday
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
