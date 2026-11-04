package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import java.time.LocalDate
import java.time.ZoneId

// What screens hand to repositories when creating items. Ids, timestamps and sync state are added by
// the repository, so callers can never forget them.

public data class TaskDraft(
    val title: String,
    val notes: String? = null,
    val dueAtMillis: Long? = null,
    val dueZoneId: ZoneId? = null,
    val dueDate: LocalDate? = null,
    val priority: Priority = Priority.NONE,
    val projectId: String? = null,
    val parentTaskId: String? = null,
    val category: String? = null,
    val recurrenceRule: String? = null,
    val repeatFromCompletion: Boolean = false,
    val importance: Importance = Importance.NORMAL,
) {
    init {
        require(title.isNotBlank()) { "A task needs a title" }
        require(dueAtMillis == null || dueDate == null) { "A task is due at an instant or on a day, not both" }
        require((dueAtMillis == null) == (dueZoneId == null)) { "dueAtMillis and dueZoneId go together" }
    }
}

public data class EventDraft(
    val title: String,
    val type: EventType = EventType.EVENT,
    val allDay: Boolean = false,
    val startAtMillis: Long? = null,
    val endAtMillis: Long? = null,
    val zoneId: ZoneId? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val birthYear: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    val colorCategory: String? = null,
    val recurrenceRule: String? = null,
    val importance: Importance = Importance.NORMAL,
) {
    init {
        require(title.isNotBlank()) { "An event needs a title" }
        if (allDay) {
            require(startDate != null && startAtMillis == null) { "All-day events use dates, not instants" }
        } else {
            require(startAtMillis != null && zoneId != null && startDate == null) { "Timed events need an instant and a zone" }
        }
        require(endAtMillis == null || startAtMillis == null || endAtMillis >= startAtMillis) { "An event cannot end before it starts" }
    }
}

/** One early reminder: [offsetMinutes] before the item's time (0 = at time). */
public data class ReminderDraft(
    val offsetMinutes: Int,
    val importance: Importance = Importance.NORMAL,
) {
    init {
        require(offsetMinutes >= 0) { "Offsets count backwards from the item's time, so they are never negative" }
    }
}
