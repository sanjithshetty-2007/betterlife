package com.sanjith.betterlife.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.settings.EventCategoryStore
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.domain.time.zonedNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The event, meeting and birthday editor. Saving updates the event, its reminders and the alarms. */
public class EventEditorViewModel(
    private val eventId: String?,
    initialType: EventType,
    private val service: EventService,
    private val events: EventRepository,
    private val categoryStore: EventCategoryStore,
    private val clock: Clock,
    private val defaults: suspend () -> ReminderDefaults,
) : ViewModel() {

    private var reminderDefaults = ReminderDefaults()

    private val _state = MutableStateFlow(EventEditorState.new(initialType, clock.zonedNow(), reminderDefaults).copy(id = eventId))
    public val state: StateFlow<EventEditorState> = _state

    public val categories: StateFlow<List<EventCategory>> =
        categoryStore.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Becomes true once saved or deleted, so the screen can close. */
    public val finished: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            reminderDefaults = defaults()
            if (eventId == null) {
                // The owner's own defaults replace the built-in ones until they touch the reminders.
                _state.update { if (it.remindersEdited) it else it.copy(reminders = reminderDefaults.forEvent(it.type)) }
            } else {
                load(eventId)
            }
        }
    }

    private suspend fun load(id: String) {
        val event = events.getAnyById(id) ?: return
        val reminders = events.activeReminders(id).map { ReminderPlan(it.offsetMinutes, it.importance) }
        _state.value = EventEditorState.from(event, reminders, clock.zone)
    }

    public fun today(): java.time.LocalDate = clock.today()

    public fun edit(change: (EventEditorState) -> EventEditorState) {
        _state.update(change)
    }

    public fun setType(type: EventType) {
        _state.update { it.withType(type, reminderDefaults) }
    }

    public fun setReminders(list: List<ReminderPlan>) {
        _state.update { it.copy(reminders = list, remindersEdited = true) }
    }

    public fun addCategory(category: EventCategory) {
        viewModelScope.launch {
            categoryStore.save(category)
            edit { it.copy(colorCategory = category.name.trim()) }
        }
    }

    public fun save() {
        val s = _state.value
        if (s.errors(clock.today()).isNotEmpty()) return
        viewModelScope.launch {
            val reminders = s.reminders.distinctBy { it.offsetMinutes }.map { ReminderDraft(it.offsetMinutes, it.importance) }
            val existing = s.id?.let { events.getAnyById(it) }
            if (existing == null) {
                service.createEvent(s.toDraft(clock.zone), reminders)
            } else {
                service.saveEvent(s.applyTo(existing, clock.zone), reminders)
            }
            finished.value = true
        }
    }

    public fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            service.deleteEvent(id)
            finished.value = true
        }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        public const val ARG_EVENT_ID: String = "eventId"
        public const val ARG_TYPE: String = "type"

        /** Route value meaning "create a new event". */
        public const val NEW: String = "new"

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val handle = createSavedStateHandle()
                val id = handle.get<String>(ARG_EVENT_ID)?.takeIf { it != NEW }
                val type = handle.get<String>(ARG_TYPE)?.let { name -> EventType.entries.firstOrNull { it.name == name } } ?: EventType.EVENT
                EventEditorViewModel(id, type, c.eventService, c.eventRepository, c.eventCategoryStore, c.clock) {
                    c.settingsRepository.current().reminderDefaults
                }
            }
        }
    }
}
