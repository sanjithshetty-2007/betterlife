package com.sanjith.betterlife.domain.widget

import com.sanjith.betterlife.domain.calendar.CalendarMarks
import com.sanjith.betterlife.domain.calendar.CalendarRanges
import com.sanjith.betterlife.domain.calendar.DayMarks
import com.sanjith.betterlife.domain.calendar.MonthGrid
import com.sanjith.betterlife.domain.event.Agenda
import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.AgendaKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the calendar widget shows: the month grid, or the next events (the owner picks when adding it). */
public enum class CalendarWidgetMode { MONTH, AGENDA }

/** This month with the same dots and cakes as the Calendar tab. */
public data class CalendarWidgetMonth(val grid: MonthGrid, val today: LocalDate, val marks: Map<LocalDate, DayMarks>)

/**
 * One upcoming item. [title] is null when details are hidden: the widget then shows only what it is
 * (Event, Meeting, Birthday) and when.
 */
public data class CalendarWidgetEntry(
    val itemId: String,
    val date: LocalDate,
    val start: Instant?,
    val title: String?,
    val kind: AgendaKind,
    val colorIndex: Int?,
    val conflict: Boolean,
)

/**
 * Builds the calendar widget (Prompt 20) with the Calendar tab's rules: repeating items expanded for
 * the shown dates only (Agenda.build), the same dots per category.
 *
 * Example: today 11:00 meeting (past) and 18:00 event, a birthday tomorrow -> agenda: 18:00 event,
 * tomorrow's birthday; the 11:00 one has ended and is left out.
 */
public object CalendarWidgetBuilder {
    /** How far ahead the next-events list looks, and how many it lists. */
    public const val AGENDA_DAYS: Int = 14
    public const val AGENDA_LIMIT: Int = 6

    private val EVENT_KINDS = setOf(AgendaKind.EVENT, AgendaKind.MEETING, AgendaKind.BIRTHDAY)

    public fun month(items: List<AgendaItem>, today: LocalDate, weekStart: DayOfWeek, zone: ZoneId, colorIndex: (String?) -> Int?): CalendarWidgetMonth {
        val grid = CalendarRanges.monthGrid(YearMonth.from(today), weekStart)
        val days = Agenda.build(items, grid.days.first(), grid.days.size, zone)
        return CalendarWidgetMonth(grid, today, CalendarMarks.of(days, colorIndex))
    }

    /** Events, meetings and birthdays from now on; tasks have their own widget. */
    public fun agenda(items: List<AgendaItem>, now: Instant, zone: ZoneId, hideDetails: Boolean, colorIndex: (String?) -> Int?): List<CalendarWidgetEntry> {
        val today = now.atZone(zone).toLocalDate()
        return Agenda.build(items.filter { it.kind in EVENT_KINDS }, today, AGENDA_DAYS, zone)
            .flatMap { it.entries }
            .filter { entry -> entry.start == null || !(entry.end ?: entry.start).isBefore(now) }
            .take(AGENDA_LIMIT)
            .map { e ->
                CalendarWidgetEntry(e.item.id, e.date, e.start, if (hideDetails) null else e.item.title, e.item.kind, colorIndex(e.item.colorCategory), e.conflict)
            }
    }
}
