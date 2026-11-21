package com.sanjith.betterlife.feature.protein

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.protein.ProteinRepository
import com.sanjith.betterlife.data.protein.ProteinSaveResult
import com.sanjith.betterlife.data.protein.ProteinService
import com.sanjith.betterlife.data.protein.ProteinSettings
import com.sanjith.betterlife.domain.protein.PeriodStats
import com.sanjith.betterlife.domain.protein.ProteinRules
import com.sanjith.betterlife.domain.protein.ProteinStats
import com.sanjith.betterlife.domain.protein.ProteinStreaks
import com.sanjith.betterlife.domain.settings.AppSettings
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
 * The protein dashboard. [todayTarget] is today's saved target, or the goal when today has no entry.
 * [boxes] and [heatmap] are null until there is a first protein day.
 */
public data class ProteinUi(
    val settings: ProteinSettings,
    val today: LocalDate,
    val todayGrams: Int?,
    val todayTarget: Int?,
    val yesterdayGrams: Int?,
    val week: PeriodStats,
    val month: PeriodStats,
    val streaks: ProteinStreaks,
    val boxes: List<GridBox>?,
    val heatmap: Heatmap?,
) {
    val hasGoal: Boolean get() = settings.targetGrams != null
}

/** Protein (owner's feature, 2026-11-21): today, yesterday's fix, week, month, streaks, goal and reminder. */
public class ProteinViewModel(
    repository: ProteinRepository,
    private val service: ProteinService,
    proteinSettings: Flow<ProteinSettings>,
    appSettings: Flow<AppSettings>,
    private val today: suspend () -> LocalDate,
) : ViewModel() {

    public val ui: StateFlow<ProteinUi?> = combine(repository.observeDays(), proteinSettings, appSettings) { days, settings, app ->
        val day = today()
        val byDate = days.associateBy { it.date }
        val first = ProteinRules.firstDay(settings.goalSetOn, days)
        val todayEntry = byDate[day]
        ProteinUi(
            settings = settings,
            today = day,
            todayGrams = todayEntry?.grams,
            todayTarget = todayEntry?.targetGrams ?: settings.targetGrams,
            yesterdayGrams = byDate[day.minusDays(1)]?.grams,
            week = ProteinStats.week(byDate, day, app.weekStart, first),
            month = ProteinStats.month(byDate, day, first),
            streaks = ProteinStats.streaks(days, day, first),
            boxes = first?.let { HabitGridModel.boxes(ProteinRules.history(days, it), YearMonth.from(day), day) },
            heatmap = first?.let { HabitDetailViewModel.heatmap(ProteinRules.history(days, it), day) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The last save that was refused (shown once, then cleared). */
    private val _refused = MutableStateFlow<ProteinSaveResult?>(null)
    public val refused: StateFlow<ProteinSaveResult?> = _refused

    public fun save(date: LocalDate, grams: Int) {
        viewModelScope.launch {
            val result = service.save(date, grams)
            if (result != ProteinSaveResult.SAVED) _refused.value = result
        }
    }

    public fun refusalShown() {
        _refused.value = null
    }

    public fun setTarget(grams: Int) {
        viewModelScope.launch { service.setTarget(grams) }
    }

    /** The first goal: the target and the reminder time, with the reminder on. */
    public fun start(grams: Int, time: LocalTime) {
        viewModelScope.launch {
            service.setTarget(grams)
            service.setReminder(on = true, time = time)
        }
    }

    public fun setReminder(on: Boolean, time: LocalTime) {
        viewModelScope.launch { service.setReminder(on, time) }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                ProteinViewModel(c.proteinRepository, c.proteinService, c.proteinSettingsStore.settings, c.settingsRepository.settings, c::proteinToday)
            }
        }
    }
}
