package com.sanjith.betterlife.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The habit "day" an instant belongs to. Before [rolloverHour] (local wall time) it still counts as
 * the previous day, so ticking a habit at 01:00 after a late night lands on the day you were living.
 *
 * Example: 2026-10-09 01:00 with rollover 3 -> 2026-10-08; 2026-10-09 03:00 -> 2026-10-09.
 */
public fun habitDay(instant: Instant, zone: ZoneId, rolloverHour: Int): LocalDate {
    require(rolloverHour in 0..23) { "rolloverHour must be 0 to 23: $rolloverHour" }
    val local = instant.atZone(zone).toLocalDateTime()
    return if (local.hour < rolloverHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
}

/** Today's habit day according to [clock]. */
public fun Clock.habitToday(rolloverHour: Int): LocalDate = habitDay(now(), zone, rolloverHour)
