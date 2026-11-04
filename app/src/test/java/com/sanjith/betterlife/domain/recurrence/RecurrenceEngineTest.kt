package com.sanjith.betterlife.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

public class RecurrenceEngineTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    public fun dailyAndCustomInterval() {
        assertEquals(d(2026, 10, 9), RecurrenceEngine.nextOccurrence(RecurrenceRule.Daily(), d(2026, 10, 1), d(2026, 10, 8)))
        assertEquals(d(2026, 10, 10), RecurrenceEngine.nextOccurrence(RecurrenceRule.Daily(3), d(2026, 10, 1), d(2026, 10, 8)))
    }

    @Test
    public fun anchorItselfIsAnOccurrence() {
        assertEquals(d(2026, 10, 1), RecurrenceEngine.nextOccurrence(RecurrenceRule.Daily(), d(2026, 10, 1), d(2026, 9, 1)))
    }

    @Test
    public fun weeklyOnChosenDays() {
        val rule = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val between = RecurrenceEngine.occurrencesBetween(rule, d(2026, 10, 8), d(2026, 10, 1), d(2026, 10, 21))
        assertEquals(listOf(d(2026, 10, 12), d(2026, 10, 14), d(2026, 10, 19), d(2026, 10, 21)), between)
    }

    @Test
    public fun everyTwoWeeksCountsFromTheAnchorWeek() {
        val rule = RecurrenceRule.Weekly(setOf(DayOfWeek.FRIDAY), interval = 2)
        val between = RecurrenceEngine.occurrencesBetween(rule, d(2026, 10, 9), d(2026, 10, 1), d(2026, 11, 10))
        assertEquals(listOf(d(2026, 10, 9), d(2026, 10, 23), d(2026, 11, 6)), between)
    }

    @Test
    public fun everyWeekdaySkipsTheWeekend() {
        val between = RecurrenceEngine.occurrencesBetween(RecurrenceRule.Weekdays, d(2026, 10, 8), d(2026, 10, 8), d(2026, 10, 13))
        assertEquals(listOf(d(2026, 10, 8), d(2026, 10, 9), d(2026, 10, 12), d(2026, 10, 13)), between)
    }

    @Test
    public fun monthlyOnThe31stThroughAFullYear() {
        val between = RecurrenceEngine.occurrencesBetween(
            RecurrenceRule.Monthly(31), d(2026, 1, 31), d(2026, 1, 1), d(2026, 12, 31),
        )
        val expected = listOf(
            d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31), d(2026, 6, 30),
            d(2026, 7, 31), d(2026, 8, 31), d(2026, 9, 30), d(2026, 10, 31), d(2026, 11, 30), d(2026, 12, 31),
        )
        assertEquals(expected, between)
    }

    @Test
    public fun monthlyOn31stInALeapYearFebruaryIs29th() {
        assertEquals(d(2028, 2, 29), RecurrenceEngine.nextOccurrence(RecurrenceRule.Monthly(31), d(2028, 1, 31), d(2028, 1, 31)))
    }

    @Test
    public fun monthlyDoesNotDriftAfterAShortMonth() {
        // After 28 Feb the next is 31 Mar, not 28 Mar.
        assertEquals(d(2026, 3, 31), RecurrenceEngine.nextOccurrence(RecurrenceRule.Monthly(31), d(2026, 1, 31), d(2026, 2, 28)))
    }

    @Test
    public fun monthlyAnchoredAfterItsDayStartsNextMonth() {
        assertEquals(d(2026, 11, 5), RecurrenceEngine.nextOccurrence(RecurrenceRule.Monthly(5), d(2026, 10, 8), d(2026, 10, 7)))
    }

    @Test
    public fun leapDayBirthdayAcross2027And2028() {
        val rule = RecurrenceRule.Yearly(Month.FEBRUARY, 29)
        val between = RecurrenceEngine.occurrencesBetween(rule, d(2024, 2, 29), d(2026, 1, 1), d(2029, 12, 31))
        assertEquals(listOf(d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 29), d(2029, 2, 28)), between)
    }

    @Test
    public fun skipOccurrenceGivesTheOneAfter() {
        assertEquals(d(2026, 10, 15), RecurrenceEngine.skipOccurrence(RecurrenceRule.Weekly(setOf(DayOfWeek.THURSDAY)), d(2026, 10, 1), d(2026, 10, 8)))
    }

    @Test
    public fun fromDueDateNextIsAfterTheDueDate() {
        val next = RecurrenceEngine.nextDue(
            RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)), anchor = d(2026, 10, 5),
            currentDue = d(2026, 10, 12), completedOn = d(2026, 10, 10), mode = RepeatMode.FROM_DUE_DATE,
        )
        assertEquals(d(2026, 10, 19), next)
    }

    @Test
    public fun fromDueDateCompletedLateDoesNotCreateABacklog() {
        val next = RecurrenceEngine.nextDue(
            RecurrenceRule.Daily(), anchor = d(2026, 10, 1),
            currentDue = d(2026, 10, 1), completedOn = d(2026, 10, 8), mode = RepeatMode.FROM_DUE_DATE,
        )
        assertEquals(d(2026, 10, 9), next)
    }

    @Test
    public fun fromCompletionCountsFromTheCompletionDay() {
        fun next(rule: RecurrenceRule) = RecurrenceEngine.nextDue(
            rule, anchor = d(2026, 9, 1), currentDue = d(2026, 10, 1), completedOn = d(2026, 10, 10),
            mode = RepeatMode.FROM_COMPLETION,
        )
        assertEquals(d(2026, 10, 13), next(RecurrenceRule.Daily(3)))
        assertEquals(d(2026, 10, 24), next(RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY), interval = 2)))
        assertEquals(d(2026, 11, 10), next(RecurrenceRule.Monthly(1)))
        assertEquals(d(2027, 10, 10), next(RecurrenceRule.Yearly(Month.SEPTEMBER, 1)))
        // 10 Oct 2026 is a Saturday: the next weekday is Monday.
        assertEquals(d(2026, 10, 12), next(RecurrenceRule.Weekdays))
    }

    @Test
    public fun fromCompletionMonthlyClampsToShortMonths() {
        val next = RecurrenceEngine.nextDue(
            RecurrenceRule.Monthly(31), anchor = d(2026, 1, 31), currentDue = d(2026, 1, 31),
            completedOn = d(2026, 1, 31), mode = RepeatMode.FROM_COMPLETION,
        )
        assertEquals(d(2026, 2, 28), next)
    }

    @Test
    public fun ignoredUnder30DaysKeepsItsOneOverdueDate() {
        val due = RecurrenceEngine.overdueInstanceDue(RecurrenceRule.Daily(), d(2026, 9, 20), d(2026, 9, 20), d(2026, 10, 8))
        assertEquals(d(2026, 9, 20), due)
    }

    @Test
    public fun ignoredFor30DaysRollsForwardToTheLatestOccurrence() {
        val daily = RecurrenceEngine.overdueInstanceDue(RecurrenceRule.Daily(), d(2026, 9, 1), d(2026, 9, 1), d(2026, 10, 8))
        assertEquals(d(2026, 10, 8), daily)
        val weekly = RecurrenceEngine.overdueInstanceDue(
            RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)), d(2026, 9, 7), d(2026, 9, 7), d(2026, 10, 8),
        )
        assertEquals(d(2026, 10, 5), weekly)
    }

    @Test
    public fun exactly30DaysRollsForward() {
        val due = RecurrenceEngine.overdueInstanceDue(RecurrenceRule.Daily(), d(2026, 9, 8), d(2026, 9, 8), d(2026, 10, 8))
        assertEquals(d(2026, 10, 8), due)
    }

    @Test
    public fun hugeRangeIsCapped() {
        val between = RecurrenceEngine.occurrencesBetween(RecurrenceRule.Daily(), d(2000, 1, 1), d(2000, 1, 1), d(2100, 1, 1))
        assertEquals(RecurrenceEngine.MAX_OCCURRENCES, between.size)
    }

    @Test
    public fun emptyRangeGivesNothing() {
        assertTrue(RecurrenceEngine.occurrencesBetween(RecurrenceRule.Daily(), d(2026, 10, 1), d(2026, 10, 9), d(2026, 10, 8)).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    public fun weeklyWithoutDaysIsRejected() {
        RecurrenceRule.Weekly(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    public fun zeroIntervalIsRejected() {
        RecurrenceRule.Daily(0)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun april31IsRejected() {
        RecurrenceRule.Yearly(Month.APRIL, 31)
    }

    @Test
    public fun ruleTextRoundTripsEveryKind() {
        listOf(
            RecurrenceRule.Daily(2),
            RecurrenceRule.Weekdays,
            RecurrenceRule.Weekly(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), 3),
            RecurrenceRule.Monthly(31),
            RecurrenceRule.Yearly(Month.FEBRUARY, 29),
        ).forEach { rule ->
            assertEquals(rule, RecurrenceRuleText.decode(RecurrenceRuleText.encode(rule)))
        }
        assertEquals("WEEKLY;DAYS=MON,WED;INTERVAL=3", RecurrenceRuleText.encode(RecurrenceRule.Weekly(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), 3)))
    }

    @Test
    public fun brokenRuleTextDecodesToNull() {
        listOf(null, "", "NOPE", "WEEKLY", "WEEKLY;DAYS=XYZ", "MONTHLY;DAY=40", "DAILY;INTERVAL=0", "YEARLY;MONTH=13;DAY=1").forEach {
            assertNull("for $it", RecurrenceRuleText.decode(it))
        }
    }
}
