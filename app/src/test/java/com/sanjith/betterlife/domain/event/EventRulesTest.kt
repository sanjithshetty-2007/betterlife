package com.sanjith.betterlife.domain.event

import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.reminder.ReminderOffsets
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.TaskRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId

/** Birthdays, same-time grouping, reminder defaults and colour categories. */
public class EventRulesTest {

    private val today = LocalDate.of(2026, 11, 4)

    // Birthdays

    @Test
    public fun birthdayWithUnknownYearIsStoredInALeapYearWithNoAge() {
        val start = BirthdayRules.startDate(MonthDay.of(Month.FEBRUARY, 29), null)
        assertEquals(LocalDate.of(BirthdayRules.UNKNOWN_YEAR_ANCHOR, 2, 29), start)
        assertNull(BirthdayRules.ageOn(LocalDate.of(2026, 2, 28), null))
        assertTrue(BirthdayRules.isValid(MonthDay.of(Month.FEBRUARY, 29), null, today))
    }

    @Test
    public fun birthdayWithKnownYearHasAnAge() {
        assertEquals(LocalDate.of(1999, 10, 20), BirthdayRules.startDate(MonthDay.of(Month.OCTOBER, 20), 1999))
        assertEquals(27, BirthdayRules.ageOn(LocalDate.of(2026, 10, 20), 1999))
        assertNull(BirthdayRules.ageOn(LocalDate.of(1998, 10, 20), 1999))
    }

    @Test
    public fun futureOrImpossibleBirthYearsAreRejected() {
        assertFalse(BirthdayRules.isValid(MonthDay.of(Month.JANUARY, 1), 2027, today))
        assertFalse(BirthdayRules.isValid(MonthDay.of(Month.FEBRUARY, 29), 2001, today))
        assertFalse(BirthdayRules.isValid(MonthDay.of(Month.JANUARY, 1), 1850, today))
        assertTrue(BirthdayRules.isValid(MonthDay.of(Month.FEBRUARY, 29), 2004, today))
    }

    @Test
    public fun leapDayBirthdayDateInEachYear() {
        val leapDay = MonthDay.of(Month.FEBRUARY, 29)
        assertEquals(LocalDate.of(2027, 2, 28), BirthdayRules.dateIn(leapDay, 2027))
        assertEquals(LocalDate.of(2028, 2, 29), BirthdayRules.dateIn(leapDay, 2028))
        assertEquals(RecurrenceRule.Yearly(Month.FEBRUARY, 29), BirthdayRules.rule(leapDay))
    }

    // Same-time grouping

    @Test
    public fun itemsInTheSameMinuteCollapseWithACount() {
        val t = Instant.parse("2026-11-04T08:30:00Z")
        val items = listOf("a" to t.plusSeconds(10), "b" to t.plusSeconds(50), "c" to t.plusSeconds(3_600), "d" to null, "e" to t, "f" to null)
        val groups = SameTimeGrouping.group(items) { it.second }
        assertEquals(listOf(listOf("a", "b", "e"), listOf("c"), listOf("d"), listOf("f")), groups.map { g -> g.items.map { it.first } })
        assertEquals(listOf(3, 1, 1, 1), groups.map { it.count })
        assertEquals(t, groups.first().minute)
        assertNull(groups[2].minute)
    }

    @Test
    public fun groupingAnEmptyListGivesNothing() {
        assertTrue(SameTimeGrouping.group(emptyList<Instant>()) { it }.isEmpty())
    }

    // Reminder defaults

    @Test
    public fun builtInDefaultsMatchContextSection51() {
        val d = ReminderDefaults()
        assertEquals(listOf(ReminderOffsets.DAY_1, ReminderOffsets.AT_TIME), d.forEvent(EventType.BIRTHDAY).map { it.offsetMinutes })
        assertEquals(listOf(ReminderOffsets.AT_TIME), d.forEvent(EventType.EVENT).map { it.offsetMinutes })
        assertEquals(listOf(ReminderOffsets.AT_TIME), d.forEvent(EventType.MEETING).map { it.offsetMinutes })
        assertEquals(listOf(ReminderOffsets.DAY_1, ReminderOffsets.AT_TIME), d.deadline.map { it.offsetMinutes })
    }

    @Test
    public fun ownerDefaultsApplyToNewTimedTasks() {
        val mine = ReminderDefaults(deadline = listOf(ReminderPlan(ReminderOffsets.HOURS_3, Importance.ALARM)))
        val due = TaskDue.At(Instant.parse("2026-11-05T11:30:00Z"), ZoneId.of("Asia/Kolkata"))
        assertEquals(mine.deadline, TaskRules.defaultReminders(due, mine))
        // Whole-day and undated tasks are not deadlines with a time.
        assertEquals(1, TaskRules.defaultReminders(TaskDue.OnDay(today), mine).size)
        assertTrue(TaskRules.defaultReminders(TaskDue.None, mine).isEmpty())
    }

    @Test
    public fun defaultsRoundTripThroughTheirStoredText() {
        val plans = listOf(ReminderPlan(0, Importance.FULL_SCREEN), ReminderPlan(10_080, Importance.NORMAL), ReminderPlan(45, Importance.ALARM))
        val text = ReminderDefaults.encode(plans)
        assertEquals("10080:NORMAL,45:ALARM,0:FULL_SCREEN", text)
        assertEquals(plans.sortedByDescending { it.offsetMinutes }, ReminderDefaults.decode(text))
        assertEquals(emptyList<ReminderPlan>(), ReminderDefaults.decode(""))
    }

    @Test
    public fun badStoredDefaultsFallBack() {
        assertNull(ReminderDefaults.decode(null))
        assertNull(ReminderDefaults.decode("abc"))
        assertNull(ReminderDefaults.decode("10:LOUD"))
        assertNull(ReminderDefaults.decode("-5:NORMAL"))
    }

    // Colour categories

    @Test
    public fun categoriesRoundTripAndSkipBadLines() {
        val list = listOf(EventCategory("Work", 2), EventCategory("Family", 6))
        assertEquals(list, EventCategory.decode(EventCategory.encode(list)))
        assertEquals(list, EventCategory.decode("2|Work\nnonsense\n6|Family\n|Empty\n3|work"))
        assertEquals(emptyList<EventCategory>(), EventCategory.decode(null))
    }

    @Test
    public fun upsertRecoloursAnExistingNameIgnoringCase() {
        val list = listOf(EventCategory("Work", 2))
        assertEquals(listOf(EventCategory("work", 5)), EventCategory.upsert(list, EventCategory(" work ", 5)))
        assertEquals(list + EventCategory("Gym", 1), EventCategory.upsert(list, EventCategory("Gym", 1)))
    }
}
