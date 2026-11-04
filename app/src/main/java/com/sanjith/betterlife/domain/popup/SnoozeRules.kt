package com.sanjith.betterlife.domain.popup

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** The snooze choices from context.md section 5.1, in the order the pop-up shows them. */
public enum class SnoozeChoice { MINUTES_10, MINUTES_30, HOUR_1, TONIGHT, TOMORROW, PICK_TIME }

/**
 * One snooze button. [at] is when the reminder comes back (null for PICK_TIME, chosen later).
 * [allowed] is false when the option makes no sense right now (Tonight after 20:00) or would land
 * after the event has ended.
 */
public data class SnoozeOption(val choice: SnoozeChoice, val at: Instant?, val allowed: Boolean)

/** Snooze maths for the pop-up. Pure, so every boundary is unit tested. */
public object SnoozeRules {

    /** After this many snoozes of one reminder the pop-up suggests moving it to tomorrow. */
    public const val SUGGEST_MOVE_AFTER: Int = 3

    private val FIXED = mapOf(
        SnoozeChoice.MINUTES_10 to Duration.ofMinutes(10),
        SnoozeChoice.MINUTES_30 to Duration.ofMinutes(30),
        SnoozeChoice.HOUR_1 to Duration.ofHours(1),
    )

    /**
     * Example: now Thu 18:00, tonight 20:00, tomorrow 09:00 -> Tonight is today 20:00, Tomorrow is
     * Fri 09:00. At 21:00 Tonight is not allowed. Before the [rolloverHour] (after midnight)
     * "Tomorrow" means this morning: at 01:00 it is today 09:00.
     */
    public fun options(
        now: ZonedDateTime,
        tonight: LocalTime,
        tomorrow: LocalTime,
        rolloverHour: Int,
        eventEnd: Instant?,
    ): List<SnoozeOption> = SnoozeChoice.entries.map { choice ->
        val at: Instant? = when (choice) {
            SnoozeChoice.MINUTES_10, SnoozeChoice.MINUTES_30, SnoozeChoice.HOUR_1 ->
                now.toInstant().plus(FIXED.getValue(choice))
            SnoozeChoice.TONIGHT -> now.with(tonight).takeIf { it.isAfter(now) }?.toInstant()
            SnoozeChoice.TOMORROW -> tomorrowAt(now, tomorrow, rolloverHour)
            SnoozeChoice.PICK_TIME -> null
        }
        val allowed = when (choice) {
            SnoozeChoice.PICK_TIME -> true
            else -> at != null && allowedAt(at, now.toInstant(), eventEnd)
        }
        SnoozeOption(choice, at, allowed)
    }

    /** A picked wall-clock time: today if it is still ahead, otherwise tomorrow. */
    public fun pickedTime(now: ZonedDateTime, picked: LocalTime): Instant {
        val today = now.with(picked).withSecond(0).withNano(0)
        return (if (today.isAfter(now)) today else today.plusDays(1)).toInstant()
    }

    /** Snoozing is only allowed into the future and never past the end of the event. */
    public fun allowedAt(at: Instant, now: Instant, eventEnd: Instant?): Boolean =
        at.isAfter(now) && (eventEnd == null || !at.isAfter(eventEnd))

    public fun suggestMoveToTomorrow(snoozeCount: Int): Boolean = snoozeCount >= SUGGEST_MOVE_AFTER

    private fun tomorrowAt(now: ZonedDateTime, time: LocalTime, rolloverHour: Int): Instant {
        // Between midnight and the rollover hour the owner's "tomorrow" is the coming morning.
        val sameDay = now.with(time)
        return if (now.hour < rolloverHour && sameDay.isAfter(now)) sameDay.toInstant() else sameDay.plusDays(1).toInstant()
    }
}
