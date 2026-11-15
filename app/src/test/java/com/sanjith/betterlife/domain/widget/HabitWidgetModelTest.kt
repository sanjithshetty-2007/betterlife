package com.sanjith.betterlife.domain.widget

import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.ConsistencyCalculator
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PausePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The habit widget's content (Prompt 19): privacy, the cap with "+N more", done counts and the rollover hour. */
public class HabitWidgetModelTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val nov15 = LocalDate.of(2026, 11, 15)
    private fun at(time: String) = LocalDateTime.parse(time).atZone(zone).toInstant()

    private fun habit(n: Int, done: List<LocalDate> = emptyList(), start: LocalDate = nov15.minusDays(20), pauses: List<PausePeriod> = emptyList()) =
        WidgetHabit("h$n", "Habit name $n", "#FF6B5E", HabitHistory(start, done.associateWith { HabitLogStatus.DONE }, pauses))

    private fun build(habits: List<WidgetHabit>, hide: Boolean = true, cap: Int = HabitWidgetBuilder.COMPACT_CAP, now: String = "2026-11-15T10:00") =
        HabitWidgetBuilder.build(habits, at(now), zone, rolloverHour = 3, hideDetails = hide, cap = cap)

    @Test
    public fun detailsHiddenShowsNoNamesAndShownShowsThem() {
        val habits = listOf(habit(1, listOf(nov15)), habit(2))
        val hidden = build(habits, hide = true)
        assertTrue(hidden.detailsHidden)
        assertTrue(hidden.boxes.all { it.name == null })
        assertEquals(listOf(1, 2), hidden.boxes.map { it.number })

        val shown = build(habits, hide = false)
        assertEquals(listOf("Habit name 1", "Habit name 2"), shown.boxes.map { it.name })
        assertEquals(listOf(true, false), shown.boxes.map { it.done })
    }

    @Test
    public fun moreHabitsThanRoomMakeTheLastBoxPlusNMore() {
        val six = (1..6).map { habit(it, if (it <= 2) listOf(nov15) else emptyList()) }
        val compact = build(six)
        assertEquals(3, compact.boxes.size)
        assertEquals(3, compact.more)
        assertEquals(2 to 6, compact.settled to compact.total)

        val wide = build(six, cap = HabitWidgetBuilder.WIDE_CAP)
        assertEquals(6, wide.boxes.size)
        assertEquals(0, wide.more)

        val exactlyFour = build((1..4).map { habit(it) })
        assertEquals(4 to 0, exactlyFour.boxes.size to exactlyFour.more)
    }

    @Test
    public fun atOneInTheMorningTheWidgetStillShowsYesterday() {
        val doneOn14th = listOf(habit(1, listOf(nov15.minusDays(1))))
        val early = build(doneOn14th, now = "2026-11-15T01:00")
        assertEquals(nov15.minusDays(1), early.today)
        assertTrue(early.boxes.single().done)

        val after = build(doneOn14th, now = "2026-11-15T03:00")
        assertEquals(nov15, after.today)
        assertEquals(false, after.boxes.single().done)
    }

    @Test
    public fun aPausedHabitIsSettledButCannotBeTapped() {
        val paused = habit(1, pauses = listOf(PausePeriod(nov15, nov15)))
        val model = build(listOf(paused, habit(2)))
        assertEquals(listOf(false, true), model.boxes.map { it.tappable })
        assertEquals(listOf(false, false), model.boxes.map { it.done })
        assertEquals(1, model.settled)
    }

    @Test
    public fun noHabitsIsEmptyAndTheHeaderSaysNew() {
        val model = build(emptyList())
        assertTrue(model.isEmpty)
        assertEquals(Consistency.New, model.consistency)
        assertEquals(0, model.more)
    }

    @Test
    public fun theOverallConsistencyAddsEveryHabitsDays() {
        // 10 of the last 10 scheduled days for one habit, 0 of 10 for the other: 50 %.
        val all = (1..10).map { nov15.minusDays(it.toLong()) }
        val a = HabitHistory(nov15.minusDays(10), all.associateWith { HabitLogStatus.DONE }, emptyList())
        val b = HabitHistory(nov15.minusDays(10), emptyMap(), emptyList())
        assertEquals(Consistency.Percent(50, 10, 20), ConsistencyCalculator.overall(listOf(a, b), nov15))
        assertEquals(Consistency.New, ConsistencyCalculator.overall(listOf(HabitHistory(nov15.minusDays(3), emptyMap(), emptyList())), nov15))
        assertNull(build(listOf(habit(1))).boxes.single().name)
    }
}

/** The widget measures the space the launcher gives it instead of guessing cell sizes (Prompt 19). */
public class HabitWidgetLayoutTest {

    private fun fit(width: Float, height: Float) =
        HabitWidgetLayout.fit(width, height, cell = 48f, header = 28f, labelAndFooter = 36f, padding = 16f, smallPadding = 8f, roomyFrom = 150f)

    @Test
    public fun aSquareTwoByTwoGetsTwoRowsOfTwo() {
        assertEquals(HabitWidgetLayout(columns = 2, rows = 2, labels = false, roomy = true), fit(170f, 170f))
        assertEquals(4, fit(170f, 170f).capacity)
    }

    @Test
    public fun aWideWidgetGetsOneRowWithNames() {
        val wide = fit(330f, 170f)
        assertEquals(1, wide.rows)
        assertEquals(6, wide.columns)
        assertEquals(true, wide.labels)
        // Wide but short: one row, no room for names.
        assertEquals(false, fit(330f, 110f).labels)
        assertEquals(HabitWidgetBuilder.WIDE_CAP, fit(900f, 400f).capacity)
    }

    @Test
    public fun theSmallestSizeStillShowsABoxWithSmallPadding() {
        val tiny = fit(110f, 110f)
        assertEquals(1, tiny.capacity)
        assertEquals(false, tiny.roomy)
        // A short 2x2 (some launchers) uses the small padding and one row of two, with names below.
        assertEquals(HabitWidgetLayout(columns = 2, rows = 1, labels = true, roomy = false), fit(140f, 130f))
    }
}
