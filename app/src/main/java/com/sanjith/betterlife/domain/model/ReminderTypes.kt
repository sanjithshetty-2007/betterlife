package com.sanjith.betterlife.domain.model

// Stored in the database by name. Renaming a constant breaks existing rows: add, never rename.

public enum class Priority { NONE, LOW, MEDIUM, HIGH }

/** How loudly a reminder interrupts (context.md section 5.1). */
public enum class Importance { NORMAL, FULL_SCREEN, ALARM }

public enum class EventType { EVENT, MEETING, BIRTHDAY }

/** What a reminder belongs to. STANDALONE reminders ("remind me") have no linked item. */
public enum class ReminderItemType { TASK, EVENT, STANDALONE }

/** Current state of a reminder row (fire state is local only, never synced). */
public enum class ReminderState { SCHEDULED, FIRED, SNOOZED, COMPLETED, DISMISSED, MISSED, HELD, FAILED, CANCELLED }

/** What happened at one firing, as shown in the Reminder Center. */
public enum class ReminderLogStatus { SCHEDULED, FIRED, COMPLETED, SNOOZED, RESCHEDULED, DISMISSED, MISSED, HELD, FAILED }

/** Why a reminder was held, changed or not shown (context.md section 5.1). */
public enum class ReasonCode {
    QUIET_HOURS,
    PAUSED,
    FATIGUE_GROUPED,
    FATIGUE_DOWNGRADED,
    IN_CALL,
    PERMISSION_MISSING,
    ITEM_COMPLETED,
    ITEM_DELETED,
    DEVICE_OFF,
    EXACT_ALARM_DENIED,
}
