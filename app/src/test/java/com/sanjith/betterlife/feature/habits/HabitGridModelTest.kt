package com.sanjith.betterlife.feature.habits

import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PausePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The 31-box grid and the yearly heatmap (Prompt 15), on fixed dates. */
public class HabitGridModelTest {

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    public fun everyMonthHasThirtyOneBoxesAndShortMonthsGreyTheExtraOnes() {
        val history = HabitHistory(LocalDate.of(2025, 1, 1), emptyMap())
        val feb = HabitGridModel.boxes(history, YearMonth.of(2026, 2), today)
        assertEquals(31, feb.size)
        assertEquals(listOf(29, 30, 31), feb.filter { it.state == BoxState.NO_SUCH_DAY }.map { it.day })
        assertEquals(listOf(31), HabitGridModel.boxes(history, YearMonth.of(2026, 9), today).filter { it.date == null }.map { it.day })
        assertTrue(HabitGridModel.boxes(history, YearMonth.of(2028, 2), today).none { it.day == 29 && it.date == null })
    }

    @Test
    public fun onlyTodaysBoxCanBeTapped() {
        val history = HabitHistory(LocalDate.of(2026, 11, 10), mapOf(LocalDate.of(2026, 11, 12) to HabitLogStatus.DONE))
        val boxes = HabitGridModel.boxes(history, YearMonth.of(2026, 11), today)

        assertEquals((1..9).toList(), boxes.filter { it.state == BoxState.BEFORE_START }.map { it.day })
        assertEquals(BoxState.DONE, boxes[11].state)
        assertEquals(BoxState.OPEN, boxes[10].state)
        assertTrue(boxes[19].isToday)
        assertEquals(listOf(20), boxes.filter { it.tappable }.map { it.day })
        assertEquals((21..30).toList(), boxes.filter { it.state == BoxState.FUTURE }.map { it.day })
        assertEquals(1, HabitGridModel.doneCount(boxes))
    }

    @Test
    public fun keptAndPausedDaysShowTheirOwnState() {
        val history = HabitHistory(
            LocalDate.of(2026, 11, 1),
            mapOf(LocalDate.of(2026, 11, 2) to HabitLogStatus.KEPT),
            listOf(PausePeriod(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))),
        )
        val boxes = HabitGridModel.boxes(history, YearMonth.of(2026, 11), today)
        assertEquals(BoxState.KEPT, boxes[1].state)
        assertTrue(boxes[1].state.filled)
        assertEquals(listOf(5, 6), boxes.filter { it.state == BoxState.PAUSED }.map { it.day })
    }

    @Test
    public fun theHeatmapCoversFiftyThreeWeeksEndingWithThisWeek() {
        val history = HabitHistory(LocalDate.of(2026, 1, 1), mapOf(today to HabitLogStatus.DONE, LocalDate.of(2026, 1, 5) to HabitLogStatus.DONE))
        val map = HabitDetailViewModel.heatmap(history, today)

        assertEquals(53, map.weeks.size)
        assertTrue(map.weeks.all { it.size == 7 })
        assertEquals(DayOfWeek.MONDAY, map.firstMonday.dayOfWeek)
        // Today is Friday: the last week's Friday is DONE and its weekend is still to come.
        assertEquals(DayState.DONE, map.weeks.last()[4])
        assertEquals(DayState.FUTURE, map.weeks.last()[6])
        assertEquals(2, map.doneDays)
    }
}
