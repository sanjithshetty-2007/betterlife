package com.sanjith.betterlife.feature.events

import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.event.AgendaTime
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.task.TaskDue
import java.time.Instant

// Stored rows as the agenda and calendar see them (Prompts 10 and 18; Today uses events too).

internal fun EventEntity.toAgendaItem(): AgendaItem = AgendaItem(
    id = id,
    kind = when (type) {
        EventType.EVENT -> AgendaKind.EVENT
        EventType.MEETING -> AgendaKind.MEETING
        EventType.BIRTHDAY -> AgendaKind.BIRTHDAY
    },
    title = title,
    time = when {
        startAtMillis != null && zoneId != null ->
            AgendaTime.Timed(Instant.ofEpochMilli(startAtMillis), endAtMillis?.let(Instant::ofEpochMilli), zoneId)
        else -> AgendaTime.AllDay(startDate ?: java.time.LocalDate.EPOCH, endDate)
    },
    recurrence = RecurrenceRuleText.decode(recurrenceRule),
    location = location,
    colorCategory = colorCategory,
    birthYear = birthYear,
)

/** Only dated tasks are on the agenda; a repeating task shows its current instance only. */
internal fun TaskEntity.toAgendaItem(): AgendaItem? = when (val d = due) {
    is TaskDue.At -> AgendaItem(id, AgendaKind.TASK, title, AgendaTime.Timed(d.instant, null, d.zone))
    is TaskDue.OnDay -> AgendaItem(id, AgendaKind.TASK, title, AgendaTime.AllDay(d.date))
    TaskDue.None -> null
}

internal fun StandaloneReminderEntity.toAgendaItem(): AgendaItem =
    AgendaItem(id, AgendaKind.REMINDER, title, AgendaTime.Timed(Instant.ofEpochMilli(atMillis), null, zoneId))

internal fun List<EventCategory>.colorIndexOf(name: String?): Int? =
    name?.let { n -> firstOrNull { it.name.equals(n, ignoreCase = true) }?.colorIndex }
