package com.sanjith.betterlife.domain.reminder

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.recurrence.RecurrenceEngine
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Which AlarmManager call to use (owner decision 2026-11-04, from the Prompt 4 results). */
public enum class AlarmKind {
    /** setAlarmClock: FULL_SCREEN and ALARM levels; proven on every Prompt 4 gate row. */
    ALARM_CLOCK,

    /** setExactAndAllowWhileIdle: NORMAL level; no status-bar alarm icon. */
    EXACT_WHILE_IDLE,
}

public fun alarmKindFor(importance: Importance): AlarmKind =
    if (importance == Importance.NORMAL) AlarmKind.EXACT_WHILE_IDLE else AlarmKind.ALARM_CLOCK

/**
 * One reminder as the planner sees it. [itemTime] null means the item has no time, so nothing fires.
 * [scheduledFireAt] is the alarm currently set (if any); [lastFiredFireAt] the last one delivered.
 */
public data class ScheduleTarget(
    val reminderId: String,
    val importance: Importance,
    val offsetMinutes: Int,
    val itemTime: ItemTime?,
    val recurrence: RecurrenceRule?,
    val itemDone: Boolean,
    val scheduledFireAt: Instant?,
    val lastFiredFireAt: Instant?,
)

/** An alarm to set ([fireAt] non-null) or to cancel ([fireAt] null) for one reminder. */
public data class PlannedAlarm(val reminderId: String, val fireAt: Instant?, val kind: AlarmKind)

/** A reminder whose alarm time passed without being delivered (phone off, app stopped). */
public data class MissedReminder(val target: ScheduleTarget, val missedFireAt: Instant)

public data class SchedulePlan(val alarms: List<PlannedAlarm>, val missed: List<MissedReminder>)

/**
 * Decides which alarms should exist right now. Pure, so the window, the alarm cap, recurring items and
 * missed reminders are all unit tested.
 */
public object SchedulePlanner {

    /** Only alarms in the next 30 days are set; a daily job tops the window up (agent.md section 5). */
    public val WINDOW: Duration = Duration.ofDays(30)

    /** Android allows roughly 500 pending alarms per app; stay well below it. */
    public const val MAX_ALARMS: Int = 400

    /** An alarm only this late is still set (it fires at once) instead of being reported as missed. */
    public val MISSED_GRACE: Duration = Duration.ofMinutes(2)

    /** How many occurrences of a repeating item to look through for the next fire time. */
    private const val MAX_OCCURRENCES_SCANNED = 400

    /**
     * Example: one NORMAL reminder at time for a task due tomorrow 09:00 -> one EXACT_WHILE_IDLE alarm
     * tomorrow 09:00, nothing missed.
     */
    public fun plan(
        targets: List<ScheduleTarget>,
        now: Instant,
        deviceZone: ZoneId,
        allDayFireTime: LocalTime,
    ): SchedulePlan {
        val missed = mutableListOf<MissedReminder>()
        val wanted = targets.map { target ->
            val fireAt = if (target.itemDone) {
                null
            } else {
                firstPendingOrNext(target, now, deviceZone, allDayFireTime, missed)
            }
            PlannedAlarm(target.reminderId, fireAt?.takeIf { !it.isAfter(now.plus(WINDOW)) }, alarmKindFor(target.importance))
        }
        // Keep only the earliest alarms under the cap; later ones are set by a future top-up.
        val keep = wanted.filter { it.fireAt != null }.sortedBy { it.fireAt }.take(MAX_ALARMS).map { it.reminderId }.toSet()
        val alarms = wanted.map { if (it.reminderId in keep) it else it.copy(fireAt = null) }
        return SchedulePlan(alarms, missed)
    }

    private fun firstPendingOrNext(
        target: ScheduleTarget,
        now: Instant,
        zone: ZoneId,
        allDayFireTime: LocalTime,
        missed: MutableList<MissedReminder>,
    ): Instant? {
        val pending = target.scheduledFireAt?.takeIf { it != target.lastFiredFireAt }
        if (pending != null && pending.isBefore(now)) {
            // Just late: set it again and it fires immediately. Too late: report it as missed.
            if (!pending.isBefore(now.minus(MISSED_GRACE))) return pending
            missed += MissedReminder(target, pending)
        }
        return nextFireTime(target, now, zone, allDayFireTime)
    }

    /**
     * The next fire instant at or after [now], never the one already delivered. One-off items have at
     * most one; repeating items look at their occurrences in order and use only the next one.
     */
    public fun nextFireTime(target: ScheduleTarget, now: Instant, zone: ZoneId, allDayFireTime: LocalTime): Instant? {
        val itemTime = target.itemTime ?: return null
        val candidates: Sequence<ItemTime> = when (val rule = target.recurrence) {
            null -> sequenceOf(itemTime)
            else -> occurrenceTimes(itemTime, rule, earliestDate(now, zone, target.offsetMinutes))
        }
        return candidates.take(MAX_OCCURRENCES_SCANNED)
            .mapNotNull { EarlyReminderCalculator.fireTimes(it, listOf(target.offsetMinutes), now, zone, allDayFireTime).firstOrNull()?.at }
            .firstOrNull { fireAt -> target.lastFiredFireAt == null || fireAt.isAfter(target.lastFiredFireAt) }
    }

    // Occurrences before this date cannot produce a fire time at or after now.
    private fun earliestDate(now: Instant, zone: ZoneId, offsetMinutes: Int): LocalDate =
        now.atZone(zone).toLocalDate().minusDays(Duration.ofMinutes(offsetMinutes.toLong()).toDays() + 1)

    private fun occurrenceTimes(itemTime: ItemTime, rule: RecurrenceRule, fromDate: LocalDate): Sequence<ItemTime> =
        when (itemTime) {
            is ItemTime.AllDay -> RecurrenceEngine.occurrences(rule, itemTime.date)
                .dropWhile { it.isBefore(fromDate) }
                .map { ItemTime.AllDay(it) }
            is ItemTime.At -> {
                // A repeating timed item keeps its local time of day on every occurrence.
                val local = itemTime.instant.atZone(itemTime.zone)
                RecurrenceEngine.occurrences(rule, local.toLocalDate())
                    .dropWhile { it.isBefore(fromDate) }
                    .map { date -> ItemTime.At(date.atTime(local.toLocalTime()).atZone(itemTime.zone).toInstant(), itemTime.zone) }
            }
        }
}
