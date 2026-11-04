package com.sanjith.betterlife.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The only source of "now" for app logic (agent.md section 3). Logic never calls
 * System.currentTimeMillis() or LocalDate.now() directly, so tests and the debug Time Machine can
 * control time.
 */
public interface Clock {
    public fun now(): Instant

    /** The phone's current time zone. Read on every call so a zone change is picked up at once. */
    public val zone: ZoneId
}

public fun Clock.zonedNow(): ZonedDateTime = now().atZone(zone)

public fun Clock.today(): LocalDate = zonedNow().toLocalDate()

public fun Clock.millis(): Long = now().toEpochMilli()

/**
 * A clock stopped at one moment, for tests.
 *
 * Example: `FixedClock.of("2026-10-08T18:00", "Asia/Kolkata")` is Thursday 8 October 2026, 18:00 local.
 */
public data class FixedClock(private val instant: Instant, override val zone: ZoneId) : Clock {
    override fun now(): Instant = instant

    public companion object {
        public fun of(localDateTime: String, zoneId: String): FixedClock {
            val zone = ZoneId.of(zoneId)
            return FixedClock(java.time.LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone)
        }
    }
}
