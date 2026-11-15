package com.sanjith.betterlife.feature.calendar

import android.content.Context
import androidx.room.Room
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.repository.EventDraft
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.domain.event.BirthdayRules
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.time.FixedClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/** A real in-memory database and repositories for the calendar's device tests, on Fri 20 Nov 2026 10:00. */
internal class CalendarTestData(context: Context) {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val clock: FixedClock = FixedClock.of("2026-11-20T10:00", "Asia/Kolkata")
    val today: LocalDate = LocalDate.of(2026, 11, 20)
    val db: BetterLifeDatabase = Room.inMemoryDatabaseBuilder(context, BetterLifeDatabase::class.java).build()
    private var idCounter = 0
    private val ids = { "00000000-0000-4000-8000-%012d".format(++idCounter) }
    val events = EventRepository(db, { 1_000L }, ids)
    val tasks = TaskRepository(db, { 1_000L }, ids)
    val standalone = StandaloneReminderRepository(db, { 1_000L }, ids)
    val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    val categories = MutableStateFlow(listOf(EventCategory("Work", 4), EventCategory("Personal", 0)))

    fun viewModel(): CalendarViewModel = CalendarViewModel(events, tasks, standalone, categories, weekStart, clock)

    fun millis(date: LocalDate, time: String): Long = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    fun event(title: String, date: LocalDate, from: String, to: String, category: String? = null, rule: RecurrenceRule? = null, type: EventType = EventType.EVENT): String =
        runBlocking {
            events.create(
                EventDraft(
                    title, type, startAtMillis = millis(date, from), endAtMillis = millis(date, to), zoneId = zone,
                    colorCategory = category, recurrenceRule = rule?.let(RecurrenceRuleText::encode),
                ),
            )
        }

    fun birthday(name: String, monthDay: MonthDay): String = runBlocking {
        events.create(
            EventDraft(
                name, EventType.BIRTHDAY, allDay = true, startDate = monthDay.atYear(2000),
                recurrenceRule = RecurrenceRuleText.encode(BirthdayRules.rule(monthDay)),
            ),
        )
    }

    fun task(title: String, date: LocalDate, at: String): String = runBlocking {
        tasks.create(TaskDraft(title, dueAtMillis = millis(date, at), dueZoneId = zone))
    }

    fun close() = db.close()
}

internal fun CalendarViewModel.await(predicate: (CalendarUi) -> Boolean): CalendarUi = runBlocking {
    withTimeout(TIMEOUT_MS) { ui.first { !it.loading && predicate(it) } }
}

private const val TIMEOUT_MS = 5_000L
