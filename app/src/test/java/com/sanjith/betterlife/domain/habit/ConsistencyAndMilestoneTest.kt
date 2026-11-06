package com.sanjith.betterlife.domain.habit

import com.sanjith.betterlife.domain.habit.HabitTestData.TODAY
import com.sanjith.betterlife.domain.habit.HabitTestData.history
import com.sanjith.betterlife.domain.habit.HabitTestData.nov
import com.sanjith.betterlife.domain.habit.HabitTestData.novDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Consistency % and the 7/10/15/30 milestones (context.md 5.4), today = Fri 20 Nov 2026. */
public class ConsistencyAndMilestoneTest {

    @Test
    public fun consistencyIsNewUnderSevenDaysOfData() {
        assertEquals(Consistency.New, ConsistencyCalculator.calculate(history(start = nov(16), done = novDays(16, 20)), TODAY))
        // Today pending is not data yet: 6 scheduled days.
        assertEquals(Consistency.New, ConsistencyCalculator.calculate(history(start = nov(14), done = novDays(14, 19)), TODAY))
        assertEquals(Consistency.Percent(100, 7, 7), ConsistencyCalculator.calculate(history(start = nov(14), done = novDays(14, 20)), TODAY))
    }

    @Test
    public fun pausedDaysAndDaysBeforeTheStartAreNotScheduled() {
        // Window 22 Oct..20 Nov. Before 1 Nov: not started. 5..9 Nov: paused. Today: pending.
        // Scheduled = 1..19 Nov minus 5 paused days = 14; done = 10. 10 / 14 = 71 %.
        val h = history(
            start = nov(1),
            done = novDays(1, 4) + novDays(10, 15),
            pauses = listOf(PausePeriod(nov(5), nov(9))),
        )
        assertEquals(Consistency.Percent(71, 10, 14), ConsistencyCalculator.calculate(h, TODAY))
    }

    @Test
    public fun aPausedLogDayIsLeftOutAndAKeptDayIsASuccess() {
        val h = history(start = nov(10), done = novDays(10, 15), kept = listOf(nov(17)), paused = listOf(nov(16)))
        // Scheduled: 10..15, 17, 18, 19 = 9 days (16 paused, today pending); done 7.
        assertEquals(Consistency.Percent(78, 7, 9), ConsistencyCalculator.calculate(h, TODAY))
    }

    @Test
    public fun todayCountsOnceItIsDone() {
        assertEquals(Consistency.Percent(100, 19, 19), ConsistencyCalculator.calculate(history(done = novDays(1, 19)), TODAY))
        assertEquals(Consistency.Percent(100, 20, 20), ConsistencyCalculator.calculate(history(done = novDays(1, 20)), TODAY))
    }

    @Test
    public fun onlyTheLastThirtyDaysCount() {
        val start = LocalDate.of(2026, 9, 1)
        val everyDay = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(TODAY) }.toList()
        val missedJustOutside = HabitHistory(start, (everyDay - LocalDate.of(2026, 10, 21)).associateWith { HabitLogStatus.DONE })
        assertEquals(Consistency.Percent(100, 30, 30), ConsistencyCalculator.calculate(missedJustOutside, TODAY))
        val missedJustInside = HabitHistory(start, (everyDay - LocalDate.of(2026, 10, 22)).associateWith { HabitLogStatus.DONE })
        assertEquals(Consistency.Percent(97, 29, 30), ConsistencyCalculator.calculate(missedJustInside, TODAY))
    }

    @Test
    public fun reachingSevenTodayIsAFullCelebration() {
        val outcome = MilestoneDetector.detect(StreakRun(nov(14), nov(20), 7), emptySet(), isBackfill = false)
        assertEquals(MilestoneOutcome(listOf(7), MilestoneEvent(7, CelebrationKind.FULL, nov(14))), outcome)
        assertEquals(MilestoneOutcome.NONE, MilestoneDetector.detect(StreakRun(nov(15), nov(20), 6), emptySet(), isBackfill = false))
    }

    @Test
    public fun eachMilestoneFiresOncePerStreakEvenAfterUntickThenRetick() {
        val run = StreakRun(nov(14), nov(20), 7)
        val first = MilestoneDetector.detect(run, emptySet(), isBackfill = false)
        // Untick today and tick it again: same streak start, 7 already in the ledger.
        assertEquals(MilestoneOutcome.NONE, MilestoneDetector.detect(run, first.record.toSet(), isBackfill = false))
    }

    @Test
    public fun aBackfillThatCreatesDaySevenIsQuiet() {
        val outcome = MilestoneDetector.detect(StreakRun(nov(14), nov(20), 7), emptySet(), isBackfill = true)
        assertEquals(MilestoneEvent(7, CelebrationKind.QUIET, nov(14)), outcome.event)
        assertEquals(listOf(7), outcome.record)
    }

    @Test
    public fun aMergeRecordsEveryPassedMilestoneQuietlyAndShowsOnlyTheHighest() {
        val outcome = MilestoneDetector.detect(StreakRun(nov(9), nov(20), 12), emptySet(), isBackfill = true)
        assertEquals(listOf(7, 10), outcome.record)
        assertEquals(MilestoneEvent(10, CelebrationKind.QUIET, nov(9)), outcome.event)
    }

    @Test
    public fun aMilestoneTheLedgerMissedIsQuietEvenOnToday() {
        // Day 8 today, but 7 was never recorded (for example the logs came from a restore).
        val outcome = MilestoneDetector.detect(StreakRun(nov(13), nov(20), 8), emptySet(), isBackfill = false)
        assertEquals(MilestoneEvent(7, CelebrationKind.QUIET, nov(13)), outcome.event)
    }

    @Test
    public fun laterMilestonesFireInTurn() {
        assertEquals(10, MilestoneDetector.detect(StreakRun(nov(11), nov(20), 10), setOf(7), false).event?.milestone)
        assertEquals(15, MilestoneDetector.detect(StreakRun(nov(6), nov(20), 15), setOf(7, 10), false).event?.milestone)
        val thirty = MilestoneDetector.detect(StreakRun(LocalDate.of(2026, 10, 22), nov(20), 30), setOf(7, 10, 15), false)
        assertEquals(MilestoneEvent(30, CelebrationKind.FULL, LocalDate.of(2026, 10, 22)), thirty.event)
        assertEquals(MilestoneOutcome.NONE, MilestoneDetector.detect(StreakRun(nov(1), nov(20), 45), setOf(7, 10, 15, 30), false))
    }
}
