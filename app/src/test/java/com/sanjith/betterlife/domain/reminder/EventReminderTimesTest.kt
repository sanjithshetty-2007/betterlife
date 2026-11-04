package com.sanjith.betterlife.domain.reminder

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

/** Early reminders for events and birthdays (Prompt 10): midnight, month ends, repeats. */
public class EventReminderTimesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val nine = LocalTime.of(9, 0)
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun fires(item: ItemTime, now: String, vararg offsets: Int) =
        EarlyReminderCalculator.fireTimes(item, offsets.toList(), at(now), zone, nine).map { it.at }

    @Test
    public fun eventAtMidnightFiresTheEveningBefore() {
        val midnight = ItemTime.At(at("2026-10-10T00:00"), zone)
        assertEquals(
            listOf(at("2026-10-09T00:00"), at("2026-10-09T23:00"), at("2026-10-09T23:30"), at("2026-10-09T23:55"), at("2026-10-10T00:00")),
            fires(midnight, "2026-10-08T12:00", 0, 5, 30, 60, 1_440),
        )
    }

    @Test
    public fun earlyRemindersCrossMonthAndYearEnds() {
        val firstOfMonth = ItemTime.At(at("2026-11-01T08:00"), zone)
        assertEquals(
            listOf(at("2026-10-25T08:00"), at("2026-10-30T08:00"), at("2026-10-31T08:00"), at("2026-11-01T05:00")),
            fires(firstOfMonth, "2026-10-20T00:00", 180, 1_440, 2_880, 10_080),
        )
        val newYear = ItemTime.AllDay(LocalDate.of(2027, 1, 1))
        assertEquals(listOf(at("2026-12-31T09:00"), at("2027-01-01T09:00")), fires(newYear, "2026-12-01T00:00", 0, 1_440))
    }

    @Test
    public fun birthdayTomorrowFiresTodayAt0900AndTomorrowAt0900() {
        val bday = ScheduleTarget("b1", Importance.NORMAL, 1_440, ItemTime.AllDay(LocalDate.of(2000, 11, 5)),
            RecurrenceRule.Yearly(Month.NOVEMBER, 5), false, null, null)
        val onDay = bday.copy(reminderId = "b0", offsetMinutes = 0)
        val plan = SchedulePlanner.plan(listOf(bday, onDay), at("2026-11-04T07:00"), zone, nine)
        assertEquals(
            mapOf("b1" to at("2026-11-04T09:00"), "b0" to at("2026-11-05T09:00")),
            plan.alarms.associate { it.reminderId to it.fireAt },
        )
    }

    @Test
    public fun leapDayBirthdayFiresOn28FebruaryInANonLeapYear() {
        val bday = ScheduleTarget("b", Importance.NORMAL, 0, ItemTime.AllDay(LocalDate.of(2000, 2, 29)),
            RecurrenceRule.Yearly(Month.FEBRUARY, 29), false, null, null)
        assertEquals(at("2027-02-28T09:00"), SchedulePlanner.nextFireTime(bday, at("2027-01-10T00:00"), zone, nine))
    }

    @Test
    public fun weeklyMeetingSchedulesOnlyItsNextOccurrence() {
        val meeting = ScheduleTarget("m", Importance.FULL_SCREEN, 10, ItemTime.At(at("2026-10-05T10:00"), zone),
            RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)), false, null, null)
        val plan = SchedulePlanner.plan(listOf(meeting), at("2026-11-04T12:00"), zone, nine)
        assertEquals(listOf(at("2026-11-09T09:50")), plan.alarms.map { it.fireAt })
    }
}
