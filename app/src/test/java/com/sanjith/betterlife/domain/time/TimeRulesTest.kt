package com.sanjith.betterlife.domain.time

import com.sanjith.betterlife.domain.reminder.EarlyReminderCalculator
import com.sanjith.betterlife.domain.reminder.ItemTime
import com.sanjith.betterlife.domain.reminder.ReminderOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Day rollover, early reminders and time-zone changes. */
public class TimeRulesTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val london = ZoneId.of("Europe/London")

    private fun at(local: String, zone: ZoneId = kolkata) = LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test
    public fun oneAmBelongsToThePreviousDay() {
        assertEquals(LocalDate.of(2026, 10, 8), habitDay(at("2026-10-09T01:00"), kolkata, 3))
    }

    @Test
    public fun rolloverBoundaryIs0300() {
        assertEquals(LocalDate.of(2026, 10, 8), habitDay(at("2026-10-09T02:59:59"), kolkata, 3))
        assertEquals(LocalDate.of(2026, 10, 9), habitDay(at("2026-10-09T03:00"), kolkata, 3))
    }

    @Test
    public fun rolloverZeroIsPlainMidnight() {
        assertEquals(LocalDate.of(2026, 10, 9), habitDay(at("2026-10-09T00:30"), kolkata, 0))
    }

    @Test
    public fun rolloverAcrossMonthAndYearEnds() {
        assertEquals(LocalDate.of(2026, 12, 31), habitDay(at("2027-01-01T02:00"), kolkata, 3))
        assertEquals(LocalDate.of(2028, 2, 29), habitDay(at("2028-03-01T01:00"), kolkata, 3))
    }

    @Test
    public fun sameInstantGivesEachZonesLocalDay() {
        // 2026-10-08 20:00 UTC is 01:30 on the 9th in Kolkata but 21:00 on the 8th in London.
        val instant = LocalDateTime.parse("2026-10-08T20:00").atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(LocalDate.of(2026, 10, 8), habitDay(instant, kolkata, 3))
        assertEquals(LocalDate.of(2026, 10, 8), habitDay(instant, london, 3))
        assertEquals(LocalDate.of(2026, 10, 9), habitDay(instant, kolkata, 0))
    }

    @Test
    public fun fixedClockGivesTodayAndHabitToday() {
        val clock = FixedClock.of("2026-10-09T01:00", "Asia/Kolkata")
        assertEquals(LocalDate.of(2026, 10, 9), clock.today())
        assertEquals(LocalDate.of(2026, 10, 8), clock.habitToday(3))
    }

    @Test
    public fun birthdayTomorrowWithDefaultsDropsThePastOneDayReminder() {
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.AllDay(LocalDate.of(2026, 10, 9)),
            listOf(ReminderOffsets.DAY_1, ReminderOffsets.AT_TIME),
            now = at("2026-10-08T18:00"),
            deviceZone = kolkata,
        )
        assertEquals(listOf(at("2026-10-09T09:00")), fires.map { it.at })
    }

    @Test
    public fun birthdayTwoDaysAwayKeepsBothAt0900() {
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.AllDay(LocalDate.of(2026, 10, 10)),
            listOf(ReminderOffsets.AT_TIME, ReminderOffsets.DAY_1),
            now = at("2026-10-08T18:00"),
            deviceZone = kolkata,
        )
        assertEquals(listOf(at("2026-10-09T09:00"), at("2026-10-10T09:00")), fires.map { it.at })
        assertEquals(listOf(ReminderOffsets.DAY_1, ReminderOffsets.AT_TIME), fires.map { it.offsetMinutes })
    }

    @Test
    public fun eventAtMidnightCrossesIntoThePreviousDayAndMonth() {
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.At(at("2026-11-01T00:00"), kolkata),
            listOf(ReminderOffsets.MINUTES_30, ReminderOffsets.DAY_1, ReminderOffsets.WEEK_1),
            now = at("2026-10-01T00:00"),
            deviceZone = kolkata,
        )
        assertEquals(
            listOf(at("2026-10-25T00:00"), at("2026-10-31T00:00"), at("2026-10-31T23:30")),
            fires.map { it.at },
        )
    }

    @Test
    public fun allPresetsAndACustomOffset() {
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.At(at("2026-10-20T10:00"), kolkata),
            ReminderOffsets.PRESETS + 45,
            now = at("2026-10-01T00:00"),
            deviceZone = kolkata,
        )
        assertEquals(10, fires.size)
        assertEquals(at("2026-10-13T10:00"), fires.first().at)
        assertEquals(at("2026-10-20T10:00"), fires.last().at)
        assertTrue(fires.any { it.offsetMinutes == 45 && it.at == at("2026-10-20T09:15") })
    }

    @Test
    public fun reminderExactlyNowIsKeptAndDuplicatesCollapse() {
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.At(at("2026-10-08T18:00"), kolkata),
            listOf(0, 0, 10),
            now = at("2026-10-08T18:00"),
            deviceZone = kolkata,
        )
        assertEquals(listOf(0), fires.map { it.offsetMinutes })
    }

    @Test
    public fun allPastGivesNothing() {
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.At(at("2026-10-01T10:00"), kolkata),
            listOf(0, 60),
            now = at("2026-10-08T18:00"),
            deviceZone = kolkata,
        )
        assertTrue(fires.isEmpty())
    }

    @Test
    public fun dayOffsetsKeepWallTimeAcrossDaylightSaving() {
        // London clocks go back on 25 Oct 2026; "1 day before" a 09:00 item is still 09:00 local.
        val fires = EarlyReminderCalculator.fireTimes(
            ItemTime.At(at("2026-10-26T09:00", london), london),
            listOf(ReminderOffsets.DAY_1),
            now = at("2026-10-01T00:00", london),
            deviceZone = london,
        )
        assertEquals(at("2026-10-25T09:00", london), fires.single().at)
    }

    @Test
    public fun timedEventKeepsItsInstantWhenThePhoneChangesZone() {
        // An event at 10:00 Kolkata is the same moment wherever the phone is now.
        val item = ItemTime.At(at("2026-10-20T10:00"), kolkata)
        val fromKolkata = EarlyReminderCalculator.fireTimes(item, listOf(0), at("2026-10-01T00:00"), kolkata)
        val fromLondon = EarlyReminderCalculator.fireTimes(item, listOf(0), at("2026-10-01T00:00"), london)
        assertEquals(fromKolkata, fromLondon)
    }

    @Test
    public fun allDayBirthdayFollowsThePhonesNewZone() {
        // Birthdays have no zone: they fire at 09:00 wherever the phone is.
        val item = ItemTime.AllDay(LocalDate.of(2026, 10, 20))
        val fire = EarlyReminderCalculator.fireTimes(item, listOf(0), at("2026-10-01T00:00"), london).single()
        assertEquals(at("2026-10-20T09:00", london), fire.at)
    }
}
