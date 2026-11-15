package com.sanjith.betterlife.domain.calendar

import com.sanjith.betterlife.domain.event.AgendaDay
import com.sanjith.betterlife.domain.event.AgendaKind
import java.time.LocalDate

/** Dots under a month cell: one per category colour (null = no category: tasks, reminders), and a cake. */
public data class DayMarks(val colors: List<Int?>, val birthday: Boolean)

/** The month dots, shared by the Calendar tab and the calendar widget (Prompts 18 and 20). */
public object CalendarMarks {
    /** Dots under one month cell; more categories that day still show in the day's list. */
    public const val MAX_DOTS: Int = 3

    /**
     * Example: a day with a Work meeting, a Personal event and a task -> three dots (Work, Personal,
     * no-category); a birthday adds the cake instead of a dot.
     */
    public fun of(days: List<AgendaDay>, colorIndex: (String?) -> Int?): Map<LocalDate, DayMarks> = days.associate { day ->
        val colors = day.entries.filter { it.item.kind != AgendaKind.BIRTHDAY }
            .map { colorIndex(it.item.colorCategory) }.distinct().take(MAX_DOTS)
        day.date to DayMarks(colors, day.entries.any { it.item.kind == AgendaKind.BIRTHDAY })
    }
}
