package com.sanjith.betterlife.widgets

import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.widget.WidgetTask
import com.sanjith.betterlife.feature.events.colorIndexOf
import com.sanjith.betterlife.feature.events.toAgendaItem
import com.sanjith.betterlife.feature.tasks.TaskService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The task widget's data (Prompt 20): open top-level tasks and the privacy setting. */
public data class TaskWidgetInput(val tasks: List<WidgetTask>, val now: ZonedDateTime, val hideDetails: Boolean)

public class TaskWidgetSource(private val tasks: TaskRepository, private val settings: Flow<AppSettings>, private val clock: Clock) {
    public fun observe(): Flow<TaskWidgetInput> = combine(tasks.observeActive(), settings) { all, s ->
        TaskWidgetInput(
            tasks = all.filter { it.completedAt == null && it.parentTaskId == null }
                .map { WidgetTask(it.id, it.title, it.due, it.priority, it.projectId != null) },
            now = clock.now().atZone(clock.zone),
            hideDetails = s.hideWidgetDetails,
        )
    }
}

/** The calendar widget's data: events (with birthdays), open dated tasks for the dots, categories and settings. */
public data class CalendarWidgetInput(
    val items: List<AgendaItem>,
    val categories: List<EventCategory>,
    val now: Instant,
    val zone: ZoneId,
    val weekStart: DayOfWeek,
    val hideDetails: Boolean,
) {
    public fun colorIndex(category: String?): Int? = categories.colorIndexOf(category)
}

public class CalendarWidgetSource(
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val categories: Flow<List<EventCategory>>,
    private val settings: Flow<AppSettings>,
    private val clock: Clock,
) {
    public fun observe(): Flow<CalendarWidgetInput> = combine(events.observeActive(), tasks.observeActive(), categories, settings) { e, t, cats, s ->
        CalendarWidgetInput(
            items = e.map { it.toAgendaItem() } + t.filter { it.completedAt == null }.mapNotNull { it.toAgendaItem() },
            categories = cats,
            now = clock.now(),
            zone = clock.zone,
            weekStart = s.weekStart,
            hideDetails = s.hideWidgetDetails,
        )
    }
}

/**
 * Task widget taps go through the same TaskService as the Tasks screen, so a completed task gets its
 * next instance if it repeats and its reminders stop, and a deleted one goes to the soft-deleted state
 * with its reminders cancelled, then the alarms are re-planned (agent.md section 4).
 */
public class TaskWidgetActions(private val service: TaskService) {
    public suspend fun complete(taskId: String) {
        service.complete(taskId)
    }

    public suspend fun delete(taskId: String) {
        service.delete(taskId)
    }
}
