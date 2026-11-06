package com.sanjith.betterlife.domain.today

import com.sanjith.betterlife.domain.habit.ConsistencyCalculator
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PausePeriod
import com.sanjith.betterlife.domain.habit.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Minimum Day, comeback, the evening nudge and habit pause ranges (Prompt 17). Today = Fri 20 Nov 2026. */
public class GentleRulesTest {

    private val today = LocalDate.of(2026, 11, 20)

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    private fun habit(id: String, essential: Boolean = false, selfCare: Boolean = false, streak: Int = 0, settled: Boolean = false) =
        GentleHabit(id, id, essential, selfCare, streak, settled)

    @Test
    public fun minimumDayPicksTheTopTaskTheEssentialHabitAndADifferentSelfCareHabit() {
        val pick = MinimumDay.pick("task", listOf(habit("med", selfCare = true, streak = 9), habit("creatine", essential = true), habit("skin", selfCare = true)))
        assertEquals(MinimumDayPick("task", "creatine", "med"), pick)
    }

    @Test
    public fun withoutAnEssentialHabitTheLongestStreakAtRiskIsPicked() {
        val pick = MinimumDay.pick(null, listOf(habit("a", streak = 3), habit("b", streak = 12), habit("done", streak = 40, settled = true), habit("c")))
        assertEquals("b", pick.habitId)
        assertNull(pick.taskId)
        assertNull(pick.selfCareId)
        assertEquals(1, pick.count)
    }

    @Test
    public fun theSelfCareHabitIsNeverTheSameAsTheFirstAndMayBeMissing() {
        assertEquals(MinimumDayPick("t", "only", null), MinimumDay.pick("t", listOf(habit("only", essential = true, selfCare = true))))
        assertEquals(MinimumDayPick(null, null, null), MinimumDay.pick(null, emptyList()))
    }

    @Test
    public fun threeMissedDaysOnAHabitStartComebackAndPausedDaysDoNotCount() {
        val missed = HabitHistory(nov(1), mapOf(nov(16) to HabitLogStatus.DONE))
        // 17, 18, 19 missed (today pending): 3.
        assertEquals(3, Comeback.missedRun(missed, today))
        assertEquals(ComebackTrigger.MISSED_HABIT_DAYS, Comeback.trigger(today, today, null, listOf(missed)))

        val withPause = HabitHistory(nov(1), mapOf(nov(16) to HabitLogStatus.DONE), listOf(PausePeriod(nov(18), nov(18))))
        assertEquals(2, Comeback.missedRun(withPause, today))
        assertNull(Comeback.trigger(today, today, null, listOf(withPause)))

        val fine = HabitHistory(nov(1), (17..19).associate { nov(it) to HabitLogStatus.DONE })
        assertNull(Comeback.trigger(today, today, null, listOf(fine)))
    }

    @Test
    public fun twoDaysAwayStartComebackButOnlyOnceADay() {
        assertEquals(ComebackTrigger.AWAY_FROM_APP, Comeback.trigger(today, nov(18), null, emptyList()))
        assertNull(Comeback.trigger(today, nov(19), null, emptyList()))
        assertNull(Comeback.trigger(today, nov(10), shownOn = today, habits = emptyList()))
        assertNull(Comeback.trigger(today, null, null, emptyList()))
    }

    @Test
    public fun theMissionIsTwoOpenHabitsEssentialFirstAndTheTopTask() {
        val mission = Comeback.mission("task", listOf(habit("a"), habit("done", settled = true), habit("b", essential = true), habit("c")))
        assertEquals(listOf("b", "a", "task"), mission)
        assertEquals(listOf("a"), Comeback.mission(null, listOf(habit("a"))))
    }

    @Test
    public fun theEveningNudgeOnlyWhenSomethingIsLeft() {
        assertTrue(HabitNudge.shouldNudge(listOf(habit("a", settled = true), habit("b"))))
        assertFalse(HabitNudge.shouldNudge(listOf(habit("a", settled = true))))
        assertFalse(HabitNudge.shouldNudge(emptyList()))
    }

    @Test
    public fun aPauseRangeHoldsUntilTheRolloverAfterItsLastDay() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = Instant.parse("2026-11-20T06:00:00Z")
        assertEquals(
            Instant.parse("2026-11-22T21:30:00Z"),
            PauseRangeRules.activeUntil(listOf(PausePeriod(nov(19), nov(22))), now, zone, rolloverHour = 3),
        )
        // An open range is checked again tomorrow at the rollover hour.
        assertEquals(Instant.parse("2026-11-20T21:30:00Z"), PauseRangeRules.activeUntil(listOf(PausePeriod(nov(20), null)), now, zone, 3))
        assertNull(PauseRangeRules.activeUntil(listOf(PausePeriod(nov(21), nov(25))), now, zone, 3))
        // 01:00 on the 23rd is still the 22nd's habit day: still paused.
        assertEquals(
            Instant.parse("2026-11-22T21:30:00Z"),
            PauseRangeRules.activeUntil(listOf(PausePeriod(nov(19), nov(22))), Instant.parse("2026-11-22T19:30:00Z"), zone, 3),
        )
    }

    @Test
    public fun pausedRangeDaysKeepTheStreakAndConsistency() {
        val history = HabitHistory(nov(1), (1..14).associate { nov(it) to HabitLogStatus.DONE } + (19..20).associate { nov(it) to HabitLogStatus.DONE }, listOf(PausePeriod(nov(15), nov(18))))
        assertEquals(16, StreakCalculator.current(history, today))
        assertEquals(Consistency.Percent(100, 16, 16), ConsistencyCalculator.calculate(history, today))
    }
}
