package com.sanjith.betterlife.feature.events

import com.sanjith.betterlife.domain.event.BirthdayRules
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.feature.tasks.RepeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

public class EventEditorStateTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 4)
    private val defaults = ReminderDefaults()

    @Test
    public fun newEventStartsAtTheNextWholeHourForOneHourWithEventDefaults() {
        val s = EventEditorState.new(EventType.EVENT, LocalDateTime.parse("2026-11-04T14:20").atZone(zone), defaults)
        assertEquals(today, s.date)
        assertEquals(LocalTime.of(15, 0), s.startTime)
        assertEquals(LocalTime.of(16, 0), s.endTime)
        assertEquals(defaults.event, s.reminders)
    }

    @Test
    public fun newEventLateAtNightStartsTomorrow() {
        val s = EventEditorState.new(EventType.EVENT, LocalDateTime.parse("2026-11-04T23:10").atZone(zone), defaults)
        assertEquals(today.plusDays(1), s.date)
        assertEquals(LocalTime.MIDNIGHT, s.startTime)
    }

    @Test
    public fun changingTypeAppliesThatTypesDefaultsUntilRemindersAreEdited() {
        val s = EventEditorState.new(EventType.EVENT, LocalDateTime.parse("2026-11-04T14:20").atZone(zone), defaults)
        assertEquals(defaults.birthday, s.withType(EventType.BIRTHDAY, defaults).reminders)
        assertTrue(s.withType(EventType.BIRTHDAY, defaults).yearUnknown)
        assertTrue(EventEditorState.new(EventType.BIRTHDAY, LocalDateTime.parse("2026-11-04T14:20").atZone(zone), defaults).yearUnknown)
        val mine = listOf(ReminderPlan(30, Importance.ALARM))
        val edited = s.copy(reminders = mine, remindersEdited = true)
        assertEquals(mine, edited.withType(EventType.BIRTHDAY, defaults).reminders)
    }

    @Test
    public fun birthdayWithUnknownYearSavesAsYearlyAllDayInTheAnchorYear() {
        val s = EventEditorState(type = EventType.BIRTHDAY, title = "Asha", date = LocalDate.of(2026, 2, 28), yearUnknown = true)
        assertTrue(s.errors(today).isEmpty())
        val d = s.toDraft(zone)
        assertTrue(d.allDay)
        assertEquals(LocalDate.of(BirthdayRules.UNKNOWN_YEAR_ANCHOR, 2, 28), d.startDate)
        assertNull(d.birthYear)
        assertEquals(RecurrenceRule.Yearly(Month.FEBRUARY, 28), RecurrenceRuleText.decode(d.recurrenceRule))
    }

    @Test
    public fun birthdayWithKnownYearKeepsItAndRejectsAFutureYear() {
        val s = EventEditorState(type = EventType.BIRTHDAY, title = "Asha", date = LocalDate.of(1999, 10, 20))
        assertEquals(1999, s.toDraft(zone).birthYear)
        assertEquals(LocalDate.of(1999, 10, 20), s.toDraft(zone).startDate)
        assertEquals(listOf(EventEditorError.BIRTH_YEAR_INVALID), s.copy(date = LocalDate.of(2027, 1, 1)).errors(today))
    }

    @Test
    public fun birthdayIgnoresTheChosenRepeatAndTheAllDaySwitch() {
        val s = EventEditorState(type = EventType.BIRTHDAY, title = "Asha", date = LocalDate.of(1999, 10, 20),
            repeat = RepeatKind.DAILY, allDay = false)
        assertTrue(s.isAllDay)
        assertEquals(RecurrenceRule.Yearly(Month.OCTOBER, 20), s.rule())
    }

    @Test
    public fun overnightEventEndsTheNextDay() {
        val s = EventEditorState(title = "Party", date = today, startTime = LocalTime.of(22, 0), endTime = LocalTime.of(1, 0))
        val (start, end) = s.timedRange(zone)
        assertEquals(LocalDateTime.parse("2026-11-04T22:00").atZone(zone).toInstant(), start)
        assertEquals(LocalDateTime.parse("2026-11-05T01:00").atZone(zone).toInstant(), end)
    }

    @Test
    public fun movingTheStartKeepsTheLength() {
        val s = EventEditorState(title = "Gym", date = today, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30))
        assertEquals(LocalTime.of(12, 30), s.withStart(LocalTime.of(11, 0)).endTime)
        assertNull(s.copy(endTime = null).withStart(LocalTime.of(11, 0)).endTime)
    }

    @Test
    public fun weeklyMeetingRuleUsesThePickedDays() {
        val s = EventEditorState(type = EventType.MEETING, title = "Standup", date = today, repeat = RepeatKind.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), s.rule())
    }

    @Test
    public fun allDayEventEndingBeforeItStartsIsAnError() {
        val s = EventEditorState(title = "Trip", allDay = true, date = today, endDate = today.minusDays(1))
        assertEquals(listOf(EventEditorError.END_BEFORE_START), s.errors(today))
        assertNull(s.copy(endDate = today).toDraft(zone).endDate)
        assertEquals(today.plusDays(2), s.copy(endDate = today.plusDays(2)).toDraft(zone).endDate)
    }

    @Test
    public fun storedEventLoadsBackIntoTheSameFields() {
        val s = EventEditorState(type = EventType.MEETING, title = "Review", location = "Room 2", date = today,
            startTime = LocalTime.of(15, 0), endTime = LocalTime.of(16, 0), colorCategory = "Work")
        val d = s.toDraft(zone)
        val entity = com.sanjith.betterlife.data.db.entity.EventEntity(
            "e1", d.title, d.type, d.allDay, d.startAtMillis, d.endAtMillis, d.zoneId, d.startDate, d.endDate, d.birthYear,
            d.location, d.notes, d.colorCategory, d.recurrenceRule, d.importance, 0, 0, null,
            com.sanjith.betterlife.data.db.entity.SyncState.DIRTY, null,
        )
        val back = EventEditorState.from(entity, emptyList(), zone)
        assertEquals(s.copy(id = "e1", remindersEdited = true), back)
    }
}
