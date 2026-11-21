package com.sanjith.betterlife.feature.silent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.silent.SilentReminderRepository
import com.sanjith.betterlife.domain.silent.SilentReminder
import com.sanjith.betterlife.domain.silent.SilentRules
import com.sanjith.betterlife.domain.silent.VibrationPattern
import com.sanjith.betterlife.reminders.silent.SilentBuzzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The Silent tab in Tasks: every silent reminder, its switch, and delete with Undo. */
public class SilentListViewModel(private val repository: SilentReminderRepository) : ViewModel() {

    public val reminders: StateFlow<List<SilentReminder>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The reminder just deleted, while its Undo is offered. */
    public val undo: MutableStateFlow<SilentReminder?> = MutableStateFlow(null)

    public fun setEnabled(reminder: SilentReminder, on: Boolean) {
        viewModelScope.launch { repository.setEnabled(reminder.id, on) }
    }

    public fun delete(reminder: SilentReminder) {
        viewModelScope.launch {
            repository.delete(reminder.id)
            undo.value = reminder
        }
    }

    public fun undo(reminder: SilentReminder) {
        viewModelScope.launch { repository.restore(reminder.id) }
        undo.value = null
    }

    public fun undoExpired() {
        undo.value = null
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                SilentListViewModel(c.silentReminderRepository)
            }
        }
    }
}

/** What the editor holds. A reminder needs a name and at least one time. */
public data class SilentEditorState(
    val id: String? = null,
    val name: String = "",
    val times: List<LocalTime> = emptyList(),
    val pattern: VibrationPattern = VibrationPattern.PULSE,
    val seconds: Int = SilentRules.DEFAULT_SECONDS,
    val enabled: Boolean = true,
) {
    val canSave: Boolean get() = name.isNotBlank() && times.isNotEmpty()
}

/** Create or edit one silent reminder. */
public class SilentEditorViewModel(
    private val id: String?,
    private val repository: SilentReminderRepository,
    private val buzzer: SilentBuzzer,
) : ViewModel() {

    private val _state = MutableStateFlow(SilentEditorState(id = id))
    public val state: StateFlow<SilentEditorState> = _state

    /** Becomes true once saved or deleted, so the screen can close. */
    public val finished: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        if (id != null) {
            viewModelScope.launch {
                repository.get(id)?.let { r -> _state.value = SilentEditorState(r.id, r.name, r.times, r.pattern, r.seconds, r.enabled) }
            }
        }
    }

    public fun edit(change: (SilentEditorState) -> SilentEditorState) {
        _state.update(change)
    }

    public fun addTime(time: LocalTime) {
        _state.update { s -> s.copy(times = (s.times + time.withSecond(0).withNano(0)).distinct().sorted()) }
    }

    public fun removeTime(time: LocalTime) {
        _state.update { s -> s.copy(times = s.times - time) }
    }

    public fun tryPattern() {
        buzzer.preview(_state.value.pattern)
    }

    public fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repository.save(SilentReminder(s.id.orEmpty(), s.name, s.times, s.pattern, s.seconds, s.enabled))
            finished.value = true
        }
    }

    public fun delete() {
        val existing = _state.value.id ?: return
        viewModelScope.launch {
            repository.delete(existing)
            finished.value = true
        }
    }

    public companion object {
        public const val ARG_ID: String = "silentId"
        public const val NEW: String = "new"

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val id = createSavedStateHandle().get<String>(ARG_ID)?.takeIf { it != NEW }
                SilentEditorViewModel(id, c.silentReminderRepository, c.silentBuzzer)
            }
        }
    }
}
