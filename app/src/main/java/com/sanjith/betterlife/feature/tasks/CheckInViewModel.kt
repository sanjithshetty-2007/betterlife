package com.sanjith.betterlife.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.ProjectRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.settings.SettingsRepository
import com.sanjith.betterlife.data.settings.TopTasksStore
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.domain.time.zonedNow
import com.sanjith.betterlife.reminders.daily.DailyPrompts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime

public data class CheckInUiState(
    /** Today's and overdue open tasks: what the morning picks from and the evening reviews. */
    val open: List<TaskRowUi> = emptyList(),
    val top: Set<String> = emptySet(),
    val doneToday: Int = 0,
    val settings: AppSettings = AppSettings(),
)

/** Morning check-in (pick today's top 3), evening wrap-up (review and move unfinished), and their nudges. */
public class CheckInViewModel(
    private val service: TaskService,
    tasks: TaskRepository,
    projects: ProjectRepository,
    private val topTasks: TopTasksStore,
    private val settings: SettingsRepository,
    private val dailyPrompts: DailyPrompts,
    private val clock: Clock,
) : ViewModel() {

    public val state: StateFlow<CheckInUiState> =
        combine(tasks.observeActive(), projects.observeActive(), topTasks.observe { clock.today() }, settings.settings) { all, p, top, s ->
            val now = clock.zonedNow()
            val tabs = buildTaskTabs(all, p, top, now)
            val doneToday = all.count { t ->
                t.parentTaskId == null && t.completedAt?.let { Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate() } == now.toLocalDate()
            }
            CheckInUiState(tabs.overdue + tabs.today, top, doneToday, s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CheckInUiState())

    /** Picks or unpicks a top task; at most three. */
    public fun toggleTop(id: String) {
        val current = state.value.top
        val next = if (id in current) current - id else current + id
        if (next.size > TopTasksStore.MAX_TOP) return
        viewModelScope.launch { topTasks.save(clock.today(), next) }
    }

    public fun moveToTomorrow(id: String) {
        viewModelScope.launch { service.moveToTomorrow(id) }
    }

    public fun moveAllToTomorrow() {
        val ids = state.value.open.map { it.id }
        viewModelScope.launch { ids.forEach { service.moveToTomorrow(it) } }
    }

    public fun setMorning(enabled: Boolean, time: LocalTime) = updatePrompts { it.copy(morningCheckInEnabled = enabled, morningCheckInTime = time) }

    public fun setEvening(enabled: Boolean, time: LocalTime) = updatePrompts { it.copy(eveningWrapUpEnabled = enabled, eveningWrapUpTime = time) }

    private fun updatePrompts(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settings.update(change)
            dailyPrompts.rescheduleAll()
        }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                CheckInViewModel(c.taskService, c.taskRepository, c.projectRepository, c.topTasksStore, c.settingsRepository, c.dailyPrompts, c.clock)
            }
        }
    }
}
