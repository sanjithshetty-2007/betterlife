package com.sanjith.betterlife.domain.calendar

import com.sanjith.betterlife.domain.event.Agenda
import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.event.AgendaTime
import com.sanjith.betterlife.domain.event.BirthdayRules
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId

/** Month grids, view ranges and repeat expansion over the visible range (Prompt 18). */
public class CalendarRangeTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test
    public fun monthGridStartsOnMondayOrSundayPerSetting() {
        val monday = CalendarRanges.monthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(date(2026, 10, 26), monday.days.first())
        // 30 November 2026 is a Monday, so the grid needs a sixth week.
        assertEquals(date(2026, 12, 6), monday.days.last())
        assertEquals(6, monday.weeks.size)
        assertTrue(monday.weeks.all { it.first().dayOfWeek == DayOfWeek.MONDAY })

        val sunday = CalendarRanges.monthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(date(2026, 11, 1), sunday.days.first())
        assertEquals(date(2026, 12, 5), sunday.days.last())
        assertTrue(sunday.weeks.all { it.first().dayOfWeek == DayOfWeek.SUNDAY })
        assertFalse(sunday.inMonth(date(2026, 12, 1)))
    }

    @Test
    public fun leapFebruaryAndAFourWeekFebruary() {
        val leap = CalendarRanges.monthGrid(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        assertEquals(29, leap.days.count { leap.inMonth(it) })
        assertEquals(date(2028, 1, 31), leap.days.first())
        assertEquals(date(2028, 3, 5), leap.days.last())

        val exact = CalendarRanges.monthGrid(YearMonth.of(2027, 2), DayOfWeek.MONDAY)
        assertEquals(4, exact.weeks.size)
        assertEquals(28, exact.days.size)
    }

    @Test
    public fun sixWeekMonthWhenTheFirstFallsLate() {
        // August 2026 starts on a Saturday and has 31 days.
        assertEquals(6, CalendarRanges.monthGrid(YearMonth.of(2026, 8), DayOfWeek.MONDAY).weeks.size)
        assertEquals(DayOfWeek.MONDAY, CalendarRanges.weekdays(DayOfWeek.MONDAY).first())
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), CalendarRanges.weekdays(DayOfWeek.SUNDAY).take(2))
    }

    @Test
    public fun rangesAndStepsPerView() {
        val anchor = date(2026, 11, 15) // a Sunday
        assertEquals(DateRange(anchor, anchor), CalendarRanges.range(CalendarView.DAY, anchor, DayOfWeek.MONDAY))
        assertEquals(DateRange(date(2026, 11, 9), date(2026, 11, 15)), CalendarRanges.range(CalendarView.WEEK, anchor, DayOfWeek.MONDAY))
        assertEquals(DateRange(date(2026, 11, 15), date(2026, 11, 21)), CalendarRanges.range(CalendarView.WEEK, anchor, DayOfWeek.SUNDAY))
        assertEquals(60, CalendarRanges.range(CalendarView.AGENDA, anchor, DayOfWeek.MONDAY).days)
        assertEquals(date(2027, 2, 28), CalendarRanges.shift(CalendarView.MONTH, date(2027, 1, 31), 1))
        assertEquals(date(2026, 12, 31), CalendarRanges.shift(CalendarView.WEEK, date(2027, 1, 7), -1))
        assertEquals(date(2027, 1, 1), CalendarRanges.shift(CalendarView.DAY, date(2026, 12, 31), 1))
    }

    @Test
    public fun aWeeklyMeetingIsExpandedAcrossTheYearBoundaryOnlyInsideTheRange() {
        val meeting = timed("standup", AgendaKind.MEETING, date(2026, 11, 2), LocalTime.of(9, 0), RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)))
        val range = CalendarRanges.range(CalendarView.MONTH, date(2027, 1, 10), DayOfWeek.MONDAY) // 28 Dec 2026 to 31 Jan 2027
        val days = Agenda.build(listOf(meeting), range.from, range.days, zone).map { it.date }
        assertEquals(listOf(date(2026, 12, 28), date(2027, 1, 4), date(2027, 1, 11), date(2027, 1, 18), date(2027, 1, 25)), days)
    }

    @Test
    public fun monthlyOnThe31stLandsOnTheLastDayOfShortMonths() {
        val rent = allDay("rent", date(2026, 12, 31), RecurrenceRule.Monthly(31))
        val days = Agenda.build(listOf(rent), date(2027, 1, 1), 120, zone).map { it.date }
        assertEquals(listOf(date(2027, 1, 31), date(2027, 2, 28), date(2027, 3, 31), date(2027, 4, 30)), days)
    }

    @Test
    public fun birthdaysShowInFutureMonthsAndALeapDayFallsOnThe28th() {
        val mom = AgendaItem("mom", AgendaKind.BIRTHDAY, "Mom", AgendaTime.AllDay(date(2000, 2, 29)), BirthdayRules.rule(MonthDay.of(2, 29)))
        val feb = CalendarRanges.range(CalendarView.MONTH, date(2027, 2, 1), DayOfWeek.MONDAY)
        assertEquals(listOf(date(2027, 2, 28)), Agenda.build(listOf(mom), feb.from, feb.days, zone).map { it.date })
        val leapFeb = CalendarRanges.range(CalendarView.MONTH, date(2028, 2, 1), DayOfWeek.MONDAY)
        assertEquals(listOf(date(2028, 2, 29)), Agenda.build(listOf(mom), leapFeb.from, leapFeb.days, zone).map { it.date })
    }

    @Test
    public fun theFilterKeepsTasksAndHidesOtherCategories() {
        val day = date(2026, 11, 20)
        val entries = Agenda.build(
            listOf(
                timed("work", AgendaKind.MEETING, day, LocalTime.of(9, 0), category = "Work"),
                timed("fun", AgendaKind.EVENT, day, LocalTime.of(18, 0), category = "Personal"),
                timed("task", AgendaKind.TASK, day, LocalTime.of(12, 0)),
                AgendaItem("bday", AgendaKind.BIRTHDAY, "B", AgendaTime.AllDay(day)),
            ),
            day, 1, zone,
        ).single().entries
        val ids = { f: CalendarFilter -> entries.filter(f::keeps).map { it.item.id }.toSet() }
        assertEquals(setOf("work", "fun", "task", "bday"), ids(CalendarFilter()))
        assertEquals(setOf("work", "task"), ids(CalendarFilter().toggleCategory("work")))
        assertEquals(setOf("task", "bday"), ids(CalendarFilter().toggleBirthdays()))
        assertTrue(CalendarFilter().toggleCategory("Work").toggleCategory("WORK").isOff)
    }

    private fun timed(id: String, kind: AgendaKind, day: LocalDate, at: LocalTime, rule: RecurrenceRule? = null, category: String? = null) =
        AgendaItem(id, kind, id, AgendaTime.Timed(day.atTime(at).atZone(zone).toInstant(), day.atTime(at.plusHours(1)).atZone(zone).toInstant(), zone), rule, colorCategory = category)

    private fun allDay(id: String, day: LocalDate, rule: RecurrenceRule?) = AgendaItem(id, AgendaKind.EVENT, id, AgendaTime.AllDay(day), rule)
}
