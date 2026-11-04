package com.sanjith.betterlife.feature.events

import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.repository.EventDraft
import com.sanjith.betterlife.domain.event.BirthdayRules
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.feature.tasks.RepeatKind
import com.sanjith.betterlife.feature.tasks.TaskEditorState
import com.sanjith.betterlife.feature.tasks.repeatRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime

public enum class EventEditorError { EMPTY_TITLE, END_BEFORE_START, BIRTH_YEAR_INVALID }

/**
 * Everything the event editor shows and edits (context.md 5.2). [id] is null for a new event.
 * A birthday is always all-day and yearly; [date]'s year is its birth year unless [yearUnknown].
 * A timed event's end is on the same day, or the next day when [endTime] is before [startTime]
 * (an overnight event). [remindersEdited] stops the type's defaults from replacing the owner's choice.
 */
public data class EventEditorState(
    val id: String? = null,
    val type: EventType = EventType.EVENT,
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val date: LocalDate = LocalDate.of(BirthdayRules.UNKNOWN_YEAR_ANCHOR, 1, 1),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endTime: LocalTime? = null,
    val endDate: LocalDate? = null,
    val yearUnknown: Boolean = false,
    val colorCategory: String? = null,
    val repeat: RepeatKind = RepeatKind.NONE,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val importance: Importance = Importance.NORMAL,
    val reminders: List<ReminderPlan> = emptyList(),
    val remindersEdited: Boolean = false,
) {
    val isBirthday: Boolean get() = type == EventType.BIRTHDAY

    /** Birthdays ignore the all-day switch: they are always all-day. */
    val isAllDay: Boolean get() = allDay || isBirthday

    public fun errors(today: LocalDate): List<EventEditorError> = listOfNotNull(
        EventEditorError.EMPTY_TITLE.takeIf { title.isBlank() },
        EventEditorError.END_BEFORE_START.takeIf { !isBirthday && isAllDay && endDate != null && endDate.isBefore(date) },
        EventEditorError.BIRTH_YEAR_INVALID.takeIf { isBirthday && !BirthdayRules.isValid(MonthDay.from(date), birthYear(), today) },
    )

    public fun birthYear(): Int? = if (isBirthday && !yearUnknown) date.year else null

    /** Birthdays repeat every year on their day; other events use the chosen repeat. */
    public fun rule(): RecurrenceRule? =
        if (isBirthday) BirthdayRules.rule(MonthDay.from(date)) else repeatRule(repeat, interval, weekdays, date)

    /** The start and end instants of a timed event in [zone]. */
    public fun timedRange(zone: ZoneId): Pair<Instant, Instant?> {
        val start = date.atTime(startTime).atZone(zone)
        val end = endTime?.let { e ->
            val sameDay = date.atTime(e).atZone(zone)
            if (e.isBefore(startTime)) sameDay.plusDays(1) else sameDay
        }
        return start.toInstant() to end?.toInstant()
    }

    /** The draft for a new event, with timed values in [zone]. Call only when [errors] is empty. */
    public fun toDraft(zone: ZoneId): EventDraft {
        val ruleText = rule()?.let(RecurrenceRuleText::encode)
        return if (isAllDay) {
            EventDraft(
                title = title.trim(), type = type, allDay = true,
                startDate = if (isBirthday) BirthdayRules.startDate(MonthDay.from(date), birthYear()) else date,
                endDate = if (isBirthday) null else endDate?.takeIf { it.isAfter(date) },
                birthYear = birthYear(), location = location.ifBlank { null }, notes = notes.ifBlank { null },
                colorCategory = colorCategory, recurrenceRule = ruleText, importance = importance,
            )
        } else {
            val (start, end) = timedRange(zone)
            EventDraft(
                title = title.trim(), type = type, allDay = false,
                startAtMillis = start.toEpochMilli(), endAtMillis = end?.toEpochMilli(), zoneId = zone,
                location = location.ifBlank { null }, notes = notes.ifBlank { null },
                colorCategory = colorCategory, recurrenceRule = ruleText, importance = importance,
            )
        }
    }

    /** [existing] with this state's values, keeping its id, timestamps and sync columns. */
    public fun applyTo(existing: EventEntity, zone: ZoneId): EventEntity {
        val d = toDraft(zone)
        return existing.copy(
            title = d.title, type = d.type, allDay = d.allDay, startAtMillis = d.startAtMillis, endAtMillis = d.endAtMillis,
            zoneId = d.zoneId, startDate = d.startDate, endDate = d.endDate, birthYear = d.birthYear, location = d.location,
            notes = d.notes, colorCategory = d.colorCategory, recurrenceRule = d.recurrenceRule, importance = d.importance,
        )
    }

    /** Moves the start time; the end moves with it, so the event keeps its length. */
    public fun withStart(time: LocalTime): EventEditorState {
        val end = endTime ?: return copy(startTime = time)
        val length = Duration.between(startTime, end).let { if (it.isNegative) it.plusDays(1) else it }
        return copy(startTime = time, endTime = time.plus(length))
    }

    /** A type change: birthdays become all-day; untouched reminders follow the new type's defaults. */
    public fun withType(newType: EventType, defaults: ReminderDefaults): EventEditorState {
        // A new birthday starts with the year unknown, so the picked date's year is not taken as a birth year.
        val changed = copy(type = newType, yearUnknown = if (id == null && newType == EventType.BIRTHDAY) true else yearUnknown)
        return if (remindersEdited || id != null) changed else changed.copy(reminders = defaults.forEvent(newType))
    }

    public companion object {
        /** A new event starting at the next whole hour [now], for one hour, with the event defaults. */
        public fun new(type: EventType, now: ZonedDateTime, defaults: ReminderDefaults): EventEditorState {
            val start = now.toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1)
            // After 23:00 the next whole hour is tomorrow.
            val date = if (start.isBefore(now.toLocalTime())) now.toLocalDate().plusDays(1) else now.toLocalDate()
            return EventEditorState(
                type = type,
                date = date,
                startTime = start,
                endTime = start.plusHours(1),
                yearUnknown = type == EventType.BIRTHDAY,
                reminders = defaults.forEvent(type),
            )
        }

        /** The editor fields for a stored event, seen in [zone]. */
        public fun from(event: EventEntity, reminders: List<ReminderPlan>, zone: ZoneId): EventEditorState {
            val (repeat, interval, days) = TaskEditorState.repeatFields(RecurrenceRuleText.decode(event.recurrenceRule))
            val start = event.startAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
            val end = event.endAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
            val isBirthday = event.type == EventType.BIRTHDAY
            return EventEditorState(
                id = event.id,
                type = event.type,
                title = event.title,
                notes = event.notes.orEmpty(),
                location = event.location.orEmpty(),
                allDay = event.allDay,
                date = start?.toLocalDate() ?: event.startDate ?: LocalDate.of(BirthdayRules.UNKNOWN_YEAR_ANCHOR, 1, 1),
                startTime = start?.toLocalTime() ?: LocalTime.of(9, 0),
                endTime = end?.toLocalTime(),
                endDate = event.endDate,
                yearUnknown = isBirthday && event.birthYear == null,
                colorCategory = event.colorCategory,
                repeat = if (isBirthday) RepeatKind.NONE else repeat,
                interval = interval,
                weekdays = days,
                importance = event.importance,
                reminders = reminders,
                remindersEdited = true,
            )
        }
    }
}

/** Everything the standalone "remind me" editor shows. [id] is null for a new one. */
public data class StandaloneEditorState(
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val date: LocalDate = LocalDate.of(BirthdayRules.UNKNOWN_YEAR_ANCHOR, 1, 1),
    val time: LocalTime = LocalTime.of(9, 0),
    val reminders: List<ReminderPlan> = emptyList(),
    val done: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()

    public fun at(zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}
