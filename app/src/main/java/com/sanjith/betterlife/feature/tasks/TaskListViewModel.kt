package com.sanjith.betterlife.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.CompletionResult
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.settings.TopTasksStore
import com.sanjith.betterlife.domain.parser.AmPm
import com.sanjith.betterlife.domain.parser.QuickAddParser
import com.sanjith.betterlife.domain.parser.QuickAddResult
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.domain.time.zonedNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An action the owner can still undo from the snackbar. */
public sealed interface UndoOffer {
    public val title: String
    public data class Completed(val result: CompletionResult, override val title: String) : UndoOffer
    public data class Deleted(val taskId: String, override val title: String) : UndoOffer
}

public class TaskListViewModel(
    private val service: TaskService,
    tasks: TaskRepository,
    projects: ProjectRepository,
    topTasks: TopTasksStore,
    private val clock: Clock,
) : ViewModel() {

    // Tabs depend on the time (a task becomes overdue as time passes), so re-sort every minute.
    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MILLIS)
        }
    }

    public val tabs: StateFlow<TaskTabs> =
        combine(tasks.observeActive(), projects.observeActive(), topTasks.observe { clock.today() }, minuteTick) { all, p, top, _ ->
            buildTaskTabs(all, p, top, clock.zonedNow())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TaskTabs())

    public val selectedTab: MutableStateFlow<TaskTab> = MutableStateFlow(TaskTab.TODAY)

    private val quickAddText = MutableStateFlow("")
    private val amPm = MutableStateFlow<AmPm?>(null)
    public val text: StateFlow<String> = quickAddText

    /** The always-visible preview; null while the field is empty. */
    public val preview: StateFlow<QuickAddResult?> = combine(quickAddText, amPm) { text, answer ->
        text.takeIf { it.isNotBlank() }?.let { QuickAddParser.parse(it, clock.zonedNow(), answer) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _undo = MutableStateFlow<UndoOffer?>(null)
    public val undo: StateFlow<UndoOffer?> = _undo

    public fun onQuickAddText(value: String) {
        quickAddText.value = value
        amPm.value = null
    }

    public fun answerAmPm(answer: AmPm) {
        amPm.value = answer
    }

    public fun submitQuickAdd() {
        val result = preview.value?.takeIf { it.isReadyToSave } ?: return
        quickAddText.value = ""
        amPm.value = null
        viewModelScope.launch { service.quickAdd(result) }
    }

    public fun complete(row: TaskRowUi) {
        viewModelScope.launch { _undo.value = UndoOffer.Completed(service.complete(row.id), row.title) }
    }

    public fun delete(row: TaskRowUi) {
        viewModelScope.launch {
            service.delete(row.id)
            _undo.value = UndoOffer.Deleted(row.id, row.title)
        }
    }

    public fun undo(offer: UndoOffer) {
        _undo.value = null
        viewModelScope.launch {
            when (offer) {
                is UndoOffer.Completed -> service.undoComplete(offer.result)
                is UndoOffer.Deleted -> service.undoDelete(offer.taskId)
            }
        }
    }

    public fun undoExpired(offer: UndoOffer) {
        if (_undo.value == offer) _undo.value = null
    }

    public companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                TaskListViewModel(c.taskService, c.taskRepository, c.projectRepository, c.topTasksStore, c.clock)
            }
        }
    }
}
