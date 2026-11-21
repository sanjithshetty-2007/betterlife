package com.sanjith.betterlife.domain.protein

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

public class ProteinRulesTest {

    private val today = LocalDate.parse("2026-11-19") // a Thursday

    private fun day(date: String, grams: Int, target: Int = 120) = ProteinDay(LocalDate.parse(date), grams, target)

    @Test
    public fun onlyTodayAndYesterdayCanBeEdited() {
        assertTrue(ProteinRules.canEdit(today, today))
        assertTrue(ProteinRules.canEdit(today.minusDays(1), today))
        assertFalse(ProteinRules.canEdit(today.minusDays(2), today))
        assertFalse(ProteinRules.canEdit(today.plusDays(1), today))
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
        assertEquals(0, ProteinRules.parseGrams("0"))
        assertEquals(120, ProteinRules.parseGrams(" 120 "))
        assertNull(ProteinRules.parseGrams(""))
        assertNull(ProteinRules.parseGrams("12.5"))
        assertNull(ProteinRules.parseGrams("1000"))
        assertNull(ProteinRules.parseTarget("0"))
        assertEquals(1, ProteinRules.parseTarget("1"))
    }

    @Test
    public fun weekAverageCountsEmptyDaysAsZeroAndLeavesOutAnUnloggedToday() {
        val days = listOf(day("2026-11-16", 120), day("2026-11-18", 90)).associateBy { it.date }

        val week = ProteinStats.week(days, today, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-01"))

        // Mon 120, Tue 0, Wed 90; today (Thu) not logged yet, so it is not counted.
        assertEquals(3, week.countedDays)
        assertEquals(70, week.averageGrams)
        assertEquals(1, week.goalDays)
        assertEquals(7, week.days.size)
    }

    @Test
    public fun todayCountsOnceLogged() {
        val days = listOf(day("2026-11-16", 120), day("2026-11-19", 130)).associateBy { it.date }

        val week = ProteinStats.week(days, today, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-01"))

        assertEquals(4, week.countedDays)
        assertEquals(63, week.averageGrams) // (120 + 0 + 0 + 130) / 4 = 62.5
        assertEquals(2, week.goalDays)
    }

    @Test
    public fun daysBeforeTrackingStartedAreNotCounted() {
        val days = listOf(day("2026-11-18", 120)).associateBy { it.date }

        val week = ProteinStats.week(days, today, DayOfWeek.MONDAY, firstDay = LocalDate.parse("2026-11-18"))

        assertEquals(1, week.countedDays)
        assertEquals(120, week.averageGrams)
    }

    @Test
    public fun weekFollowsTheWeekStartSetting() {
        val week = ProteinStats.week(emptyMap(), today, DayOfWeek.SUNDAY, firstDay = null)

        assertEquals(LocalDate.parse("2026-11-15"), week.start)
        assertNull(week.averageGrams)
    }

    @Test
    public fun monthCoversTheCalendarMonth() {
        val month = ProteinStats.month(mapOf(today to day("2026-11-19", 120)), today, firstDay = LocalDate.parse("2026-11-01"))

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

        val streaks = ProteinStats.streaks(days, today, firstDay = LocalDate.parse("2026-11-14"))

        // Today is still pending, so the streak counts through yesterday.
        assertEquals(2, streaks.current)
        assertEquals(2, streaks.best)
        assertEquals(4, streaks.totalSuccessDays)
    }

    @Test
    public fun firstDayIsTheGoalDayOrAnEarlierEntry() {
        val goal = LocalDate.parse("2026-11-10")
        assertEquals(goal, ProteinRules.firstDay(goal, listOf(day("2026-11-12", 100))))
        assertEquals(LocalDate.parse("2026-11-08"), ProteinRules.firstDay(goal, listOf(day("2026-11-08", 100))))
        assertNull(ProteinRules.firstDay(null, emptyList()))
    }
}
