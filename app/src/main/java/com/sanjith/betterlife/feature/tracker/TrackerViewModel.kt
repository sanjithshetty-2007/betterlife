package com.sanjith.betterlife.feature.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.tracker.TrackerRepository
import com.sanjith.betterlife.data.tracker.TrackerSaveResult
import com.sanjith.betterlife.data.tracker.TrackerService
import com.sanjith.betterlife.data.tracker.TrackerSettings
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.tracker.PeriodStats
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.domain.tracker.TrackerRules
import com.sanjith.betterlife.domain.tracker.TrackerStats
import com.sanjith.betterlife.domain.tracker.TrackerStreaks
import com.sanjith.betterlife.feature.habits.GridBox
import com.sanjith.betterlife.feature.habits.HabitDetailViewModel
import com.sanjith.betterlife.feature.habits.HabitGridModel
import com.sanjith.betterlife.feature.habits.Heatmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * One tracker's dashboard. [latest] is the newest loggable day (today, or last night for sleep);
 * [latestTarget] is its saved target, or the goal when it has no entry. [boxes] and [heatmap] are null
 * until there is a first day.
 */
public data class TrackerUi(
    val kind: TrackerKind,
    val settings: TrackerSettings,
    val latest: LocalDate,
    val latestAmount: Int?,
    val latestTarget: Int?,
    val earlierAmount: Int?,
    val week: PeriodStats,
    val month: PeriodStats,
    val streaks: TrackerStreaks,
    val boxes: List<GridBox>?,
    val heatmap: Heatmap?,
) {
    val hasGoal: Boolean get() = settings.target != null
}

/** A tracker (protein, sleep; owner's features, 2026-11-21): latest day, the fix before it, week, month, streaks, goal and reminder. */
public class TrackerViewModel(
    kind: TrackerKind,
    repository: TrackerRepository,
    private val service: TrackerService,
    trackerSettings: Flow<TrackerSettings>,
    appSettings: Flow<AppSettings>,
    private val today: suspend () -> LocalDate,
) : ViewModel() {

    public val ui: StateFlow<TrackerUi?> = combine(repository.observeDays(), trackerSettings, appSettings) { days, settings, app ->
        val latest = TrackerRules.latestDay(kind, today())
        val byDate = days.associateBy { it.date }
        val first = TrackerRules.firstDay(settings.goalSetOn, days)
        val entry = byDate[latest]
        TrackerUi(
            kind = kind,
            settings = settings,
            latest = latest,
            latestAmount = entry?.amount,
            latestTarget = entry?.target ?: settings.target,
            earlierAmount = byDate[latest.minusDays(1)]?.amount,
            week = TrackerStats.week(byDate, latest, app.weekStart, first),
            month = TrackerStats.month(byDate, latest, first),
            streaks = TrackerStats.streaks(days, latest, first),
            boxes = first?.let { HabitGridModel.boxes(TrackerRules.history(days, it), YearMonth.from(latest), latest) },
            heatmap = first?.let { HabitDetailViewModel.heatmap(TrackerRules.history(days, it), latest) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The last save that was refused (shown once, then cleared). */
    private val _refused = MutableStateFlow<TrackerSaveResult?>(null)
    public val refused: StateFlow<TrackerSaveResult?> = _refused

    public fun save(date: LocalDate, amount: Int) {
        viewModelScope.launch {
            val result = service.save(date, amount)
            if (result != TrackerSaveResult.SAVED) _refused.value = result
        }
    }

    public fun refusalShown() {
        _refused.value = null
    }

    public fun setTarget(target: Int) {
        viewModelScope.launch { service.setTarget(target) }
    }

    /** The first goal: the target and the reminder time, with the reminder on. */
    public fun start(target: Int, time: LocalTime) {
        viewModelScope.launch {
            service.setTarget(target)
            service.setReminder(on = true, time = time)
        }
    }

    public fun setReminder(on: Boolean, time: LocalTime) {
        viewModelScope.launch { service.setReminder(on, time) }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public fun factory(kind: TrackerKind): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val tracker = c.trackers[kind]
                TrackerViewModel(kind, tracker.repository, tracker.service, tracker.settingsStore.settings, c.settingsRepository.settings, c::habitToday)
            }
        }
    }
}
