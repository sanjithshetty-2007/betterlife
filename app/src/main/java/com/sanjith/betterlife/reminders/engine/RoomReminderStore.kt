package com.sanjith.betterlife.reminders.engine

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.reminder.ItemTime
import com.sanjith.betterlife.domain.reminder.ScheduleTarget
import java.time.Instant

/** [ReminderStore] backed by Room: joins each reminder with its task or event. */
public class RoomReminderStore(private val db: BetterLifeDatabase) : ReminderStore {

    private val reminderDao = db.reminderDao()

    override suspend fun loadTargets(): List<ScheduleTarget> {
        val reminders = reminderDao.getSchedulable()
        val tasks = loadTasks(reminders)
        val events = loadEvents(reminders)
        return reminders.map { reminder ->
            val item = itemFor(reminder, tasks, events)
            ScheduleTarget(
                reminderId = reminder.id,
                importance = reminder.importance,
                offsetMinutes = reminder.offsetMinutes,
                itemTime = item.time,
                recurrence = item.recurrence,
                itemDone = item.status != ItemStatus.ACTIVE,
                scheduledFireAt = reminder.scheduledFireAt?.let(Instant::ofEpochMilli),
                lastFiredFireAt = reminder.lastFiredFireAt?.let(Instant::ofEpochMilli),
            )
        }
    }

    override suspend fun idsToUnschedule(): List<String> = reminderDao.getIdsToUnschedule()

    override suspend fun setScheduledFireAt(reminderId: String, fireAt: Instant?) {
        reminderDao.setScheduledFireAt(reminderId, fireAt?.toEpochMilli())
    }

    override suspend fun fireContext(reminderId: String): FireContext? {
        val reminder = reminderDao.getAnyById(reminderId) ?: return null
        val item = when (reminder.itemType) {
            ReminderItemType.TASK -> reminder.itemId?.let { db.taskDao().getAnyById(it) }?.toItem() ?: MISSING
            ReminderItemType.EVENT -> reminder.itemId?.let { db.eventDao().getAnyById(it) }?.toItem() ?: MISSING
            ReminderItemType.STANDALONE -> STANDALONE
        }
        // A reminder deleted on its own counts like a deleted item: it must not show.
        val status = if (reminder.deletedAt != null || reminder.state == ReminderState.CANCELLED) ItemStatus.DELETED else item.status
        return FireContext(reminder.id, reminder.importance, status, item.title, item.recurrence != null, reminder.offsetMinutes)
    }

    override suspend fun markFired(reminderId: String, fireAt: Instant, state: ReminderState): Boolean =
        reminderDao.markFired(reminderId, fireAt.toEpochMilli(), state) > 0

    /** The parts of a task or event the engine needs. */
    private data class ItemInfo(val title: String, val status: ItemStatus, val time: ItemTime?, val recurrence: RecurrenceRule?)

    private fun itemFor(reminder: ReminderEntity, tasks: Map<String, TaskEntity>, events: Map<String, EventEntity>): ItemInfo =
        when (reminder.itemType) {
            ReminderItemType.TASK -> tasks[reminder.itemId]?.toItem() ?: MISSING
            ReminderItemType.EVENT -> events[reminder.itemId]?.toItem() ?: MISSING
            ReminderItemType.STANDALONE -> STANDALONE
        }

    private suspend fun loadTasks(reminders: List<ReminderEntity>): Map<String, TaskEntity> =
        idsOf(reminders, ReminderItemType.TASK).chunked(SQL_CHUNK).flatMap { db.taskDao().getAnyByIds(it) }.associateBy { it.id }

    private suspend fun loadEvents(reminders: List<ReminderEntity>): Map<String, EventEntity> =
        idsOf(reminders, ReminderItemType.EVENT).chunked(SQL_CHUNK).flatMap { db.eventDao().getAnyByIds(it) }.associateBy { it.id }

    private fun idsOf(reminders: List<ReminderEntity>, type: ReminderItemType): List<String> =
        reminders.filter { it.itemType == type }.mapNotNull { it.itemId }.distinct()

    private fun TaskEntity.toItem() = ItemInfo(
        title = title,
        status = when {
            deletedAt != null -> ItemStatus.DELETED
            completedAt != null -> ItemStatus.COMPLETED
            else -> ItemStatus.ACTIVE
        },
        time = when {
            dueAtMillis != null && dueZoneId != null -> ItemTime.At(Instant.ofEpochMilli(dueAtMillis), dueZoneId)
            dueDate != null -> ItemTime.AllDay(dueDate)
            else -> null
        },
        recurrence = RecurrenceRuleText.decode(recurrenceRule),
    )

    private fun EventEntity.toItem() = ItemInfo(
        title = title,
        status = if (deletedAt != null) ItemStatus.DELETED else ItemStatus.ACTIVE,
        time = when {
            allDay && startDate != null -> ItemTime.AllDay(startDate)
            startAtMillis != null && zoneId != null -> ItemTime.At(Instant.ofEpochMilli(startAtMillis), zoneId)
            else -> null
        },
        recurrence = RecurrenceRuleText.decode(recurrenceRule),
    )

    private companion object {
        /** SQLite allows 999 bound variables per query; stay well under it. */
        const val SQL_CHUNK = 500
        val MISSING = ItemInfo(title = "", status = ItemStatus.DELETED, time = null, recurrence = null)

        // Standalone reminders get their own time and title in Prompt 10; until then they never fire.
        val STANDALONE = ItemInfo(title = "", status = ItemStatus.ACTIVE, time = null, recurrence = null)
    }
}
