package com.sanjith.betterlife.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.ConsistencyCalculator
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.StreakCalculator
import com.sanjith.betterlife.domain.habit.StreakSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The heatmap's weeks (Monday first), oldest first; each week holds 7 day states. */
public data class Heatmap(val firstMonday: LocalDate, val weeks: List<List<DayState>>) {
    val doneDays: Int get() = weeks.sumOf { week -> week.count { it.counts } }
}

/** One habit's page: numbers, the last 12 months as a heatmap, and this month's grid. */
public data class HabitDetailUi(
    val habit: HabitEntity,
    val streak: StreakSummary,
    val consistency: Consistency,
    val heatmap: Heatmap,
    val boxes: List<GridBox>,
    val haptics: Boolean,
)

/** Habit detail (Prompt 15). Everything is derived from the logs, like the list. */
public class HabitDetailViewModel(
    private val habitId: String,
    habits: HabitRepository,
    private val service: HabitService,
    settings: kotlinx.coroutines.flow.Flow<com.sanjith.betterlife.domain.settings.AppSettings>,
) : ViewModel() {

    public val ui: StateFlow<HabitDetailUi?> = combine(
        habits.observeById(habitId),
        habits.observeLogs(habitId),
        habits.observePauses(),
        settings,
    ) { habit, logs, pauses, s ->
        habit ?: return@combine null
        val today = service.today()
        val history = HabitHistory(habit.startDate, logs.associate { it.localDate to it.status }, pauses)
        HabitDetailUi(
            habit = habit,
            streak = StreakCalculator.summary(history, today),
            consistency = ConsistencyCalculator.calculate(history, today),
            heatmap = heatmap(history, today),
            boxes = HabitGridModel.boxes(history, YearMonth.from(today), today),
            haptics = s.habitTickHaptics,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    public fun toggle(box: GridBox) {
        val date = box.date ?: return
        if (!box.state.tappable) return
        viewModelScope.launch {
            if (box.state.filled || box.state == BoxState.PAUSED) service.untick(habitId, date) else service.tick(habitId, date)
        }
    }

    public companion object {
        public const val ARG_ID: String = "habitId"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** 53 weeks: the last 12 months plus the week that started them. */
        public const val HEATMAP_WEEKS: Int = 53
        private const val DAYS_PER_WEEK = 7

        /** The last [HEATMAP_WEEKS] weeks ending with the week of [today], each Monday to Sunday. */
        public fun heatmap(history: HabitHistory, today: LocalDate): Heatmap {
            val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val firstMonday = thisMonday.minusWeeks((HEATMAP_WEEKS - 1).toLong())
            val weeks = (0 until HEATMAP_WEEKS).map { w ->
                (0 until DAYS_PER_WEEK).map { d -> history.state(firstMonday.plusDays((w * DAYS_PER_WEEK + d).toLong()), today) }
            }
            return Heatmap(firstMonday, weeks)
        }

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                HabitDetailViewModel(checkNotNull(createSavedStateHandle().get<String>(ARG_ID)), c.habitRepository, c.habitService, c.settingsRepository.settings)
            }
        }
    }
}
