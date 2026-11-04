package com.sanjith.betterlife.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.data.repository.withDue
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.TaskRules
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

public class TaskEditorViewModel(
    private val taskId: String?,
    private val service: TaskService,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskEditorState(id = taskId))
    public val state: StateFlow<TaskEditorState> = _state

    public val projectList: StateFlow<List<ProjectEntity>> =
        projects.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Becomes true once saved, so the screen can close. */
    public val saved: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        taskId?.let { id -> viewModelScope.launch { load(id) } }
        taskId?.let { id ->
            viewModelScope.launch {
                tasks.observeActive().map { all -> all.filter { it.parentTaskId == id } }.collect { subs ->
                    _state.update { s -> s.copy(subtasks = subs.map { SubtaskUi(it.id, it.title, it.completedAt != null) }) }
                }
            }
        }
    }

    private suspend fun load(id: String) {
        val task = tasks.getAnyById(id) ?: return
        val due = task.due
        val (repeat, interval, days) = TaskEditorState.repeatFields(RecurrenceRuleText.decode(task.recurrenceRule))
        val local = (due as? TaskDue.At)?.instant?.atZone(due.zone)
        _state.update {
            it.copy(
                title = task.title,
                notes = task.notes.orEmpty(),
                dueDate = local?.toLocalDate() ?: (due as? TaskDue.OnDay)?.date,
                dueTime = local?.toLocalTime(),
                priority = task.priority,
                projectId = task.projectId,
                category = task.category.orEmpty(),
                repeat = repeat,
                interval = interval,
                weekdays = days,
                repeatFromCompletion = task.repeatFromCompletion,
                importance = task.importance,
                reminders = tasks.activeReminders(id).map { r -> ReminderPlan(r.offsetMinutes, r.importance) },
            )
        }
    }

    public fun edit(change: (TaskEditorState) -> TaskEditorState) {
        _state.update { before ->
            val after = change(before)
            // A brand-new task that just got its first date gets the default reminders.
            val firstDate = before.dueDate == null && after.dueDate != null && before.id == null && before.reminders.isEmpty()
            if (firstDate) after.copy(reminders = TaskRules.defaultReminders(after.due(clock.zone))) else after
        }
    }

    public fun setDate(date: LocalDate?) = edit { it.copy(dueDate = date, dueTime = if (date == null) null else it.dueTime) }

    public fun setTime(time: LocalTime?) = edit { it.copy(dueTime = time) }

    public fun save() {
        val s = _state.value
        if (s.errors().isNotEmpty()) return
        viewModelScope.launch {
            val reminders = s.reminders.distinctBy { it.offsetMinutes }.map { ReminderDraft(it.offsetMinutes, it.importance) }
            val existing = s.id?.let { tasks.getAnyById(it) }
            if (existing == null) {
                service.create(draftFrom(s), reminders)
            } else {
                service.save(
                    existing.withDue(s.due(clock.zone)).copy(
                        title = s.title.trim(), notes = s.notes.ifBlank { null }, priority = s.priority, projectId = s.projectId,
                        category = s.category.ifBlank { null }, recurrenceRule = s.rule()?.let(RecurrenceRuleText::encode),
                        repeatFromCompletion = s.repeatFromCompletion, importance = s.importance,
                    ),
                    reminders,
                )
            }
            saved.value = true
        }
    }

    public fun addSubtask(title: String) {
        val parent = _state.value.id ?: return
        if (title.isBlank()) return
        viewModelScope.launch { service.create(TaskDraft(title.trim(), parentTaskId = parent), emptyList()) }
    }

    public fun completeSubtask(id: String) {
        viewModelScope.launch { service.complete(id) }
    }

    public fun deleteSubtask(id: String) {
        viewModelScope.launch { service.delete(id) }
    }

    public fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = projects.create(name)
            edit { it.copy(projectId = id) }
        }
    }

    private fun draftFrom(s: TaskEditorState): TaskDraft {
        val base = TaskDraft(
            title = s.title.trim(), notes = s.notes.ifBlank { null }, priority = s.priority, projectId = s.projectId,
            category = s.category.ifBlank { null }, recurrenceRule = s.rule()?.let(RecurrenceRuleText::encode),
            repeatFromCompletion = s.repeatFromCompletion, importance = s.importance,
        )
        return when (val due = s.due(clock.zone)) {
            is TaskDue.At -> base.copy(dueAtMillis = due.instant.toEpochMilli(), dueZoneId = due.zone)
            is TaskDue.OnDay -> base.copy(dueDate = due.date)
            TaskDue.None -> base
        }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        public const val ARG_TASK_ID: String = "taskId"

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val id = createSavedStateHandle().get<String>(ARG_TASK_ID)?.takeIf { it != NEW_TASK }
                TaskEditorViewModel(id, c.taskService, c.taskRepository, c.projectRepository, c.clock)
            }
        }

        /** Route value meaning "create a new task". */
        public const val NEW_TASK: String = "new"
    }
}
