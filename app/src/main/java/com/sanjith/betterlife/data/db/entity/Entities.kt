package com.sanjith.betterlife.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import java.time.LocalDate
import java.time.ZoneId

// Rules (agent.md section 4): UUID text ids made on the phone; createdAt/updatedAt/deletedAt on every
// row (epoch millis, deletedAt null = not deleted). Synced tables (context.md section 11a) also carry
// syncState and serverUpdatedAt from version 1, so sync needs no migration later.

/** Whether a synced row still has to be pushed to Supabase. */
public enum class SyncState { DIRTY, CLEAN }

@Entity(
    tableName = "projects",
    indices = [Index("deletedAt"), Index("syncState")],
)
public data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String?,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/**
 * A task is due either at an instant ([dueAtMillis] + [dueZoneId]) or on a whole day ([dueDate]),
 * never both. Neither set means undated (it shows in the Inbox).
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index("deletedAt", "dueAtMillis"),
        Index("dueDate"),
        Index("projectId"),
        Index("parentTaskId"),
        Index("syncState"),
    ],
)
public data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val dueAtMillis: Long?,
    val dueZoneId: ZoneId?,
    val dueDate: LocalDate?,
    val priority: Priority,
    val projectId: String?,
    val parentTaskId: String?,
    val category: String?,
    /** Recurrence rule text; its format is defined in Prompt 6. */
    val recurrenceRule: String?,
    val repeatFromCompletion: Boolean,
    val importance: Importance,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/**
 * Timed events use [startAtMillis]/[endAtMillis] + [zoneId]. All-day items (birthdays) use
 * [startDate]/[endDate] with no zone. [birthYear] is null when the year of a birthday is unknown.
 */
@Entity(
    tableName = "events",
    indices = [Index("deletedAt", "startAtMillis"), Index("startDate"), Index("syncState")],
)
public data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: EventType,
    val allDay: Boolean,
    val startAtMillis: Long?,
    val endAtMillis: Long?,
    val zoneId: ZoneId?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val birthYear: Int?,
    val location: String?,
    val notes: String?,
    val colorCategory: String?,
    val recurrenceRule: String?,
    val importance: Importance,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/**
 * One reminder for one item: fires [offsetMinutes] before the item's time (0 = at time).
 * [itemId] points at a task, event or standalone reminder (validated in the repository, not by a
 * foreign key). [state], [scheduledFireAt] and [lastFiredFireAt] are local fire state and are
 * never synced. [scheduledFireAt] is the alarm currently set (epoch millis); [lastFiredFireAt] is the
 * last one delivered, which makes a second delivery of the same alarm a no-op (added in version 2).
 */
@Entity(
    tableName = "reminders",
    indices = [Index("itemType", "itemId"), Index("deletedAt", "state"), Index("syncState")],
)
public data class ReminderEntity(
    @PrimaryKey val id: String,
    val itemType: ReminderItemType,
    val itemId: String?,
    val offsetMinutes: Int,
    val importance: Importance,
    val state: ReminderState,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
    val scheduledFireAt: Long? = null,
    val lastFiredFireAt: Long? = null,
    /** Times this reminder was snoozed since it last fired normally (version 3, local only). */
    @ColumnInfo(defaultValue = "0") val snoozeCount: Int = 0,
    /** When a snoozed reminder comes back (version 3, local only). */
    val snoozedUntil: Long? = null,
)

/**
 * A standalone "remind me" item (Prompt 10): a title and a moment, with no task or event behind it.
 * Its reminders point at it with itemType STANDALONE. [doneAt] is set when the owner marks it done.
 * Synced like events (reminder definitions are on the synced list, context.md 11a).
 */
@Entity(
    tableName = "standalone_reminders",
    indices = [Index("deletedAt"), Index("syncState")],
)
public data class StandaloneReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val atMillis: Long,
    val zoneId: ZoneId,
    val doneAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "DIRTY") val syncState: SyncState,
    val serverUpdatedAt: Long?,
)

/** Append-only history of what happened to reminders; never edited, kept 60 days (Prompt 11). */
@Entity(
    tableName = "reminder_log",
    indices = [Index("reminderId"), Index("firedAt")],
)
public data class ReminderLogEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val firedAt: Long,
    val status: ReminderLogStatus,
    val reasonCode: ReasonCode?,
    val level: Importance,
    /** Technical detail for debugging. Never private content such as note text. */
    val detail: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** The pull cursor per synced table (Prompt S2): the server's server_updated_at in MICROseconds since the epoch. */
@Entity(tableName = "sync_cursors")
public data class SyncCursorEntity(
    @PrimaryKey val tableName: String,
    val lastPulledAt: Long,
)
