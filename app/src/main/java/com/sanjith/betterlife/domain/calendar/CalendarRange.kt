package com.sanjith.betterlife.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The four calendar views (context.md 5.2, Prompt 18). */
public enum class CalendarView { DAY, WEEK, MONTH, AGENDA }

/** Both ends included. */
public data class DateRange(val from: LocalDate, val to: LocalDate) {
    init {
        require(!to.isBefore(from)) { "A range ends on or after its start" }
    }

    val days: Int get() = (to.toEpochDay() - from.toEpochDay() + 1).toInt()

    public fun dates(): List<LocalDate> = (0 until days).map { from.plusDays(it.toLong()) }

    public operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

/** The month grid: whole weeks from the week holding the 1st to the week holding the last day. */
public data class MonthGrid(val month: YearMonth, val weekStart: DayOfWeek, val days: List<LocalDate>) {
    val weeks: List<List<LocalDate>> get() = days.chunked(DAYS_PER_WEEK)

    public fun inMonth(date: LocalDate): Boolean = YearMonth.from(date) == month
}

internal const val DAYS_PER_WEEK = 7

/**
 * Which dates each view shows, and how the arrows and swipes move it. The week starts on the
 * owner's setting (Monday by default, context.md 5.6).
 */
public object CalendarRanges {

    /** How far the agenda list looks ahead from its first day (it was 60 days in Prompt 10). */
    public const val AGENDA_DAYS: Int = 60

    /**
     * Example: November 2026 with Monday first -> 26 Oct to 6 Dec (6 weeks, the 30th is a Monday); February 2027 with
     * Monday first -> 1 to 28 Feb (4 weeks, it starts on a Monday).
     */
    public fun monthGrid(month: YearMonth, weekStart: DayOfWeek): MonthGrid {
        val first = weekStartOf(month.atDay(1), weekStart)
        val last = weekStartOf(month.atEndOfMonth(), weekStart).plusDays(DAYS_PER_WEEK - 1L)
        return MonthGrid(month, weekStart, DateRange(first, last).dates())
    }

    public fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(weekStart))

    /** The weekday letters' order for the grid header. */
    public fun weekdays(weekStart: DayOfWeek): List<DayOfWeek> = (0 until DAYS_PER_WEEK).map { weekStart.plus(it.toLong()) }

    /** The dates [view] shows around [anchor]. */
    public fun range(view: CalendarView, anchor: LocalDate, weekStart: DayOfWeek): DateRange = when (view) {
        CalendarView.DAY -> DateRange(anchor, anchor)
        CalendarView.WEEK -> weekStartOf(anchor, weekStart).let { DateRange(it, it.plusDays(DAYS_PER_WEEK - 1L)) }
        CalendarView.MONTH -> monthGrid(YearMonth.from(anchor), weekStart).days.let { DateRange(it.first(), it.last()) }
        CalendarView.AGENDA -> DateRange(anchor, anchor.plusDays(AGENDA_DAYS - 1L))
    }

    /**
     * The anchor one step back ([steps] -1) or forward (+1). A month step keeps the day where it can
     * (31 Jan + 1 month = 28 or 29 Feb).
     */
    public fun shift(view: CalendarView, anchor: LocalDate, steps: Int): LocalDate = when (view) {
        CalendarView.DAY -> anchor.plusDays(steps.toLong())
        CalendarView.WEEK -> anchor.plusWeeks(steps.toLong())
        CalendarView.MONTH -> anchor.plusMonths(steps.toLong())
        CalendarView.AGENDA -> anchor.plusDays(steps.toLong() * AGENDA_DAYS)
    }
}
