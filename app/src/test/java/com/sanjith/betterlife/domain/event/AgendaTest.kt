package com.sanjith.betterlife.domain.event

import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

public class AgendaTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun timed(id: String, start: String, end: String?, kind: AgendaKind = AgendaKind.EVENT, rule: RecurrenceRule? = null) =
        AgendaItem(id, kind, id, AgendaTime.Timed(at(start), end?.let(::at), zone), recurrence = rule)

    private fun allDay(id: String, date: String, kind: AgendaKind = AgendaKind.EVENT, end: String? = null) =
        AgendaItem(id, kind, id, AgendaTime.AllDay(LocalDate.parse(date), end?.let(LocalDate::parse)))

    private fun build(vararg items: AgendaItem, from: String = "2026-10-12", days: Int = 14) =
        Agenda.build(items.toList(), LocalDate.parse(from), days, zone)

    private fun conflicted(vararg items: AgendaItem): Set<String> =
        build(*items).flatMap { it.entries }.filter { it.conflict }.map { it.item.id }.toSet()

    // Conflict detection

    @Test
    public fun overlappingTimedEventsConflict() {
        assertEquals(
            setOf("a", "b"),
            conflicted(timed("a", "2026-10-12T10:00", "2026-10-12T11:00"), timed("b", "2026-10-12T10:30", "2026-10-12T11:30")),
        )
    }

    @Test
    public fun oneEventInsideAnotherConflicts() {
        assertEquals(
            setOf("a", "b"),
            conflicted(timed("a", "2026-10-12T09:00", "2026-10-12T17:00"), timed("b", "2026-10-12T12:00", "2026-10-12T13:00")),
        )
    }

    @Test
    public fun touchingBoundariesDoNotConflict() {
        assertEquals(
            emptySet<String>(),
            conflicted(timed("a", "2026-10-12T10:00", "2026-10-12T11:00"), timed("b", "2026-10-12T11:00", "2026-10-12T12:00")),
        )
    }

    @Test
    public fun meetingsConflictWithEventsToo() {
        assertEquals(
            setOf("a", "m"),
            conflicted(timed("a", "2026-10-12T10:00", "2026-10-12T11:00"), timed("m", "2026-10-12T10:15", "2026-10-12T10:45", AgendaKind.MEETING)),
        )
    }

    @Test
    public fun allDayAndTimedNeverConflict() {
        assertEquals(
            emptySet<String>(),
            conflicted(allDay("trip", "2026-10-12"), timed("a", "2026-10-12T10:00", "2026-10-12T11:00"), allDay("other", "2026-10-12")),
        )
    }

    @Test
    public fun tasksBirthdaysAndRemindersNeverConflict() {
        assertEquals(
            emptySet<String>(),
            conflicted(
                timed("a", "2026-10-12T10:00", "2026-10-12T11:00"),
                timed("task", "2026-10-12T10:30", null, AgendaKind.TASK),
                timed("remind", "2026-10-12T10:30", null, AgendaKind.REMINDER),
                allDay("bday", "2026-10-12", AgendaKind.BIRTHDAY),
            ),
        )
    }

    @Test
    public fun eventsWithoutEndConflictOnlyWhenTheMomentIsCovered() {
        // Same start minute: conflict. A moment inside another event: conflict. A moment at its end: none.
        assertEquals(setOf("a", "b"), conflicted(timed("a", "2026-10-12T10:00", null), timed("b", "2026-10-12T10:00", null)))
        assertEquals(setOf("a", "b"), conflicted(timed("a", "2026-10-12T10:00", "2026-10-12T11:00"), timed("b", "2026-10-12T10:30", null)))
        assertEquals(emptySet<String>(), conflicted(timed("a", "2026-10-12T10:00", "2026-10-12T11:00"), timed("b", "2026-10-12T11:00", null)))
    }

    @Test
    public fun onlyTheOverlappingOccurrenceOfARepeatingEventConflicts() {
        val weekly = timed("standup", "2026-10-12T10:00", "2026-10-12T10:30", AgendaKind.MEETING, RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)))
        val oneOff = timed("dentist", "2026-10-19T10:15", "2026-10-19T11:00")
        val rows = build(weekly, oneOff).flatMap { it.entries }
        assertEquals(listOf(LocalDate.parse("2026-10-19")), rows.filter { it.conflict && it.item.id == "standup" }.map { it.date })
        assertFalse(rows.single { it.item.id == "standup" && it.date == LocalDate.parse("2026-10-12") }.conflict)
    }

    @Test
    public fun overlapRuleDirectly() {
        assertTrue(ConflictDetector.overlaps(at("2026-10-12T10:00"), at("2026-10-12T11:00"), at("2026-10-12T10:59"), at("2026-10-12T12:00")))
        assertFalse(ConflictDetector.overlaps(at("2026-10-12T10:00"), at("2026-10-12T11:00"), at("2026-10-12T11:00"), at("2026-10-12T12:00")))
    }

    // Building the list

    @Test
    public fun weeklyMeetingIsExpandedInTheWindowOnly() {
        val weekly = timed("standup", "2026-10-05T10:00", "2026-10-05T10:30", AgendaKind.MEETING, RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)))
        val days = build(weekly).map { it.date }
        assertEquals(listOf(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-19")), days)
        val second = build(weekly).last().entries.single()
        assertEquals(at("2026-10-19T10:00"), second.start)
        assertEquals(at("2026-10-19T10:30"), second.end)
    }

    @Test
    public fun daysAreSortedAllDayFirstThenBirthdaysThenByTime() {
        val day = build(
            timed("late", "2026-10-12T18:00", null),
            timed("early", "2026-10-12T08:00", null),
            allDay("trip", "2026-10-12"),
            allDay("bday", "2026-10-12", AgendaKind.BIRTHDAY),
        ).single()
        assertEquals(listOf("bday", "trip", "early", "late"), day.entries.map { it.item.id })
    }

    @Test
    public fun multiDayAllDayEventShowsOnEachDayInTheWindow() {
        val days = build(allDay("trip", "2026-10-10", end = "2026-10-13")).map { it.date }
        assertEquals(listOf(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-13")), days)
    }

    @Test
    public fun pastOneOffItemsAreLeftOut() {
        assertTrue(build(timed("old", "2026-10-11T10:00", null), allDay("older", "2026-01-01")).isEmpty())
    }

    @Test
    public fun birthdayShowsTheAgeWhenTheYearIsKnown() {
        val known = AgendaItem("asha", AgendaKind.BIRTHDAY, "Asha", AgendaTime.AllDay(LocalDate.of(1999, 10, 20)),
            RecurrenceRule.Yearly(Month.OCTOBER, 20), birthYear = 1999)
        val unknown = AgendaItem("ravi", AgendaKind.BIRTHDAY, "Ravi", AgendaTime.AllDay(LocalDate.of(2000, 10, 21)),
            RecurrenceRule.Yearly(Month.OCTOBER, 21), birthYear = null)
        val rows = build(known, unknown).flatMap { it.entries }.associateBy { it.item.id }
        assertEquals(27, rows.getValue("asha").age)
        assertEquals(LocalDate.parse("2026-10-20"), rows.getValue("asha").date)
        assertEquals(null, rows.getValue("ravi").age)
        assertEquals(LocalDate.parse("2026-10-21"), rows.getValue("ravi").date)
    }

    @Test
    public fun leapDayBirthdayFallsOn28FebruaryInNonLeapYears() {
        val leap = AgendaItem("leap", AgendaKind.BIRTHDAY, "Leap", AgendaTime.AllDay(LocalDate.of(2000, 2, 29)),
            RecurrenceRule.Yearly(Month.FEBRUARY, 29), birthYear = 2000)
        val rows = build(leap, from = "2027-02-01", days = 60).flatMap { it.entries }
        assertEquals(listOf(LocalDate.parse("2027-02-28")), rows.map { it.date })
        assertEquals(27, rows.single().age)
    }
}
