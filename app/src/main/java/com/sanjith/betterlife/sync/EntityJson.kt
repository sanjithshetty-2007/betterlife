package com.sanjith.betterlife.sync

import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PauseReason
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.sync.HabitDayKey
import com.sanjith.betterlife.domain.sync.RowMeta
import com.sanjith.betterlife.domain.sync.SyncCursor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * The synced tables, in push order (supabase/migrations: S1 for the Phase 1 tables, S3 for habits).
 * Reminders go last because they point at the items before them.
 */
public object SyncTables {
    public const val PROJECTS: String = "projects"
    public const val TASKS: String = "tasks"
    public const val EVENTS: String = "events"
    public const val STANDALONE: String = "standalone_reminders"
    public const val HABITS: String = "habits"
    public const val HABIT_LOGS: String = "habit_logs"
    public const val PAUSE_RANGES: String = "pause_ranges"
    public const val REMINDERS: String = "reminders"
    public val ALL: List<String> = listOf(PROJECTS, TASKS, EVENTS, STANDALONE, HABITS, HABIT_LOGS, PAUSE_RANGES, REMINDERS)

    /**
     * The server key an upsert replaces on. A habit log is one row per habit and day
     * (habit_logs_one_per_day), so the same day ticked under another id merges instead of failing.
     */
    public fun upsertKey(table: String): String = if (table == HABIT_LOGS) "user_id,habit_id,local_date" else "id"
}

/**
 * Room rows to and from the server's JSON (snake_case keys, the phone's times in epoch milliseconds,
 * dates as ISO text, enums by name). user_id and server_updated_at are never sent: the server fills
 * them. Local-only columns (reminder fire state, syncState) never leave the phone.
 */
public object EntityJson {

    public fun project(p: ProjectEntity): JsonObject = buildJsonObject {
        put("id", p.id)
        put("name", p.name)
        put("color_hex", p.colorHex)
        put("archived", p.archived)
        put("sort_order", p.sortOrder)
        times(p.createdAt, p.updatedAt, p.deletedAt)
    }

    public fun task(t: TaskEntity): JsonObject = buildJsonObject {
        put("id", t.id)
        put("title", t.title)
        put("notes", t.notes)
        put("due_at_millis", t.dueAtMillis)
        put("due_zone_id", t.dueZoneId?.id)
        put("due_date", t.dueDate?.toString())
        put("priority", t.priority.name)
        put("project_id", t.projectId)
        put("parent_task_id", t.parentTaskId)
        put("category", t.category)
        put("recurrence_rule", t.recurrenceRule)
        put("repeat_from_completion", t.repeatFromCompletion)
        put("importance", t.importance.name)
        put("completed_at", t.completedAt)
        times(t.createdAt, t.updatedAt, t.deletedAt)
    }

    public fun event(e: EventEntity): JsonObject = buildJsonObject {
        put("id", e.id)
        put("title", e.title)
        put("type", e.type.name)
        put("all_day", e.allDay)
        put("start_at_millis", e.startAtMillis)
        put("end_at_millis", e.endAtMillis)
        put("zone_id", e.zoneId?.id)
        put("start_date", e.startDate?.toString())
        put("end_date", e.endDate?.toString())
        put("birth_year", e.birthYear)
        put("location", e.location)
        put("notes", e.notes)
        put("color_category", e.colorCategory)
        put("recurrence_rule", e.recurrenceRule)
        put("importance", e.importance.name)
        times(e.createdAt, e.updatedAt, e.deletedAt)
    }

    public fun standalone(s: StandaloneReminderEntity): JsonObject = buildJsonObject {
        put("id", s.id)
        put("title", s.title)
        put("notes", s.notes)
        put("at_millis", s.atMillis)
        put("zone_id", s.zoneId.id)
        put("done_at", s.doneAt)
        times(s.createdAt, s.updatedAt, s.deletedAt)
    }

    /** Only the definition: item, offset and level. Fire state stays on the phone (context.md 11a). */
    public fun reminder(r: ReminderEntity): JsonObject = buildJsonObject {
        put("id", r.id)
        put("item_type", r.itemType.name)
        put("item_id", r.itemId)
        put("offset_minutes", r.offsetMinutes)
        put("importance", r.importance.name)
        times(r.createdAt, r.updatedAt, r.deletedAt)
    }

    public fun habit(h: HabitEntity): JsonObject = buildJsonObject {
        put("id", h.id)
        put("name", h.name)
        put("color_hex", h.colorHex)
        put("avatar", h.avatar)
        put("category", h.category.name)
        put("essential", h.essential)
        put("archived", h.archived)
        put("reminder_time", h.reminderTime?.toString())
        put("sort_order", h.sortOrder)
        put("start_date", h.startDate.toString())
        times(h.createdAt, h.updatedAt, h.deletedAt)
    }

    public fun habitLog(l: HabitLogEntity): JsonObject = buildJsonObject {
        put("id", l.id)
        put("habit_id", l.habitId)
        put("local_date", l.localDate.toString())
        put("status", l.status.name)
        times(l.createdAt, l.updatedAt, l.deletedAt)
    }

    public fun pauseRange(p: PauseRangeEntity): JsonObject = buildJsonObject {
        put("id", p.id)
        put("start_date", p.startDate.toString())
        put("end_date", p.endDate?.toString())
        put("reason", p.reason.name)
        times(p.createdAt, p.updatedAt, p.deletedAt)
    }

    // From the server. Rows arrive CLEAN: they are the server's version, so they are never pushed back.

    public fun toProject(j: JsonObject): ProjectEntity = ProjectEntity(
        id = j.string("id"),
        name = j.string("name"),
        colorHex = j.stringOrNull("color_hex"),
        archived = j.boolean("archived"),
        sortOrder = j.int("sort_order"),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    public fun toTask(j: JsonObject): TaskEntity = TaskEntity(
        id = j.string("id"),
        title = j.string("title"),
        notes = j.stringOrNull("notes"),
        dueAtMillis = j.longOrNull("due_at_millis"),
        dueZoneId = j.stringOrNull("due_zone_id")?.let(ZoneId::of),
        dueDate = j.stringOrNull("due_date")?.let(LocalDate::parse),
        priority = Priority.valueOf(j.string("priority")),
        projectId = j.stringOrNull("project_id"),
        parentTaskId = j.stringOrNull("parent_task_id"),
        category = j.stringOrNull("category"),
        recurrenceRule = j.stringOrNull("recurrence_rule"),
        repeatFromCompletion = j.boolean("repeat_from_completion"),
        importance = Importance.valueOf(j.string("importance")),
        completedAt = j.longOrNull("completed_at"),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    public fun toEvent(j: JsonObject): EventEntity = EventEntity(
        id = j.string("id"),
        title = j.string("title"),
        type = EventType.valueOf(j.string("type")),
        allDay = j.boolean("all_day"),
        startAtMillis = j.longOrNull("start_at_millis"),
        endAtMillis = j.longOrNull("end_at_millis"),
        zoneId = j.stringOrNull("zone_id")?.let(ZoneId::of),
        startDate = j.stringOrNull("start_date")?.let(LocalDate::parse),
        endDate = j.stringOrNull("end_date")?.let(LocalDate::parse),
        birthYear = j.intOrNull("birth_year"),
        location = j.stringOrNull("location"),
        notes = j.stringOrNull("notes"),
        colorCategory = j.stringOrNull("color_category"),
        recurrenceRule = j.stringOrNull("recurrence_rule"),
        importance = Importance.valueOf(j.string("importance")),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    public fun toStandalone(j: JsonObject): StandaloneReminderEntity = StandaloneReminderEntity(
        id = j.string("id"),
        title = j.string("title"),
        notes = j.stringOrNull("notes"),
        atMillis = j.long("at_millis"),
        zoneId = ZoneId.of(j.string("zone_id")),
        doneAt = j.longOrNull("done_at"),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    public fun toHabit(j: JsonObject): HabitEntity = HabitEntity(
        id = j.string("id"),
        name = j.string("name"),
        colorHex = j.string("color_hex"),
        avatar = j.stringOrNull("avatar"),
        category = HabitCategory.valueOf(j.string("category")),
        essential = j.boolean("essential"),
        archived = j.boolean("archived"),
        // The server's time type answers "07:30:00"; LocalTime reads both that and "07:30".
        reminderTime = j.stringOrNull("reminder_time")?.let(LocalTime::parse),
        sortOrder = j.int("sort_order"),
        startDate = LocalDate.parse(j.string("start_date")),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    public fun toHabitLog(j: JsonObject): HabitLogEntity = HabitLogEntity(
        id = j.string("id"),
        habitId = j.string("habit_id"),
        localDate = LocalDate.parse(j.string("local_date")),
        status = HabitLogStatus.valueOf(j.string("status")),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    public fun toPauseRange(j: JsonObject): PauseRangeEntity = PauseRangeEntity(
        id = j.string("id"),
        startDate = LocalDate.parse(j.string("start_date")),
        endDate = j.stringOrNull("end_date")?.let(LocalDate::parse),
        reason = PauseReason.valueOf(j.string("reason")),
        createdAt = j.long("created_at"),
        updatedAt = j.long("updated_at"),
        deletedAt = j.longOrNull("deleted_at"),
        syncState = SyncState.CLEAN,
        serverUpdatedAt = serverMicros(j),
    )

    /** The habit and day a server habit log belongs to (its logical identity, Prompt S3). */
    public fun habitDayKey(j: JsonObject): HabitDayKey =
        HabitDayKey(j.string("habit_id"), j.string("local_date"))

    /**
     * A pulled reminder definition. [existing] keeps this phone's fire state (scheduled, last fired,
     * snooze), so a pull never makes a delivered reminder fire twice; a new one starts SCHEDULED.
     */
    public fun toReminder(j: JsonObject, existing: ReminderEntity?): ReminderEntity {
        val definition = ReminderEntity(
            id = j.string("id"),
            itemType = ReminderItemType.valueOf(j.string("item_type")),
            itemId = j.stringOrNull("item_id"),
            offsetMinutes = j.int("offset_minutes"),
            importance = Importance.valueOf(j.string("importance")),
            state = ReminderState.SCHEDULED,
            createdAt = j.long("created_at"),
            updatedAt = j.long("updated_at"),
            deletedAt = j.longOrNull("deleted_at"),
            syncState = SyncState.CLEAN,
            serverUpdatedAt = serverMicros(j),
        )
        return existing?.let {
            definition.copy(
                state = it.state,
                scheduledFireAt = it.scheduledFireAt,
                lastFiredFireAt = it.lastFiredFireAt,
                snoozeCount = it.snoozeCount,
                snoozedUntil = it.snoozedUntil,
            )
        } ?: definition
    }

    /** id, updated_at and deleted_at of a server row, for the conflict rule. */
    public fun meta(j: JsonObject): RowMeta = RowMeta(j.string("id"), j.long("updated_at"), j.longOrNull("deleted_at"))

    /** The server's server_updated_at in microseconds (the pull cursor), or null if missing. */
    public fun serverMicros(j: JsonObject): Long? =
        j.stringOrNull("server_updated_at")?.let { SyncCursor.toMicros(OffsetDateTime.parse(it).toInstant()) }

    /** The cursor as the ISO-8601 text PostgREST filters accept (UTC, microseconds kept). */
    public fun cursorText(micros: Long): String = SyncCursor.fromMicros(micros).toString()

    private fun kotlinx.serialization.json.JsonObjectBuilder.times(createdAt: Long, updatedAt: Long, deletedAt: Long?) {
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted_at", deletedAt)
    }

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as JsonElement?)?.takeIf { it !is JsonNull }?.jsonPrimitive
    private fun JsonObject.string(key: String): String = requireNotNull(stringOrNull(key)) { "Missing $key" }
    private fun JsonObject.stringOrNull(key: String): String? = prim(key)?.contentOrNull
    private fun JsonObject.long(key: String): Long = requireNotNull(longOrNull(key)) { "Missing $key" }
    private fun JsonObject.longOrNull(key: String): Long? = prim(key)?.longOrNull
    private fun JsonObject.int(key: String): Int = requireNotNull(intOrNull(key)) { "Missing $key" }
    private fun JsonObject.intOrNull(key: String): Int? = prim(key)?.intOrNull
    private fun JsonObject.boolean(key: String): Boolean = requireNotNull(prim(key)?.booleanOrNull) { "Missing $key" }}
