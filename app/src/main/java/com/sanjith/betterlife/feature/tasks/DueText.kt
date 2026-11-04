package com.sanjith.betterlife.feature.tasks

import android.content.Context
import android.text.format.DateFormat
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.task.TaskDue
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Human due text for task rows: "Today 5:00 PM", "Tomorrow", "Fri 9:00 AM", "12 Oct 2026".
 * Uses the phone's 12/24-hour setting and language.
 */
public class DueText(private val context: Context, private val zone: () -> ZoneId) {

    private val timeFormat = DateFormat.getTimeFormat(context)
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val locale: Locale get() = context.resources.configuration.locales[0]

    /** [date] overrides the due's own date (an overdue repeating task rolls forward). */
    public fun format(due: TaskDue, date: LocalDate?, today: LocalDate): String? = when (due) {
        is TaskDue.At -> {
            val local = due.instant.atZone(zone())
            "${day(date ?: local.toLocalDate(), today)} ${time(local.toLocalTime())}"
        }
        is TaskDue.OnDay -> day(date ?: due.date, today)
        TaskDue.None -> null
    }

    public fun day(date: LocalDate, today: LocalDate): String = when {
        date == today -> context.getString(R.string.due_today)
        date == today.plusDays(1) -> context.getString(R.string.due_tomorrow)
        date == today.minusDays(1) -> context.getString(R.string.due_yesterday)
        date.isAfter(today) && date.isBefore(today.plusDays(DAYS_SHOWN_AS_WEEKDAY)) ->
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        else -> date.format(dateFormat.withLocale(locale))
    }

    public fun time(time: LocalTime): String =
        timeFormat.format(Date.from(time.atDate(LocalDate.EPOCH).atZone(ZoneId.systemDefault()).toInstant()))

    private companion object {
        const val DAYS_SHOWN_AS_WEEKDAY = 7L
    }
}
