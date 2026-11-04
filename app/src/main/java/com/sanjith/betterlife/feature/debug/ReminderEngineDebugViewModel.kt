package com.sanjith.betterlife.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.data.repository.ReminderRepository
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.settings.SettingsRepository
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.reminders.engine.ReminderEngine
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

public data class ReminderEngineDebugUiState(
    val scheduled: List<ReminderEntity> = emptyList(),
    val log: List<ReminderLogEntity> = emptyList(),
    val hideTitles: Boolean = false,
)

/** Debug-only: creates test reminders through the real repositories and engine. */
public class ReminderEngineDebugViewModel(
    private val tasks: TaskRepository,
    reminders: ReminderRepository,
    reminderLog: ReminderLogRepository,
    private val engine: ReminderEngine,
    private val settings: SettingsRepository,
    private val realNow: () -> Instant,
) : ViewModel() {

    public val uiState: StateFlow<ReminderEngineDebugUiState> =
        combine(reminders.observeScheduled(), reminderLog.observeRecent(LOG_ROWS), settings.settings) { scheduled, log, s ->
            ReminderEngineDebugUiState(scheduled, log, s.hideTitleOnLockScreen)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReminderEngineDebugUiState())

    /** Completes the test tasks from outside the pop-up: a showing pop-up for them must close. */
    public fun completeTests() {
        viewModelScope.launch {
            tasks.activeIdsInCategory(TEST_MARKER).forEach { tasks.complete(it) }
            engine.rescheduleAll(RescheduleTrigger.MANUAL)
        }
    }

    public fun setHideTitles(hide: Boolean) {
        viewModelScope.launch { settings.update { it.copy(hideTitleOnLockScreen = hide) } }
    }

    /** A task due in [minutes] (real time) with one at-time reminder of [level]. */
    public fun createTest(minutes: Long, level: Importance) {
        viewModelScope.launch {
            val due = realNow().plus(Duration.ofMinutes(minutes))
            tasks.create(
                TaskDraft(
                    title = "Test ${level.name} +$minutes min",
                    dueAtMillis = due.toEpochMilli(),
                    dueZoneId = ZoneId.systemDefault(),
                    category = TEST_MARKER,
                    importance = level,
                ),
                listOf(ReminderDraft(offsetMinutes = 0, importance = level)),
            )
            engine.rescheduleAll(RescheduleTrigger.MANUAL)
        }
    }

    public fun rescheduleAll() {
        viewModelScope.launch { engine.rescheduleAll(RescheduleTrigger.MANUAL) }
    }

    /** Soft-deletes every test task; the reschedule then cancels their alarms. */
    public fun removeTests() {
        viewModelScope.launch {
            tasks.activeIdsInCategory(TEST_MARKER).forEach { tasks.softDelete(it) }
            engine.rescheduleAll(RescheduleTrigger.MANUAL)
        }
    }

    public companion object {
        private const val TEST_MARKER = "debug-engine"
        private const val LOG_ROWS = 25
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                ReminderEngineDebugViewModel(
                    c.taskRepository,
                    c.reminderRepository,
                    c.reminderLogRepository,
                    c.reminderEngine,
                    c.settingsRepository,
                    realNow = { c.realClock.now() },
                )
            }
        }
    }
}
