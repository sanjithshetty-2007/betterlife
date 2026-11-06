package com.sanjith.betterlife.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.data.setup.HealthStore
import com.sanjith.betterlife.data.setup.SetupRepository
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.setup.ClosedAppTest
import com.sanjith.betterlife.domain.setup.ClosedAppTestResult
import com.sanjith.betterlife.domain.setup.FixAllWalk
import com.sanjith.betterlife.domain.setup.HealthSummary
import com.sanjith.betterlife.domain.setup.SetupCheck
import com.sanjith.betterlife.domain.setup.SetupStatus
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

public sealed interface SetupUiState {
    public data object Loading : SetupUiState

    /** [now] ticks every few seconds so "checked 2 min ago" and the test result stay current. */
    public data class Loaded(
        val status: SetupStatus,
        val summary: HealthSummary,
        val lastCheckedAt: Instant?,
        val closedAppTest: ClosedAppTestResult?,
        val now: Instant,
    ) : SetupUiState
}

/** What "Fix all" shows after the owner comes back from a settings page. */
public sealed interface FixAllPrompt {
    public data class Next(val check: SetupCheck) : FixAllPrompt
    public data class Finished(val stillRed: List<SetupCheck>) : FixAllPrompt
}

/** Reminder Health (Prompt 11): rows, score, Fix all walk and the two test reminders. */
public class SetupViewModel(
    private val repository: SetupRepository,
    healthStore: HealthStore,
    logs: ReminderLogRepository,
    private val tests: HealthTests?,
    private val realClock: Clock,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(realClock.now())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val closedAppLog = healthStore.closedAppTest.flatMapLatest { test ->
        if (test == null) flowOf(null) else logs.observeForReminder(test.reminderId).map { rows -> test to rows }
    }

    public val uiState: StateFlow<SetupUiState> = combine(
        repository.status,
        repository.lastCheckedAt,
        closedAppLog,
        ticker,
    ) { status, checked, testAndRows, now ->
        status ?: return@combine SetupUiState.Loading
        val closedResult = testAndRows?.let { (test, rows) ->
            ClosedAppTest.result(
                due = Instant.ofEpochMilli(test.dueMillis),
                now = now,
                firedAt = rows.firstOrNull { it.status == ReminderLogStatus.FIRED }?.let { Instant.ofEpochMilli(it.firedAt) },
                failed = rows.any { it.status == ReminderLogStatus.MISSED || it.status == ReminderLogStatus.FAILED },
            )
        }
        SetupUiState.Loaded(status, HealthSummary.of(status), checked?.let(Instant::ofEpochMilli), closedResult, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SetupUiState.Loading)

    /** Rows already opened during the current "Fix all"; null when no walk is running. */
    private var walkVisited: Set<SetupCheck>? = null

    public val fixAllPrompt: MutableStateFlow<FixAllPrompt?> = MutableStateFlow(null)

    /** Called on every resume, so coming back from system settings shows the new state. */
    public fun refresh() {
        viewModelScope.launch {
            repository.refresh()
            val visited = walkVisited ?: return@launch
            val status = repository.status.filterNotNull().first()
            fixAllPrompt.value = FixAllWalk.next(status, visited)?.let { FixAllPrompt.Next(it) }
                ?: FixAllPrompt.Finished(FixAllWalk.leftOver(status)).also { walkVisited = null }
        }
    }

    /** Starts "Fix all"; returns the first row to open, or null when nothing is red. */
    public fun startFixAll(): SetupCheck? {
        val status = (uiState.value as? SetupUiState.Loaded)?.status ?: return null
        val first = FixAllWalk.next(status, emptySet()) ?: return null
        walkVisited = setOf(first)
        return first
    }

    /** The owner agreed to open [check] next. */
    public fun visit(check: SetupCheck) {
        walkVisited = walkVisited.orEmpty() + check
        fixAllPrompt.value = null
    }

    public fun stopFixAll() {
        walkVisited = null
        fixAllPrompt.value = null
    }

    public fun setManualTick(check: SetupCheck, ticked: Boolean) {
        viewModelScope.launch { repository.setManualTick(check, ticked) }
    }

    public fun testQuick(title: String) {
        viewModelScope.launch { tests?.startQuick(title) }
    }

    public fun testAfterClosing(title: String) {
        viewModelScope.launch { tests?.startClosedApp(title) }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                SetupViewModel(c.setupRepository, c.healthStore, c.reminderLogRepository, c.healthTests, c.realClock)
            }
        }
    }
}
