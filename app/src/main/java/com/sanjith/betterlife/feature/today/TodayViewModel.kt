package com.sanjith.betterlife.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.habit.MascotMoodSource
import com.sanjith.betterlife.data.repository.PauseRangeRepository
import com.sanjith.betterlife.data.settings.TodayStore
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.today.Comeback
import com.sanjith.betterlife.domain.today.ComebackTrigger
import com.sanjith.betterlife.domain.today.TodayItem
import com.sanjith.betterlife.domain.today.TodayKind
import com.sanjith.betterlife.feature.habits.HabitService
import com.sanjith.betterlife.feature.tasks.TaskService
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The Today screen (Prompt 17): the snapshot plus Blip and the greeting's name. */
public data class TodayUi(val snapshot: TodaySnapshot, val ownerName: String, val mood: MascotMood, val stage: MascotStage, val hour: Int)

/**
 * Today: the ranked list, the progress numbers, Blip's mood, and the gentle features (Minimum Day,
 * Comeback, habit pause). Recomputed whenever tasks, events, habits, logs, pauses or settings change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class TodayViewModel(
    db: BetterLifeDatabase,
    private val clock: Clock,
    private val settings: Flow<AppSettings>,
    private val source: TodaySource,
    private val mood: MascotMoodSource,
    private val habitService: HabitService,
    private val taskService: TaskService,
    private val pauses: PauseRangeRepository,
    private val todayStore: TodayStore,
    private val endRangePause: suspend () -> Unit,
    /** Only Today checks comeback and records the app open; Minimum Day and Comeback reuse this model without it. */
    checkComeback: Boolean = true,
) : ViewModel() {

    private val data: Flow<TodayData> = combine(
        combine(db.taskDao().observeActive(), db.eventDao().observeActive(), db.standaloneReminderDao().observeActive(), ::Triple),
        db.habitDao().observeActive(),
        db.habitLogDao().observeAllActive(),
        db.pauseRangeDao().observeActive(),
    ) { (tasks, events, standalone), habits, logs, ranges -> TodayData(tasks, events, standalone, habits, logs, ranges) }

    public val ui: StateFlow<TodayUi?> = combine(data, settings) { d, s -> d to s }
        .mapLatest { (d, s) ->
            val snapshot = source.snapshot(d, habitService.today())
            TodayUi(snapshot, s.ownerName, mood.mood().mood, mood.stage(), clock.now().atZone(clock.zone).hour)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _comeback = MutableStateFlow<ComebackTrigger?>(null)

    /** Set once per day when comeback mode should open (Prompt 17). */
    public val comeback: StateFlow<ComebackTrigger?> = _comeback

    init {
        if (checkComeback) viewModelScope.launch {
            val today = habitService.today()
            val before = todayStore.opened(today)
            val snapshot = ui.first { it != null }!!.snapshot
            _comeback.value = Comeback.trigger(today, before.lastOpened, before.comebackShownOn, snapshot.histories)
        }
    }

    /** True while the owner has not given a name yet (read fresh, never from a cached screen state). */
    public suspend fun needsName(): Boolean = settings.first().ownerName.isBlank()

    /** Comeback opened: not again today, and the bigger-reward flag is stored for XP (Prompt 26). */
    public fun comebackShown() {
        _comeback.value = null
        viewModelScope.launch { todayStore.comebackShown(habitService.today()) }
    }

    /** The round tick on a task or habit row. */
    public fun tick(item: TodayItem) {
        viewModelScope.launch {
            when (item.kind) {
                TodayKind.TASK -> taskService.complete(item.id)
                TodayKind.HABIT -> habitService.tick(item.id, habitService.today())
                TodayKind.EVENT, TodayKind.REMINDER -> Unit
            }
        }
    }

    public fun completeTask(id: String) {
        viewModelScope.launch { taskService.complete(id) }
    }

    /** Minimum Day and Comeback tick habits; Minimum Day records them as KEPT. */
    public fun tickHabit(id: String, status: HabitLogStatus = HabitLogStatus.DONE) {
        viewModelScope.launch { habitService.tick(id, habitService.today(), status) }
    }

    /** Starts a habit pause; [end] null = until ended. Reminders are held from the next check on. */
    public fun startPause(start: LocalDate, end: LocalDate?, reason: com.sanjith.betterlife.domain.habit.PauseReason) {
        viewModelScope.launch { pauses.start(start, end, reason) }
    }

    /** Ends today's pause: today is the last paused day, and anything held arrives in one digest. */
    public fun endPause(id: String) {
        viewModelScope.launch {
            pauses.end(id, habitService.today().minusDays(1))
            endRangePause()
        }
    }

    /** Minimum Day finished: every habit not settled today becomes a KEPT day, so no streak breaks. */
    public fun keepTheRest() {
        viewModelScope.launch {
            val snapshot = ui.value?.snapshot ?: return@launch
            snapshot.habits.filter { !it.settledToday }.forEach { habitService.tick(it.id, snapshot.today, HabitLogStatus.KEPT) }
        }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = factory(checkComeback = true)

        /** For Minimum Day and Comeback: the same data, without recording an app open. */
        public val GentleFactory: ViewModelProvider.Factory = factory(checkComeback = false)

        private fun factory(checkComeback: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                TodayViewModel(
                    db = c.database,
                    clock = c.clock,
                    settings = c.settingsRepository.settings,
                    source = TodaySource(c.database, c.clock),
                    mood = c.mascotMood,
                    habitService = c.habitService,
                    taskService = c.taskService,
                    pauses = c.pauseRangeRepository,
                    todayStore = c.todayStore,
                    endRangePause = { c.reminderEngine.deliverDigest() },
                    checkComeback = checkComeback,
                )
            }
        }
    }
}
