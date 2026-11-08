package com.sanjith.betterlife.domain.habit

import com.sanjith.betterlife.domain.habit.HabitTestData.TODAY
import com.sanjith.betterlife.domain.habit.HabitTestData.history
import com.sanjith.betterlife.domain.habit.HabitTestData.nov
import com.sanjith.betterlife.domain.habit.HabitTestData.novDays
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.time.habitToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/** Streak rules from context.md 5.4, on fixed dates (today = Fri 20 Nov 2026). */
public class StreakCalculatorTest {

    @Test
    public fun sevenConsecutiveDaysEndingTodayIsAStreakOfSeven() {
        val h = history(done = novDays(14, 20))
        assertEquals(7, StreakCalculator.current(h, TODAY))
        assertEquals(7, StreakCalculator.best(h, TODAY))
    }

    @Test
    public fun aMissedDayBreaksTheStreak() {
        val h = history(done = novDays(10, 15) + novDays(17, 20))
        assertEquals(4, StreakCalculator.current(h, TODAY))
        assertEquals(6, StreakCalculator.best(h, TODAY))
    }

    @Test
    public fun todayStillPendingKeepsYesterdaysStreakAlive() {
        assertEquals(6, StreakCalculator.current(history(done = novDays(14, 19)), TODAY))
        // Yesterday missed as well: the streak is over, and the card says "Best: 5. Restarting."
        val broken = StreakCalculator.summary(history(done = novDays(14, 18)), TODAY)
        assertEquals(StreakSummary(current = 0, best = 5), broken)
        assertTrue(broken.restarting)
    }

    @Test
    public fun aKeptMinimumDayCounts() {
        val h = history(done = novDays(14, 16) + novDays(18, 20), kept = listOf(nov(17)))
        assertEquals(7, StreakCalculator.current(h, TODAY))
    }

    @Test
    public fun aPausedDayBridgesTheGapButAddsNothing() {
        val h = history(done = novDays(14, 16) + novDays(18, 20), paused = listOf(nov(17)))
        assertEquals(6, StreakCalculator.current(h, TODAY))
        assertEquals(6, StreakCalculator.best(h, TODAY))
    }

    @Test
    public fun aPauseRangeBridgesSeveralDaysAndAnOpenOneCoversToday() {
        val closed = history(done = novDays(13, 15) + novDays(19, 20), pauses = listOf(PausePeriod(nov(16), nov(18))))
        assertEquals(5, StreakCalculator.current(closed, TODAY))
        val open = history(done = novDays(15, 17), pauses = listOf(PausePeriod(nov(18), null)))
        assertEquals(DayState.PAUSED, open.state(TODAY, TODAY))
        assertEquals(3, StreakCalculator.current(open, TODAY))
    }

    @Test
    public fun untickingADayInTheMiddleSplitsTheRunAndBothSidesAreRecomputed() {
        val full = novDays(11, 20)
        assertEquals(10, StreakCalculator.current(history(done = full), TODAY))

        val split = history(done = full - nov(15))
        assertEquals(5, StreakCalculator.current(split, TODAY))
        assertEquals(StreakRun(nov(11), nov(14), 4), StreakCalculator.runAround(split, nov(12), TODAY))
        assertEquals(StreakRun(nov(16), nov(20), 5), StreakCalculator.runAround(split, nov(17), TODAY))
        assertEquals(5, StreakCalculator.best(split, TODAY))
        assertNull(StreakCalculator.runAround(split, nov(15), TODAY))
    }

    @Test
    public fun backfillingTheMissingDayMergesTwoRunsIntoOne() {
        val before = history(done = novDays(11, 14) + novDays(16, 20))
        assertEquals(5, StreakCalculator.current(before, TODAY))

        val after = history(done = novDays(11, 20))
        assertEquals(10, StreakCalculator.current(after, TODAY))
        assertEquals(StreakRun(nov(11), nov(20), 10), StreakCalculator.runAround(after, nov(15), TODAY))
    }

    @Test
    public fun theBestStreakIsKeptAfterABreak() {
        val h = history(done = novDays(1, 12) + novDays(18, 20))
        assertEquals(StreakSummary(current = 3, best = 12), StreakCalculator.summary(h, TODAY))
        assertFalse(StreakCalculator.summary(h, TODAY).restarting)
        assertTrue(StreakCalculator.summary(history(done = novDays(1, 12)), TODAY).restarting)
    }

    @Test
    public fun daysBeforeTheStartEndTheWalkAndFutureDaysNeverCount() {
        val h = history(start = nov(18), done = novDays(18, 20))
        assertEquals(DayState.BEFORE_START, h.state(nov(17), TODAY))
        assertEquals(DayState.FUTURE, h.state(nov(21), TODAY))
        assertEquals(3, StreakCalculator.current(h, TODAY))
        assertEquals(StreakSummary(0, 0), StreakCalculator.summary(history(start = TODAY), TODAY))
    }

    @Test
    public fun onlyTodayCanBeEdited() {
        assertFalse(HabitEditRules.canEdit(nov(1), TODAY))
        assertFalse(HabitEditRules.canEdit(nov(19), TODAY))
        assertTrue(HabitEditRules.canEdit(TODAY, TODAY))
        assertFalse(HabitEditRules.canEdit(nov(21), TODAY))
        assertTrue(HabitEditRules.isBackfill(nov(19), TODAY))
        assertFalse(HabitEditRules.isBackfill(TODAY, TODAY))
    }

    @Test
    public fun tickingAtOneInTheMorningCountsForThePreviousDay() {
        val oneAm = FixedClock.of("2026-11-21T01:00", "Asia/Kolkata")
        assertEquals(nov(20), oneAm.habitToday(3))
        assertEquals(nov(21), FixedClock.of("2026-11-21T03:00", "Asia/Kolkata").habitToday(3))
        // The 01:00 tick lands on the 20th, so it extends the run that ends on the 19th.
        val h = history(done = novDays(14, 19) + oneAm.habitToday(3))
        assertEquals(7, StreakCalculator.current(h, oneAm.habitToday(3)))
    }

    @Test
    public fun streaksRunAcrossMonthYearAndLeapDayBoundaries() {
        val monthEnd = LocalDate.of(2026, 11, 3)
        val acrossMonth = HabitHistory(LocalDate.of(2026, 10, 1), dates(LocalDate.of(2026, 10, 29), monthEnd))
        assertEquals(6, StreakCalculator.current(acrossMonth, monthEnd))

        val newYear = LocalDate.of(2027, 1, 2)
        val acrossYear = HabitHistory(LocalDate.of(2026, 12, 1), dates(LocalDate.of(2026, 12, 28), newYear))
        assertEquals(6, StreakCalculator.current(acrossYear, newYear))

        val march1 = LocalDate.of(2028, 3, 1)
        val leap = HabitHistory(LocalDate.of(2028, 2, 1), dates(LocalDate.of(2028, 2, 27), march1))
        assertEquals(4, StreakCalculator.current(leap, march1))
    }

    @Test
    public fun aFullYearOfDataIsFast() {
        val start = TODAY.minusDays(365)
        // Done every day except 9, 19, 29, ... days ago: every run is 9 days long.
        val logs = (0..365).filter { it % 10 != 9 }.map { TODAY.minusDays(it.toLong()) }
        val h = HabitHistory(start, logs.associateWith { HabitLogStatus.DONE })
        var summary = StreakSummary(0, 0)
        var consistency: Consistency = Consistency.New
        val took = measureTime {
            repeat(REPEATS) {
                summary = StreakCalculator.summary(h, TODAY)
                consistency = ConsistencyCalculator.calculate(h, TODAY)
            }
        }
        assertEquals(9, summary.current)
        assertEquals(9, summary.best)
        assertEquals(Consistency.Percent(90, 27, 30), consistency)
        assertTrue("$REPEATS runs took $took", took < (REPEATS * MAX_MILLIS_PER_RUN).milliseconds)
    }

    private fun dates(from: LocalDate, to: LocalDate): Map<LocalDate, HabitLogStatus> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { HabitLogStatus.DONE }

    private companion object {
        const val REPEATS = 50
        const val MAX_MILLIS_PER_RUN = 20
    }
}
