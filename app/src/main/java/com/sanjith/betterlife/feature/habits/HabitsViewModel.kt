package com.sanjith.betterlife.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.repository.HabitEditResult
import com.sanjith.betterlife.data.repository.HabitRepository
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.ConsistencyCalculator
import com.sanjith.betterlife.domain.habit.HabitHistory
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PausePeriod
import com.sanjith.betterlife.domain.habit.StreakCalculator
import com.sanjith.betterlife.domain.habit.StreakSummary
import com.sanjith.betterlife.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One habit card: who it is, its numbers (consistency first) and the 31 boxes of the month shown. */
public data class HabitCardUi(
    val id: String,
    val name: String,
    val avatar: String?,
    val colorHex: String,
    val essential: Boolean,
    val streak: StreakSummary,
    val consistency: Consistency,
    val boxes: List<GridBox>,
    val doneInMonth: Int,
)

/** The Habits screen. [cards] is null until the first load. */
public data class HabitsUi(
    val month: YearMonth,
    val today: LocalDate,
    val cards: List<HabitCardUi>?,
    val doneToday: Int,
    val haptics: Boolean,
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.from(today)
}

/** A tick or untick the owner can take back from the snackbar; [before] is what the day was. */
public data class HabitUndo(val habitId: String, val name: String, val date: LocalDate, val ticked: Boolean, val before: HabitLogStatus?)

/**
 * Habits list (Prompt 15). Streaks and consistency are worked out from the logs on every change,
 * never stored. "Today" is the habit day after the rollover hour (03:00 by default).
 */
public class HabitsViewModel(
    private val habits: HabitRepository,
    private val service: HabitService,
    settings: Flow<AppSettings>,
    private val updateSettings: suspend ((AppSettings) -> AppSettings) -> Unit,
) : ViewModel() {

    /** The month shown; null = the current one (it follows the day as it changes). */
    private val shownMonth = MutableStateFlow<YearMonth?>(null)

    /** The current habit month as of the last load; month taps step from the month state, not from the screen. */
    @Volatile private var thisMonth: YearMonth? = null
    private val _undo = MutableStateFlow<HabitUndo?>(null)
    public val undo: StateFlow<HabitUndo?> = _undo

    private val logsByHabit: Flow<Map<String, List<HabitLogEntity>>> = habits.observeAllLogs().map { list -> list.groupBy { it.habitId } }

    public val ui: StateFlow<HabitsUi?> = combine(
        habits.observeActive(),
        logsByHabit,
        habits.observePauses(),
        shownMonth,
        settings,
    ) { habitList, logs, pauses, month, s ->
        val today = service.today()
        thisMonth = YearMonth.from(today)
        val shown = month ?: YearMonth.from(today)
        val cards = habitList.map { card(it, logs[it.id].orEmpty(), pauses, shown, today) }
        HabitsUi(
            month = shown,
            today = today,
            cards = cards,
            doneToday = habitList.count { h -> logs[h.id].orEmpty().any { it.localDate == today && it.status != HabitLogStatus.PAUSED } },
            haptics = s.habitTickHaptics,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Ticks an open box, unticks a filled or paused one; greyed and future boxes do nothing. */
    public fun toggle(card: HabitCardUi, box: GridBox) {
        val date = box.date ?: return
        if (!box.tappable) return
        val before = when (box.state) {
            BoxState.DONE -> HabitLogStatus.DONE
            BoxState.KEPT -> HabitLogStatus.KEPT
            BoxState.PAUSED -> HabitLogStatus.PAUSED
            else -> null
        }
        viewModelScope.launch {
            val result = if (before == null) service.tick(card.id, date) else service.untick(card.id, date)
            if (result is HabitEditResult.Saved) _undo.value = HabitUndo(card.id, card.name, date, ticked = before == null, before = before)
        }
    }

    /** Puts the day back the way it was before the last tap. */
    public fun undo(offer: HabitUndo) {
        _undo.value = null
        viewModelScope.launch {
            val before = offer.before
            if (before == null) service.untick(offer.habitId, offer.date) else service.tick(offer.habitId, offer.date, before)
        }
    }

    public fun undoExpired(offer: HabitUndo) {
        if (_undo.value == offer) _undo.value = null
    }

    public fun previousMonth() {
        val current = thisMonth ?: return
        shownMonth.update { (it ?: current).minusMonths(1) }
    }

    /** Never past the current month: future days cannot be ticked anyway. */
    public fun nextMonth() {
        val current = thisMonth ?: return
        shownMonth.update { shown -> shown?.plusMonths(1)?.takeIf { it < current } }
    }

    public fun backToThisMonth() {
        shownMonth.value = null
    }

    public fun setHaptics(on: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(habitTickHaptics = on) } }
    }

    private fun card(habit: HabitEntity, logs: List<HabitLogEntity>, pauses: List<PausePeriod>, month: YearMonth, today: LocalDate): HabitCardUi {
        val history = HabitHistory(habit.startDate, logs.associate { it.localDate to it.status }, pauses)
        val boxes = HabitGridModel.boxes(history, month, today)
        return HabitCardUi(
            id = habit.id,
            name = habit.name,
            avatar = habit.avatar,
            colorHex = habit.colorHex,
            essential = habit.essential,
            streak = StreakCalculator.summary(history, today),
            consistency = ConsistencyCalculator.calculate(history, today),
            boxes = boxes,
            doneInMonth = HabitGridModel.doneCount(boxes),
        )
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                HabitsViewModel(c.habitRepository, c.habitService, c.settingsRepository.settings, c.settingsRepository::update)
            }
        }
    }
}
