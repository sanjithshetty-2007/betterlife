package com.sanjith.betterlife.domain.calendar

import com.sanjith.betterlife.domain.event.AgendaEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One timed occurrence drawn on one day of the week or day grid. Minutes count from that day's
 * midnight ([endMinute] up to 1440). [column] of [columns] places overlapping blocks side by side.
 */
public data class TimeBlock(
    val entry: AgendaEntry,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columns: Int,
    /** True when the occurrence started on an earlier day (it runs past midnight). */
    val continued: Boolean = false,
)

/** What one day column of the grid shows: the all-day strip and the timed blocks. */
public data class GridDay(val date: LocalDate, val allDay: List<AgendaEntry>, val blocks: List<TimeBlock>)

/**
 * Lays out the week and day time grids (Prompt 18).
 *
 * - Timed occurrences become blocks on the day they start; one that runs past midnight continues on
 *   the next days (at most [MAX_CONTINUED_DAYS]).
 * - Blocks that overlap, directly or through a chain, form a group; each block takes the first
 *   column whose last block has ended, and every block of the group shares the group's column
 *   count, so they sit side by side at equal widths.
 * - An occurrence with no end (a task, a "remind me", an event without an end time) is drawn
 *   [MIN_BLOCK_MINUTES] long so it can be read and tapped; that drawn length is what overlaps.
 * Conflict badges are NOT decided here: only events and meetings conflict (AgendaEntry.conflict).
 *
 * Example: 10:00-11:00 and 10:30-11:30 -> two columns of two; 09:00-10:00 alone -> one column.
 */
public object TimeGridLayout {
    public const val MINUTES_PER_DAY: Int = 24 * 60
    public const val MIN_BLOCK_MINUTES: Int = 30
    private const val MAX_CONTINUED_DAYS = 6
    private const val SECONDS_PER_MINUTE = 60

    public fun layout(entries: List<AgendaEntry>, days: List<LocalDate>, zone: ZoneId): List<GridDay> {
        val wanted = days.toSet()
        val segments = entries.filter { it.start != null }.flatMap { segments(it, zone) }.filter { it.date in wanted }
        val byDay = segments.groupBy { it.date }
        val allDay = entries.filter { it.allDay && it.date in wanted }.groupBy { it.date }
        return days.map { day -> GridDay(day, allDay[day].orEmpty(), arrange(byDay[day].orEmpty())) }
    }

    // Splits one timed occurrence at each local midnight it crosses.
    private fun segments(entry: AgendaEntry, zone: ZoneId): List<TimeBlock> {
        val start = requireNotNull(entry.start)
        val startLocal = start.atZone(zone)
        val startDay = startLocal.toLocalDate()
        val startMinute = startLocal.toLocalTime().toSecondOfDay() / SECONDS_PER_MINUTE
        val end = entry.end?.takeIf { it.isAfter(start) }
            ?: return listOf(block(entry, startDay, startMinute, minOf(startMinute + MIN_BLOCK_MINUTES, MINUTES_PER_DAY), false))
        return (0..MAX_CONTINUED_DAYS).mapNotNull { offset ->
            val day = startDay.plusDays(offset.toLong())
            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            if (!end.isAfter(dayStart)) return@mapNotNull null
            val from = if (offset == 0) startMinute else 0
            val to = if (end.isBefore(dayEnd)) minutesInto(day, end, zone) else MINUTES_PER_DAY
            if (to <= from) null else block(entry, day, from, to, continued = offset > 0)
        }
    }

    private fun minutesInto(day: LocalDate, instant: Instant, zone: ZoneId): Int {
        val local = instant.atZone(zone)
        return if (local.toLocalDate() == day) local.toLocalTime().toSecondOfDay() / SECONDS_PER_MINUTE else MINUTES_PER_DAY
    }

    private fun block(entry: AgendaEntry, day: LocalDate, from: Int, to: Int, continued: Boolean) =
        TimeBlock(entry, day, from, maxOf(to, minOf(from + MIN_BLOCK_MINUTES, MINUTES_PER_DAY)), column = 0, columns = 1, continued = continued)

    /** Columns for one day's blocks (pure; exposed for tests). */
    public fun arrange(blocks: List<TimeBlock>): List<TimeBlock> {
        val sorted = blocks.sortedWith(compareBy<TimeBlock>({ it.startMinute }, { -it.endMinute }, { it.entry.item.title }))
        val result = mutableListOf<TimeBlock>()
        var group = mutableListOf<TimeBlock>()
        var groupEnd = -1
        val columnEnds = mutableListOf<Int>()
        fun closeGroup() {
            val count = columnEnds.size.coerceAtLeast(1)
            result += group.map { it.copy(columns = count) }
            group = mutableListOf()
            columnEnds.clear()
        }
        for (block in sorted) {
            if (group.isNotEmpty() && block.startMinute >= groupEnd) closeGroup()
            val free = columnEnds.indexOfFirst { it <= block.startMinute }
            val column = if (free >= 0) free else columnEnds.size
            if (free >= 0) columnEnds[free] = block.endMinute else columnEnds += block.endMinute
            group += block.copy(column = column)
            groupEnd = if (group.size == 1) block.endMinute else maxOf(groupEnd, block.endMinute)
        }
        if (group.isNotEmpty()) closeGroup()
        return result
    }
}
