package com.sanjith.betterlife.domain.reminder

import com.sanjith.betterlife.domain.model.ReminderLogStatus
import java.time.Duration
import java.time.Instant

/** The Reminder Center filter chips (Prompt 11). ALL shows every status. */
public enum class CenterFilter(public val status: ReminderLogStatus?) {
    ALL(null),
    SCHEDULED(ReminderLogStatus.SCHEDULED),
    FIRED(ReminderLogStatus.FIRED),
    COMPLETED(ReminderLogStatus.COMPLETED),
    SNOOZED(ReminderLogStatus.SNOOZED),
    RESCHEDULED(ReminderLogStatus.RESCHEDULED),
    DISMISSED(ReminderLogStatus.DISMISSED),
    MISSED(ReminderLogStatus.MISSED),
    HELD(ReminderLogStatus.HELD),
    FAILED(ReminderLogStatus.FAILED),
    ;

    public fun matches(status: ReminderLogStatus): Boolean = this.status == null || this.status == status
}

/** Rules for the reminder log: how long rows are kept, and the snooze count stored in a row. */
public object ReminderLogRules {

    /** context.md 5.1: the Reminder Center keeps 60 days. */
    public val RETENTION: Duration = Duration.ofDays(60)

    /**
     * Rows that fired before this instant are purged.
     * Example: now 2026-11-06T10:00Z -> 2026-09-07T10:00Z (a row exactly at the cutoff is kept).
     */
    public fun purgeCutoff(now: Instant): Instant = now.minus(RETENTION)

    private const val SNOOZE_PREFIX = "count "
    private const val SNOOZE_UNTIL = " until "

    /** The detail text the engine writes for a snooze: "count 2 until 2026-11-06T10:10:00Z". */
    public fun snoozeDetail(count: Int, until: Instant): String = "$SNOOZE_PREFIX$count$SNOOZE_UNTIL$until"

    /** The snooze count from [snoozeDetail] text; null for any other text. */
    public fun snoozeCount(detail: String?): Int? =
        detail?.takeIf { it.startsWith(SNOOZE_PREFIX) }
            ?.removePrefix(SNOOZE_PREFIX)
            ?.substringBefore(SNOOZE_UNTIL)
            ?.toIntOrNull()
}
