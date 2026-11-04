package com.sanjith.betterlife.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.data.alarm.PendingAlarmStore
import com.sanjith.betterlife.domain.alarm.AlarmKind
import com.sanjith.betterlife.domain.alarm.PendingTestAlarm
import com.sanjith.betterlife.reminders.alarm.ScheduleResult
import com.sanjith.betterlife.reminders.alarm.TestAlarmController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class AlarmSpikeUiState(
    val kind: AlarmKind = AlarmKind.EXACT_ALLOW_WHILE_IDLE,
    val pending: PendingTestAlarm? = null,
    val exactAlarmsDenied: Boolean = false,
    val log: List<String> = emptyList(),
)

public class AlarmSpikeViewModel(
    private val controller: TestAlarmController,
    store: PendingAlarmStore,
    private val log: DebugLog,
) : ViewModel() {

    private val kind = MutableStateFlow(AlarmKind.EXACT_ALLOW_WHILE_IDLE)
    private val exactAlarmsDenied = MutableStateFlow(false)

    public val uiState: StateFlow<AlarmSpikeUiState> =
        combine(kind, store.pending, exactAlarmsDenied, log.entries) { kind, pending, denied, entries ->
            AlarmSpikeUiState(kind, pending, denied, entries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlarmSpikeUiState())

    public fun setUseAlarmClock(useAlarmClock: Boolean) {
        kind.value = if (useAlarmClock) AlarmKind.ALARM_CLOCK else AlarmKind.EXACT_ALLOW_WHILE_IDLE
    }

    public fun scheduleIn(minutes: Int) {
        viewModelScope.launch {
            exactAlarmsDenied.value = controller.scheduleIn(minutes, kind.value) == ScheduleResult.EXACT_ALARMS_DENIED
        }
    }

    public fun cancel() {
        viewModelScope.launch { controller.cancel() }
    }

    public fun clearLog() {
        log.clear()
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                AlarmSpikeViewModel(app.container.testAlarmController, app.container.pendingAlarmStore, app.container.debugLog)
            }
        }
    }
}
