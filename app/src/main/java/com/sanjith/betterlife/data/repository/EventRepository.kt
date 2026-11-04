package com.sanjith.betterlife.data.repository

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.flow.Flow

/** Events, meetings and birthdays with their reminders; same transaction and sync rules as tasks. */
public class EventRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val eventDao = db.eventDao()
    private val reminderDao = db.reminderDao()

    public fun observeActive(): Flow<List<EventEntity>> = eventDao.observeActive()

    public fun observeById(id: String): Flow<EventEntity?> = eventDao.observeById(id)

    public fun observeActiveCount(): Flow<Int> = eventDao.observeActiveCount()

    /** Creates the event and its reminders together; returns the new event id. */
    public suspend fun create(draft: EventDraft, reminders: List<ReminderDraft> = emptyList()): String {
        val now = nowMillis()
        val event = draft.toEntity(id = newId(), now = now)
        db.withTransaction {
            eventDao.insert(event)
            reminderDao.insertAll(reminders.map { it.toEntity(newId(), ReminderItemType.EVENT, event.id, now) })
        }
        return event.id
    }

    public suspend fun update(event: EventEntity) {
        eventDao.update(event.copy(updatedAt = nowMillis(), syncState = SyncState.DIRTY))
    }

    public suspend fun softDelete(id: String) {
        val now = nowMillis()
        db.withTransaction {
            eventDao.softDelete(id, now)
            reminderDao.softDeleteForItem(ReminderItemType.EVENT, id, now)
        }
    }

    public suspend fun restore(id: String) {
        db.withTransaction {
            val deletedAt = eventDao.getAnyById(id)?.deletedAt ?: return@withTransaction
            val now = nowMillis()
            eventDao.restore(id, now)
            reminderDao.restoreForItem(ReminderItemType.EVENT, id, deletedAt, now)
        }
    }

    private fun EventDraft.toEntity(id: String, now: Long) = EventEntity(
        id = id,
        title = title.trim(),
        type = type,
        allDay = allDay,
        startAtMillis = startAtMillis,
        endAtMillis = endAtMillis,
        zoneId = zoneId,
        startDate = startDate,
        endDate = endDate,
        birthYear = birthYear,
        location = location,
        notes = notes,
        colorCategory = colorCategory,
        recurrenceRule = recurrenceRule,
        importance = importance,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        syncState = SyncState.DIRTY,
        serverUpdatedAt = null,
    )
}
