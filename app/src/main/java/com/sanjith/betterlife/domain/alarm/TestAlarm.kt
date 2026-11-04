package com.sanjith.betterlife.domain.alarm

/** The two exact-alarm APIs compared in the Prompt 4 spike. */
public enum class AlarmKind {
    /** AlarmManager.setExactAndAllowWhileIdle: exact, fires in Doze, rate-limited in deep idle. */
    EXACT_ALLOW_WHILE_IDLE,

    /** AlarmManager.setAlarmClock: treated like a user alarm clock, the most reliable but shows an alarm icon. */
    ALARM_CLOCK,
}

/** A scheduled test alarm, persisted so the boot receiver can re-arm it. */
public data class PendingTestAlarm(
    val triggerAtMillis: Long,
    val kind: AlarmKind,
)

/** What to do with a persisted alarm after a reboot or app update. */
public sealed interface RearmPlan {
    /** The alarm time is still ahead: schedule it again unchanged. */
    public data class AtOriginalTime(val triggerAtMillis: Long) : RearmPlan

    /** The alarm time passed while the phone was off: fire shortly, and record by how much it was missed. */
    public data class Overdue(val fireAtMillis: Long, val missedByMillis: Long) : RearmPlan
}

private const val MILLIS_PER_MINUTE = 60_000L

/** Grace delay before firing an alarm that was missed while the phone was off, so boot can settle. */
public const val OVERDUE_GRACE_MILLIS: Long = 5_000L

/**
 * Trigger time [minutes] from [nowMillis].
 *
 * Example: `triggerAfterMinutes(nowMillis = 0, minutes = 5)` is 300_000.
 */
public fun triggerAfterMinutes(nowMillis: Long, minutes: Int): Long {
    require(minutes > 0) { "minutes must be positive: $minutes" }
    return nowMillis + minutes * MILLIS_PER_MINUTE
}

/**
 * Decides how to re-arm [pending] at [nowMillis] (after boot or an app update). An alarm exactly due
 * now counts as overdue, so it still fires instead of being scheduled in the past.
 *
 * Example: pending at 10:00, now 10:07 -> `Overdue(fireAt = 10:07:05, missedBy = 7 min)`.
 */
public fun planRearm(
    pending: PendingTestAlarm,
    nowMillis: Long,
    graceMillis: Long = OVERDUE_GRACE_MILLIS,
): RearmPlan =
    if (pending.triggerAtMillis > nowMillis) {
        RearmPlan.AtOriginalTime(pending.triggerAtMillis)
    } else {
        RearmPlan.Overdue(fireAtMillis = nowMillis + graceMillis, missedByMillis = nowMillis - pending.triggerAtMillis)
    }
