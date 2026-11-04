package com.sanjith.betterlife.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.data.settings.EventCategoryStore
import com.sanjith.betterlife.domain.event.Agenda
import com.sanjith.betterlife.domain.event.AgendaDay
import com.sanjith.betterlife.domain.event.AgendaItem
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.event.AgendaTime
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.today
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/** What the agenda screen shows: the days, and the colour of each category by name. */
public data class AgendaUi(val days: List<AgendaDay> = emptyList(), val categories: Map<String, Int> = emptyMap())

/** The agenda list (Prompt 10): events, birthdays, open dated tasks and "remind me" items by day. */
public class AgendaViewModel(
    events: EventRepository,
    tasks: TaskRepository,
    standalone: StandaloneReminderRepository,
    categories: EventCategoryStore,
    private val clock: Clock,
) : ViewModel() {

    public val ui: StateFlow<AgendaUi> = combine(
        events.observeActive(),
        tasks.observeActive(),
        standalone.observeActive(),
        categories.categories,
    ) { eventList, taskList, reminders, cats ->
        val items = eventList.map { it.toAgendaItem() } +
            taskList.filter { it.completedAt == null }.mapNotNull { it.toAgendaItem() } +
            reminders.map { it.toAgendaItem() }
        AgendaUi(
            days = Agenda.build(items, clock.today(), DAYS_SHOWN, clock.zone),
            categories = cats.associate { it.name.lowercase() to it.colorIndex },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AgendaUi())

    public fun today(): java.time.LocalDate = clock.today()

    public companion object {
        /** How far ahead the agenda looks. Calendar views come in Prompt 18. */
        public const val DAYS_SHOWN: Int = 60
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                AgendaViewModel(c.eventRepository, c.taskRepository, c.standaloneReminderRepository, c.eventCategoryStore, c.clock)
            }
        }
    }
}

internal fun EventEntity.toAgendaItem(): AgendaItem = AgendaItem(
    id = id,
    kind = when (type) {
        EventType.EVENT -> AgendaKind.EVENT
        EventType.MEETING -> AgendaKind.MEETING
        EventType.BIRTHDAY -> AgendaKind.BIRTHDAY
    },
    title = title,
    time = when {
        startAtMillis != null && zoneId != null ->
            AgendaTime.Timed(Instant.ofEpochMilli(startAtMillis), endAtMillis?.let(Instant::ofEpochMilli), zoneId)
        else -> AgendaTime.AllDay(startDate ?: java.time.LocalDate.EPOCH, endDate)
    },
    recurrence = RecurrenceRuleText.decode(recurrenceRule),
    location = location,
    colorCategory = colorCategory,
    birthYear = birthYear,
)

/** Only dated tasks are on the agenda; a repeating task shows its current instance only. */
internal fun TaskEntity.toAgendaItem(): AgendaItem? = when (val d = due) {
    is TaskDue.At -> AgendaItem(id, AgendaKind.TASK, title, AgendaTime.Timed(d.instant, null, d.zone))
    is TaskDue.OnDay -> AgendaItem(id, AgendaKind.TASK, title, AgendaTime.AllDay(d.date))
    TaskDue.None -> null
}

internal fun StandaloneReminderEntity.toAgendaItem(): AgendaItem =
    AgendaItem(id, AgendaKind.REMINDER, title, AgendaTime.Timed(Instant.ofEpochMilli(atMillis), null, zoneId))

/** Category colour index for [name], or null when the event has no (known) category. */
internal fun AgendaUi.colorIndexOf(name: String?): Int? = name?.let { categories[it.lowercase()] }

internal fun List<EventCategory>.colorIndexOf(name: String?): Int? =
    name?.let { n -> firstOrNull { it.name.equals(n, ignoreCase = true) }?.colorIndex }
