package com.sanjith.betterlife.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.StandaloneDraft
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.domain.reminder.ReminderDefaults
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.zonedNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** The standalone "remind me" editor: a title, a moment and early reminders, with no linked item. */
public class StandaloneEditorViewModel(
    private val itemId: String?,
    private val service: EventService,
    private val repository: StandaloneReminderRepository,
    private val clock: Clock,
    private val defaults: suspend () -> ReminderDefaults,
) : ViewModel() {

    private val _state = MutableStateFlow(newState())
    public val state: StateFlow<StandaloneEditorState> = _state

    /** Becomes true once saved, done or deleted, so the screen can close. */
    public val finished: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private var remindersEdited = false

    init {
        viewModelScope.launch {
            if (itemId == null) {
                val plans = defaults().standalone
                _state.update { if (remindersEdited) it else it.copy(reminders = plans) }
            } else {
                load(itemId)
            }
        }
    }

    // A new reminder starts at the next whole hour, like a new event.
    private fun newState(): StandaloneEditorState {
        val now = clock.zonedNow()
        val next = now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        return StandaloneEditorState(id = itemId, date = next.toLocalDate(), time = next.toLocalTime())
    }

    private suspend fun load(id: String) {
        val item = repository.getAnyById(id) ?: return
        val local = Instant.ofEpochMilli(item.atMillis).atZone(clock.zone)
        _state.value = StandaloneEditorState(
            id = item.id,
            title = item.title,
            notes = item.notes.orEmpty(),
            date = local.toLocalDate(),
            time = local.toLocalTime(),
            reminders = repository.activeReminders(id).map { ReminderPlan(it.offsetMinutes, it.importance) },
            done = item.doneAt != null,
        )
        remindersEdited = true
    }

    public fun edit(change: (StandaloneEditorState) -> StandaloneEditorState) {
        _state.update(change)
    }

    public fun setReminders(list: List<ReminderPlan>) {
        remindersEdited = true
        _state.update { it.copy(reminders = list) }
    }

    public fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val reminders = s.reminders.distinctBy { it.offsetMinutes }.map { ReminderDraft(it.offsetMinutes, it.importance) }
            val existing = s.id?.let { repository.getAnyById(it) }
            if (existing == null) {
                service.createReminder(StandaloneDraft(s.title, s.at(clock.zone).toEpochMilli(), clock.zone, s.notes.ifBlank { null }), reminders)
            } else {
                service.saveReminder(
                    existing.copy(title = s.title, notes = s.notes.ifBlank { null }, atMillis = s.at(clock.zone).toEpochMilli(), zoneId = clock.zone),
                    reminders,
                )
            }
            finished.value = true
        }
    }

    public fun markDone() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            service.markReminderDone(id)
            finished.value = true
        }
    }

    public fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            service.deleteReminder(id)
            finished.value = true
        }
    }

    public companion object {
        public const val ARG_ID: String = "reminderId"

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val id = createSavedStateHandle().get<String>(ARG_ID)?.takeIf { it != EventEditorViewModel.NEW }
                StandaloneEditorViewModel(id, c.eventService, c.standaloneReminderRepository, c.clock) {
                    c.settingsRepository.current().reminderDefaults
                }
            }
        }
    }
}
