package com.sanjith.betterlife.domain.widget

import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.event.AgendaTime
import com.sanjith.betterlife.domain.event.BirthdayRules
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.task.TaskDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId

/** The task and calendar widgets' content (Prompt 20): which tasks, in what order, privacy, the month and the next events. */
public class TaskCalendarWidgetTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.parse("2026-11-20T10:00").atZone(zone)
    private val today = now.toLocalDate()
    private fun at(time: String, day: LocalDate = today) = TaskDue.At(day.atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant(), zone)
    private fun task(id: String, due: TaskDue, priority: Priority = Priority.NONE) = WidgetTask(id, "Title $id", due, priority, hasProject = false)

    @Test
    public fun onlyOverdueAndTodaysTasksInOrder() {
        val tasks = listOf(
            task("allDay", TaskDue.OnDay(today)),
            task("five", at("17:00"), Priority.HIGH),
            task("nine", at("09:00", today.plusDays(1))),
            task("yesterday", at("18:00", today.minusDays(1))),
            task("morningPassed", at("08:00")),
            task("later", at("12:00")),
            task("undated", TaskDue.None),
            task("noon2", at("12:00"), Priority.HIGH),
        )
        val model = TaskWidgetBuilder.build(tasks, now, hideDetails = false)
        // 08:00 today has passed, so it is overdue like yesterday's; tomorrow and undated are left to the app.
        assertEquals(listOf("yesterday", "morningPassed", "noon2", "later", "five", "allDay"), model.rows.map { it.taskId })
        assertEquals(2, model.overdue)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), model.rows.map { it.number })
    }

    @Test
    public fun hiddenDetailsDropTheTitles() {
        val model = TaskWidgetBuilder.build(listOf(task("a", at("12:00"))), now, hideDetails = true)
        assertEquals(null, model.rows.single().title)
        assertTrue(model.detailsHidden)
        assertTrue(TaskWidgetBuilder.build(emptyList(), now, hideDetails = true).isEmpty)
    }

    private fun event(id: String, day: LocalDate, from: String, to: String, kind: AgendaKind = AgendaKind.EVENT, category: String? = null, rule: RecurrenceRule? = null) =
        AgendaItem(
            id, kind, "Title $id",
            AgendaTime.Timed(day.atTime(java.time.LocalTime.parse(from)).atZone(zone).toInstant(), day.atTime(java.time.LocalTime.parse(to)).atZone(zone).toInstant(), zone),
            rule, colorCategory = category,
        )

    @Test
    public fun theMonthHasTheGridDotsAndCakes() {
        val items = listOf(
            event("work", today.plusDays(2), "09:00", "10:00", category = "Work"),
            AgendaItem("mom", AgendaKind.BIRTHDAY, "Mom", AgendaTime.AllDay(LocalDate.of(2000, 11, 25)), BirthdayRules.rule(MonthDay.of(11, 25))),
        )
        val month = CalendarWidgetBuilder.month(items, today, DayOfWeek.MONDAY, zone) { if (it == "Work") 4 else null }
        assertEquals(LocalDate.of(2026, 10, 26), month.grid.days.first())
        assertEquals(listOf<Int?>(4), month.marks.getValue(today.plusDays(2)).colors)
        assertTrue(month.marks.getValue(LocalDate.of(2026, 11, 25)).birthday)
    }

    @Test
    public fun nextEventsSkipWhatHasEndedAndTasksAndHideTitlesWhenAsked() {
        val items = listOf(
            event("past", today, "08:00", "09:00"),
            event("evening", today, "18:00", "19:00"),
            event("weekly", today.minusDays(1), "09:00", "09:30", AgendaKind.MEETING, rule = RecurrenceRule.Weekly(setOf(DayOfWeek.THURSDAY))),
            AgendaItem("task", AgendaKind.TASK, "A task", AgendaTime.Timed(now.plusHours(1).toInstant(), null, zone)),
        )
        val shown = CalendarWidgetBuilder.agenda(items, now.toInstant(), zone, hideDetails = false) { null }
        // The weekly Thursday meeting is next on 26 Nov (yesterday's was the 19th), and again on 3 Dec inside the 14 days.
        assertEquals(
            listOf("evening" to today, "weekly" to LocalDate.of(2026, 11, 26), "weekly" to LocalDate.of(2026, 12, 3)),
            shown.map { it.itemId to it.date },
        )
        val hidden = CalendarWidgetBuilder.agenda(items, now.toInstant(), zone, hideDetails = true) { null }
        assertTrue(hidden.all { it.title == null })
        assertEquals(AgendaKind.MEETING, hidden.last().kind)
    }

    @Test
    public fun theNextEventsListIsCapped() {
        val many = (0 until 10).map { event("e$it", today.plusDays(it.toLong() + 1), "09:00", "10:00") }
        assertEquals(CalendarWidgetBuilder.AGENDA_LIMIT, CalendarWidgetBuilder.agenda(many, now.toInstant(), zone, false) { null }.size)
    }
}
