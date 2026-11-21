package com.sanjith.betterlife.domain.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

public class TrackerRulesTest {

    private val today = LocalDate.parse("2026-11-19") // a Thursday

    private fun day(date: String, amount: Int, target: Int = 120) = DailyAmount(LocalDate.parse(date), amount, target)

    @Test
    public fun onlyTodayAndYesterdayCanBeEdited() {
        assertTrue(TrackerRules.canEdit(today, today))
        assertTrue(TrackerRules.canEdit(today.minusDays(1), today))
        assertFalse(TrackerRules.canEdit(today.minusDays(2), today))
        assertFalse(TrackerRules.canEdit(today.plusDays(1), today))
    }

    @Test
    public fun aDaySucceedsOnlyAtOrAboveItsOwnTarget() {
        assertTrue(day("2026-11-19", 120).met)
        assertTrue(day("2026-11-19", 150).met)
        assertFalse(day("2026-11-19", 119).met)
        // A day keeps the target it was saved with.
        assertTrue(day("2026-11-19", 100, target = 100).met)
    }

    @Test
    public fun gramsAndTargetsAreWholeNumbersInRange() {
        assertEquals(0, TrackerRules.parseGrams("0"))
        assertEquals(120, TrackerRules.parseGrams(" 120 "))
        assertNull(TrackerRules.parseGrams(""))
        assertNull(TrackerRules.parseGrams("12.5"))
        assertNull(TrackerRules.parseGrams("1000"))
        assertFalse(TrackerRules.validTarget(TrackerKind.PROTEIN, 0))
        assertTrue(TrackerRules.validTarget(TrackerKind.PROTEIN, 1))
    }

    @Test
    public fun weekAverageCountsEmptyDaysAsZeroAndLeavesOutAnUnloggedToday() {
        val days = listOf(day("2026-11-16", 120), day("2026-11-18", 90)).associateBy { it.date }

        val week = TrackerStats.week(days, today, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-01"))

        // Mon 120, Tue 0, Wed 90; today (Thu) not logged yet, so it is not counted.
        assertEquals(3, week.countedDays)
        assertEquals(70, week.average)
        assertEquals(1, week.goalDays)
        assertEquals(7, week.days.size)
    }

    @Test
    public fun todayCountsOnceLogged() {
        val days = listOf(day("2026-11-16", 120), day("2026-11-19", 130)).associateBy { it.date }

        val week = TrackerStats.week(days, today, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-01"))

        assertEquals(4, week.countedDays)
        assertEquals(63, week.average) // (120 + 0 + 0 + 130) / 4 = 62.5
        assertEquals(2, week.goalDays)
    }

    @Test
    public fun daysBeforeTrackingStartedAreNotCounted() {
        val days = listOf(day("2026-11-18", 120)).associateBy { it.date }

        val week = TrackerStats.week(days, today, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-18"))

        assertEquals(1, week.countedDays)
        assertEquals(120, week.average)
    }

    @Test
    public fun weekFollowsTheWeekStartSetting() {
        val week = TrackerStats.week(emptyMap(), today, DayOfWeek.SUNDAY, firstDay = null)

        assertEquals(LocalDate.parse("2026-11-15"), week.start)
        assertNull(week.average)
    }

    @Test
    public fun monthCoversTheCalendarMonth() {
        val month = TrackerStats.month(mapOf(today to day("2026-11-19", 120)), today, firstDay = LocalDate.parse("2026-11-01"))

        assertEquals(30, month.days.size)
        assertEquals(19, month.countedDays)
        assertEquals(1, month.goalDays)
    }

    @Test
    public fun streaksUseHabitRulesAndAMissedDayBreaksThem() {
        val days = listOf(
            day("2026-11-14", 120), // run of 2, broken by the 16th
            day("2026-11-15", 125),
            day("2026-11-16", 80),
            day("2026-11-17", 120),
            day("2026-11-18", 140),
        )

        val streaks = TrackerStats.streaks(days, today, firstDay = LocalDate.parse("2026-11-14"))

        // Today is still pending, so the streak counts through yesterday.
        assertEquals(2, streaks.current)
        assertEquals(2, streaks.best)
        assertEquals(4, streaks.totalSuccessDays)
    }

    @Test
    public fun firstDayIsTheGoalDayOrAnEarlierEntry() {
        val goal = LocalDate.parse("2026-11-10")
        assertEquals(goal, TrackerRules.firstDay(goal, listOf(day("2026-11-12", 100))))
        assertEquals(LocalDate.parse("2026-11-08"), TrackerRules.firstDay(goal, listOf(day("2026-11-08", 100))))
        assertNull(TrackerRules.firstDay(null, emptyList()))
    }

    // Sleep: a night counts for the day you went to bed, logged the morning after.

    @Test
    public fun sleepLatestDayIsLastNightAndOnlyItAndTheNightBeforeCanBeEdited() {
        val saturday = LocalDate.parse("2026-11-21")
        val latest = TrackerRules.latestDay(TrackerKind.SLEEP, saturday)

        assertEquals(LocalDate.parse("2026-11-20"), latest) // Fri night to Sat morning is Friday
        assertTrue(TrackerRules.canEdit(latest, latest))
        assertTrue(TrackerRules.canEdit(LocalDate.parse("2026-11-19"), latest))
        assertFalse(TrackerRules.canEdit(LocalDate.parse("2026-11-18"), latest))
        assertFalse(TrackerRules.canEdit(saturday, latest)) // tonight has not happened
        assertEquals(saturday, TrackerRules.latestDay(TrackerKind.PROTEIN, saturday))
    }

    @Test
    public fun sleepIsTypedAsHoursAndMinutes() {
        assertEquals(450, TrackerRules.parseDuration("7", "30"))
        assertEquals(480, TrackerRules.parseDuration("8", ""))
        assertEquals(45, TrackerRules.parseDuration("", "45"))
        assertEquals(0, TrackerRules.parseDuration("0", "0"))
        assertNull(TrackerRules.parseDuration("", ""))
        assertNull(TrackerRules.parseDuration("24", "0"))
        assertNull(TrackerRules.parseDuration("7", "60"))
        assertTrue(TrackerRules.validAmount(TrackerKind.SLEEP, 1_439))
        assertFalse(TrackerRules.validAmount(TrackerKind.SLEEP, 1_440))
    }

    @Test
    public fun sleepStreakWaitsForLastNightAndAShortNightBreaksIt() {
        val latest = LocalDate.parse("2026-11-20") // logged on Sat 21
        val nights = listOf(
            DailyAmount(LocalDate.parse("2026-11-17"), 480, 480),
            DailyAmount(LocalDate.parse("2026-11-18"), 390, 480), // short night
            DailyAmount(LocalDate.parse("2026-11-19"), 500, 480),
        )

        val streaks = TrackerStats.streaks(nights, latest, firstDay = LocalDate.parse("2026-11-17"))

        // Last night (20th) not logged yet: pending, so the streak is the 19th alone.
        assertEquals(1, streaks.current)
        assertEquals(1, streaks.best)
        assertEquals(2, streaks.totalSuccessDays)
    }

    @Test
    public fun sleepWeekAveragesNightsWithUnloggedNightsAsZero() {
        val latest = LocalDate.parse("2026-11-20") // Friday
        val nights = listOf(
            DailyAmount(LocalDate.parse("2026-11-16"), 480, 480),
            DailyAmount(LocalDate.parse("2026-11-20"), 420, 480),
        ).associateBy { it.date }

        val week = TrackerStats.week(nights, latest, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-01"))

        // Mon 480, Tue 0, Wed 0, Thu 0, Fri (last night, logged) 420: (480 + 420) / 5 = 180.
        assertEquals(5, week.countedDays)
        assertEquals(180, week.average)
        assertEquals(1, week.goalDays)
    }
}
