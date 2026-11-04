package com.sanjith.betterlife.data.debug

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.toEntity
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.model.ReminderItemType
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Counts of rows written by one seed run. */
public data class SeedResult(val tasks: Int, val events: Int, val reminders: Int, val millis: Long)

/**
 * Debug only: fills the database with realistic volume for performance checks. Seeded rows carry
 * [SEED_MARKER] in their category, which is how [wipe] finds them without touching real data.
 */
public class SeedDataGenerator(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    public suspend fun seed(taskCount: Int = DEFAULT_TASKS, eventCount: Int = DEFAULT_EVENTS): SeedResult {
        val started = nowMillis()
        val random = Random(RANDOM_SEED)
        val tasks = List(taskCount) { index -> seedTask(index, started, random) }
        val events = List(eventCount) { index -> seedEvent(index, started, random) }
        val reminders = tasks.filter { random.nextInt(PERCENT) < TASK_REMINDER_PERCENT }
            .flatMap { reminderRows(ReminderItemType.TASK, it.id, started, random) } +
            events.flatMap { reminderRows(ReminderItemType.EVENT, it.id, started, random) }
        db.withTransaction {
            db.taskDao().insertAll(tasks)
            db.eventDao().insertAll(events)
            db.reminderDao().insertAll(reminders)
        }
        return SeedResult(tasks.size, events.size, reminders.size, nowMillis() - started)
    }

    /** Hard-deletes only seeded rows and the reminders that belonged to them. Returns rows removed. */
    public suspend fun wipe(): Int = db.withTransaction {
        db.taskDao().hardDeleteByCategory(SEED_MARKER) +
            db.eventDao().hardDeleteByColorCategory(SEED_MARKER) +
            db.reminderDao().hardDeleteOrphans()
    }

    private fun seedTask(index: Int, now: Long, random: Random): TaskEntity {
        val dated = random.nextInt(PERCENT) < DATED_TASK_PERCENT
        val due = now + random.nextLong(-WINDOW_MILLIS, WINDOW_MILLIS)
        return TaskEntity(
            id = newId(),
            title = "Seed task ${index + 1}",
            notes = null,
            dueAtMillis = if (dated) due else null,
            dueZoneId = if (dated) zone else null,
            dueDate = null,
            priority = Priority.entries.random(random),
            projectId = null,
            parentTaskId = null,
            category = SEED_MARKER,
            recurrenceRule = null,
            repeatFromCompletion = false,
            importance = Importance.NORMAL,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncState = SyncState.DIRTY,
            serverUpdatedAt = null,
        )
    }

    private fun seedEvent(index: Int, now: Long, random: Random): EventEntity {
        val birthday = index % BIRTHDAY_EVERY == 0
        val start = now + random.nextLong(-WINDOW_MILLIS, WINDOW_MILLIS)
        return EventEntity(
            id = newId(),
            title = if (birthday) "Seed birthday ${index + 1}" else "Seed event ${index + 1}",
            type = if (birthday) EventType.BIRTHDAY else EventType.entries.filter { it != EventType.BIRTHDAY }.random(random),
            allDay = birthday,
            startAtMillis = if (birthday) null else start,
            endAtMillis = if (birthday) null else start + HOUR_MILLIS,
            zoneId = if (birthday) null else zone,
            startDate = if (birthday) Instant.ofEpochMilli(start).atZone(zone).toLocalDate() else null,
            endDate = null,
            birthYear = null,
            location = null,
            notes = null,
            colorCategory = SEED_MARKER,
            recurrenceRule = null,
            importance = Importance.NORMAL,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncState = SyncState.DIRTY,
            serverUpdatedAt = null,
        )
    }

    private fun reminderRows(type: ReminderItemType, itemId: String, now: Long, random: Random) =
        List(random.nextInt(1, MAX_REMINDERS_PER_ITEM + 1)) { i ->
            ReminderDraft(offsetMinutes = OFFSETS[(i + random.nextInt(OFFSETS.size)) % OFFSETS.size])
                .toEntity(newId(), type, itemId, now)
        }

    public companion object {
        public const val SEED_MARKER: String = "debug-seed"
        private const val DEFAULT_TASKS = 1_000
        private const val DEFAULT_EVENTS = 200
        private const val RANDOM_SEED = 42
        private const val PERCENT = 100
        private const val DATED_TASK_PERCENT = 70
        private const val TASK_REMINDER_PERCENT = 30
        private const val BIRTHDAY_EVERY = 10
        private const val MAX_REMINDERS_PER_ITEM = 2
        private const val HOUR_MILLIS = 3_600_000L
        private const val WINDOW_MILLIS = 30L * 24 * HOUR_MILLIS
        private val OFFSETS = listOf(0, 10, 60, 1_440)
    }
}
