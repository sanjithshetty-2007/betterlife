package com.sanjith.betterlife.domain.reminder

import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.task.ReminderPlan

/**
 * The reminders a new item gets unless the owner changes them (context.md 5.1). Owner-editable in
 * the Reminder defaults screen. Birthdays and deadlines: 1 day before + on the day; normal events
 * and meetings: at the time; standalone "remind me": at the time.
 */
public data class ReminderDefaults(
    val birthday: List<ReminderPlan> = listOf(
        ReminderPlan(ReminderOffsets.DAY_1, Importance.NORMAL),
        ReminderPlan(ReminderOffsets.AT_TIME, Importance.FULL_SCREEN),
    ),
    val deadline: List<ReminderPlan> = listOf(
        ReminderPlan(ReminderOffsets.DAY_1, Importance.NORMAL),
        ReminderPlan(ReminderOffsets.AT_TIME, Importance.FULL_SCREEN),
    ),
    val event: List<ReminderPlan> = listOf(ReminderPlan(ReminderOffsets.AT_TIME, Importance.FULL_SCREEN)),
    val standalone: List<ReminderPlan> = listOf(ReminderPlan(ReminderOffsets.AT_TIME, Importance.FULL_SCREEN)),
) {
    /** Defaults for a new event of [type]. */
    public fun forEvent(type: EventType): List<ReminderPlan> = when (type) {
        EventType.BIRTHDAY -> birthday
        EventType.EVENT, EventType.MEETING -> event
    }

    public companion object {
        private const val SEPARATOR = ","
        private const val PART = ":"

        /** "1440:NORMAL,0:FULL_SCREEN". An empty list is stored as an empty string. */
        public fun encode(plans: List<ReminderPlan>): String =
            plans.distinctBy { it.offsetMinutes }.sortedByDescending { it.offsetMinutes }
                .joinToString(SEPARATOR) { "${it.offsetMinutes}$PART${it.importance.name}" }

        /** Null when [text] is not valid, so the caller falls back to the built-in default. */
        public fun decode(text: String?): List<ReminderPlan>? {
            text ?: return null
            if (text.isEmpty()) return emptyList()
            return text.split(SEPARATOR).map { part ->
                val (offset, level) = part.split(PART).takeIf { it.size == 2 } ?: return null
                val minutes = offset.toIntOrNull()?.takeIf { it >= 0 } ?: return null
                val importance = Importance.entries.firstOrNull { it.name == level } ?: return null
                ReminderPlan(minutes, importance)
            }.distinctBy { it.offsetMinutes }
        }
    }
}
