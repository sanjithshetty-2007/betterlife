package com.sanjith.betterlife.domain.tracker

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When a tracker's daily reminder fires (protein and sleep, owner's features, 2026-11-21):
 * 1. at the chosen time it rings for [RING] (10 s), then the sound and vibration stop;
 * 2. the pop-up stays up to [POPUP] (2 min) from the moment it fired, with no way to dismiss it;
 * 3. unanswered, it comes back [SNOOZE] (15 min) after closing, until the day is logged;
 * 4. repeats stop at midnight; the next day starts again at the chosen time.
 * Logging the day (pop-up or dashboard) stops that day's reminders.
 *
 * Example: time 20:00, never answered: 20:00, 20:17, 20:34 ... 23:43; 00:00 is past midnight, so the
 * next one is 20:00 the next day.
 */
public object TrackerReminderRules {
    public val RING: Duration = Duration.ofSeconds(10)
    public val POPUP: Duration = Duration.ofMinutes(2)
    public val SNOOZE: Duration = Duration.ofMinutes(15)

    /** The next pop-up after one shown at [firedAt]: closed after [POPUP], then [SNOOZE]. Null past midnight. */
    public fun repeatAfterFire(firedAt: Instant, zone: ZoneId): Instant? = beforeMidnight(firedAt, firedAt.plus(POPUP).plus(SNOOZE), zone)

    /** In a phone call nothing is shown; it tries again after [SNOOZE]. Null past midnight. */
    public fun repeatAfterCall(now: Instant, zone: ZoneId): Instant? = beforeMidnight(now, now.plus(SNOOZE), zone)

    /**
     * The next alarm. [repeatAt] is a pending repeat (kept on disk): it wins while it is still in the
     * calendar day it was set for and its day is not logged; if its time passed (the phone was off)
     * it fires now. Otherwise the next [time] whose day is not logged. [logged] answers for a tracker
     * day; [dayOf] turns an instant into the day it asks about (rollover hour, and for sleep last night).
     */
    public fun nextFire(
        now: Instant,
        zone: ZoneId,
        time: LocalTime,
        repeatAt: Instant?,
        dayOf: (Instant) -> LocalDate,
        logged: (LocalDate) -> Boolean,
    ): Instant {
        if (repeatAt != null && now.isBefore(midnightAfter(repeatAt, zone))) {
            val at = maxOf(now, repeatAt)
            if (!logged(dayOf(at))) return at
        }
        var candidate = ZonedDateTime.of(now.atZone(zone).toLocalDate(), time, zone)
        if (!candidate.toInstant().isAfter(now)) candidate = candidate.plusDays(1)
        if (logged(dayOf(candidate.toInstant()))) candidate = candidate.plusDays(1)
        return candidate.toInstant()
    }

    private fun beforeMidnight(from: Instant, at: Instant, zone: ZoneId): Instant? = at.takeIf { it.isBefore(midnightAfter(from, zone)) }

    private fun midnightAfter(instant: Instant, zone: ZoneId): Instant =
        instant.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
}
