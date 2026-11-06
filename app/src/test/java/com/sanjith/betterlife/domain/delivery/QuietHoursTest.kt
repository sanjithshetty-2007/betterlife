package com.sanjith.betterlife.domain.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

public class QuietHoursTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(local: String): ZonedDateTime = LocalDateTime.parse(local).atZone(zone)
    private fun instant(local: String): Instant = at(local).toInstant()
    private val night = QuietHoursSettings(enabled = true, start = LocalTime.of(22, 0), end = LocalTime.of(7, 0))

    @Test
    public fun windowThatCrossesMidnightCoversBothSides() {
        // 2026-11-03 is a Tuesday.
        assertEquals(instant("2026-11-04T07:00"), QuietHours.activeUntil(night, at("2026-11-03T22:00")))
        assertEquals(instant("2026-11-04T07:00"), QuietHours.activeUntil(night, at("2026-11-03T23:59")))
        assertEquals(instant("2026-11-04T07:00"), QuietHours.activeUntil(night, at("2026-11-04T00:00")))
        assertEquals(instant("2026-11-04T07:00"), QuietHours.activeUntil(night, at("2026-11-04T06:59")))
    }

    @Test
    public fun startIsIncludedAndEndIsNot() {
        assertNull(QuietHours.activeUntil(night, at("2026-11-04T07:00")))
        assertNull(QuietHours.activeUntil(night, at("2026-11-03T21:59")))
        assertNull(QuietHours.activeUntil(night, at("2026-11-03T12:00")))
    }

    @Test
    public fun sameDayWindowWorksToo() {
        val lunch = QuietHoursSettings(true, LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertEquals(instant("2026-11-03T14:00"), QuietHours.activeUntil(lunch, at("2026-11-03T13:30")))
        assertNull(QuietHours.activeUntil(lunch, at("2026-11-03T14:00")))
    }

    @Test
    public fun weekdayChoiceAppliesToTheEveningTheWindowStarts() {
        val weekdays = night.copy(days = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY)
        // Friday 22:00 starts a window that runs into Saturday morning.
        assertEquals(instant("2026-11-07T07:00"), QuietHours.activeUntil(weekdays, at("2026-11-06T23:00")))
        assertEquals(instant("2026-11-07T07:00"), QuietHours.activeUntil(weekdays, at("2026-11-07T06:00")))
        // Saturday night is not quiet; neither is early Monday (Sunday's window is off).
        assertNull(QuietHours.activeUntil(weekdays, at("2026-11-07T23:00")))
        assertNull(QuietHours.activeUntil(weekdays, at("2026-11-09T06:00")))
        // Monday night is quiet again.
        assertEquals(instant("2026-11-10T07:00"), QuietHours.activeUntil(weekdays, at("2026-11-09T22:30")))
    }

    @Test
    public fun offOrZeroLengthMeansNeverQuiet() {
        assertNull(QuietHours.activeUntil(night.copy(enabled = false), at("2026-11-03T23:00")))
        assertNull(QuietHours.activeUntil(night.copy(end = LocalTime.of(22, 0)), at("2026-11-03T22:00")))
        assertNull(QuietHours.activeUntil(night.copy(days = emptySet()), at("2026-11-03T23:00")))
    }

    @Test
    public fun crossesTheEndOfAMonthAndYear() {
        assertEquals(instant("2027-01-01T07:00"), QuietHours.activeUntil(night, at("2026-12-31T23:30")))
        assertEquals(instant("2026-12-01T07:00"), QuietHours.activeUntil(night, at("2026-12-01T03:00")))
    }
}
