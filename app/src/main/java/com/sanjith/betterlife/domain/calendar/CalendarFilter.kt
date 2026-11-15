package com.sanjith.betterlife.domain.calendar

import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.domain.event.AgendaKind

/**
 * The calendar's category chips (Prompt 18). Nothing selected shows everything. With chips on,
 * events and meetings show only when their colour category is selected, birthdays only when the
 * birthday chip is; tasks and "remind me" items have no colour category and always show.
 *
 * Example: {"work"} keeps a Work meeting and every task, hides a Personal event and birthdays.
 */
public data class CalendarFilter(val categories: Set<String> = emptySet(), val birthdays: Boolean = false) {
    val isOff: Boolean get() = categories.isEmpty() && !birthdays

    public fun toggleCategory(name: String): CalendarFilter = name.lowercase().let { key ->
        copy(categories = if (key in categories) categories - key else categories + key)
    }

    public fun toggleBirthdays(): CalendarFilter = copy(birthdays = !birthdays)

    public fun keeps(entry: AgendaEntry): Boolean = when {
        isOff -> true
        entry.item.kind == AgendaKind.BIRTHDAY -> birthdays
        entry.item.kind == AgendaKind.EVENT || entry.item.kind == AgendaKind.MEETING ->
            entry.item.colorCategory?.lowercase() in categories
        else -> true
    }
}
