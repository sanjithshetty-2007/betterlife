package com.sanjith.betterlife.data.repository

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.flow.Flow

/**
 * Standalone "remind me" items and their early reminders; same transaction and sync rules as tasks.
 * Callers reschedule alarms afterwards (EventService does).
 */
public class StandaloneReminderRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val dao = db.standaloneReminderDao()
    private val reminderDao = db.reminderDao()

    public fun observeActive(): Flow<List<StandaloneReminderEntity>> = dao.observeActive()

    public suspend fun getAnyById(id: String): StandaloneReminderEntity? = dao.getAnyById(id)

    public suspend fun create(draft: StandaloneDraft, reminders: List<ReminderDraft>): String {
        val now = nowMillis()
        val item = StandaloneReminderEntity(
            id = newId(),
            title = draft.title.trim(),
            notes = draft.notes,
            atMillis = draft.atMillis,
            zoneId = draft.zoneId,
            doneAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncState = SyncState.DIRTY,
            serverUpdatedAt = null,
        )
        db.withTransaction {
            dao.insert(item)
            reminderDao.insertAll(reminders.map { it.toEntity(newId(), ReminderItemType.STANDALONE, item.id, now) })
        }
        return item.id
    }

    public suspend fun activeReminders(id: String): List<ReminderDraft> =
        reminderDao.getAllForItemIncludingDeleted(ReminderItemType.STANDALONE, id)
            .filter { it.deletedAt == null }
            .map { ReminderDraft(it.offsetMinutes, it.importance) }

    /** Ids of the item's current (not deleted) reminder rows. */
    public suspend fun activeReminderIds(id: String): List<String> =
        reminderDao.getAllForItemIncludingDeleted(ReminderItemType.STANDALONE, id).filter { it.deletedAt == null }.map { it.id }

    /** Saves an edit and replaces the reminders in one transaction (old alarms go at the next reschedule). */
    public suspend fun updateWithReminders(item: StandaloneReminderEntity, reminders: List<ReminderDraft>) {
        val now = nowMillis()
        db.withTransaction {
            dao.update(item.copy(title = item.title.trim(), updatedAt = now, syncState = SyncState.DIRTY))
            reminderDao.softDeleteForItem(ReminderItemType.STANDALONE, item.id, now)
            reminderDao.insertAll(reminders.map { it.toEntity(newId(), ReminderItemType.STANDALONE, item.id, now) })
        }
    }

    /** Done: its reminders stop at the next reschedule (the item is no longer active). */
    public suspend fun markDone(id: String) {
        dao.markDone(id, nowMillis())
    }

    public suspend fun markNotDone(id: String) {
        dao.markNotDone(id, nowMillis())
    }

    public suspend fun softDelete(id: String) {
        val now = nowMillis()
        db.withTransaction {
            dao.softDelete(id, now)
            reminderDao.softDeleteForItem(ReminderItemType.STANDALONE, id, now)
        }
    }

    /** Restores the item and exactly the reminders deleted with it. */
    public suspend fun restore(id: String) {
        db.withTransaction {
            val deletedAt = dao.getAnyById(id)?.deletedAt ?: return@withTransaction
            val now = nowMillis()
            dao.restore(id, now)
            reminderDao.restoreForItem(ReminderItemType.STANDALONE, id, deletedAt, now)
        }
    }
}
