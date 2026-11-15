package com.sanjith.betterlife.domain.calendar

import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.event.AgendaTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Arranging overlapping events into side-by-side columns on the week and day grids (Prompt 18). */
public class TimeGridLayoutTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 6)

    private fun entry(id: String, from: String, to: String?, kind: AgendaKind = AgendaKind.EVENT, on: LocalDate = day, until: LocalDate = on): AgendaEntry {
        val start = on.atTime(LocalTime.parse(from)).atZone(zone).toInstant()
        val end = to?.let { until.atTime(LocalTime.parse(it)).atZone(zone).toInstant() }
        return AgendaEntry(AgendaItem(id, kind, id, AgendaTime.Timed(start, end, zone)), on, start, end)
    }

    private fun blocks(vararg entries: AgendaEntry, days: List<LocalDate> = listOf(day)) =
        TimeGridLayout.layout(entries.toList(), days, zone)

    private fun List<GridDay>.place(id: String) = flatMap { it.blocks }.single { it.entry.item.id == id }.let { it.column to it.columns }

    @Test
    public fun twoOverlappingEventsSitSideBySide() {
        val grid = blocks(entry("a", "10:00", "11:00"), entry("b", "10:30", "11:30"))
        assertEquals(0 to 2, grid.place("a"))
        assertEquals(1 to 2, grid.place("b"))
    }

    @Test
    public fun touchingEventsDoNotShareTheWidth() {
        val grid = blocks(entry("a", "09:00", "10:00"), entry("b", "10:00", "11:00"))
        assertEquals(0 to 1, grid.place("a"))
        assertEquals(0 to 1, grid.place("b"))
    }

    @Test
    public fun aChainSharesOneColumnCountAndReusesAFreedColumn() {
        // a overlaps b, b overlaps c, a and c do not overlap: c takes a's column back.
        val grid = blocks(entry("a", "09:00", "10:00"), entry("b", "09:30", "11:00"), entry("c", "10:00", "10:30"), entry("d", "12:00", "13:00"))
        assertEquals(0 to 2, grid.place("a"))
        assertEquals(1 to 2, grid.place("b"))
        assertEquals(0 to 2, grid.place("c"))
        assertEquals(0 to 1, grid.place("d"))
    }

    @Test
    public fun threeAtOnceGetThreeColumns() {
        val grid = blocks(entry("long", "09:00", "12:00"), entry("x", "09:15", "10:00"), entry("y", "09:30", "10:30"))
        assertEquals(setOf(0 to 3, 1 to 3, 2 to 3), setOf(grid.place("long"), grid.place("x"), grid.place("y")))
        assertEquals(0 to 3, grid.place("long"))
    }

    @Test
    public fun aTaskWithNoEndIsDrawnThirtyMinutesAndCanSitBesideAnEvent() {
        val grid = blocks(entry("task", "16:00", null, AgendaKind.TASK), entry("event", "16:15", "17:00"))
        val task = grid.single().blocks.single { it.entry.item.id == "task" }
        assertEquals(16 * 60, task.startMinute)
        assertEquals(16 * 60 + TimeGridLayout.MIN_BLOCK_MINUTES, task.endMinute)
        assertEquals(2, task.columns)
    }

    @Test
    public fun anEventPastMidnightContinuesOnTheNextDay() {
        val next = day.plusDays(1)
        val grid = blocks(entry("party", "22:00", "02:00", until = next), days = listOf(day, next))
        val first = grid[0].blocks.single()
        val second = grid[1].blocks.single()
        assertEquals(22 * 60 to TimeGridLayout.MINUTES_PER_DAY, first.startMinute to first.endMinute)
        assertEquals(0 to 2 * 60, second.startMinute to second.endMinute)
        assertTrue(second.continued)
    }

    @Test
    public fun allDayRowsGoToTheStripAndOtherDaysAreLeftOut() {
        val birthday = AgendaEntry(AgendaItem("b", AgendaKind.BIRTHDAY, "Mom", AgendaTime.AllDay(day)), day, null, null)
        val elsewhere = entry("x", "09:00", "10:00", on = day.plusDays(3))
        val grid = blocks(birthday, elsewhere, entry("a", "23:45", null))
        assertEquals(listOf("b"), grid.single().allDay.map { it.item.id })
        assertEquals(listOf("a"), grid.single().blocks.map { it.entry.item.id })
        // A late moment is still drawn readable, but never past midnight.
        assertEquals(TimeGridLayout.MINUTES_PER_DAY, grid.single().blocks.single().endMinute)
    }

    @Test
    public fun emptyDaysStayEmpty() {
        assertEquals(listOf(GridDay(day, emptyList(), emptyList())), blocks())
    }
}
