package com.sanjith.betterlife.feature.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.dao.CenterLogRow
import com.sanjith.betterlife.data.repository.ReminderLogRepository
import com.sanjith.betterlife.domain.reminder.CenterFilter
import com.sanjith.betterlife.domain.reminder.ReminderLogRules
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of Reminder Center rows, newest first. */
public data class CenterDay(val date: LocalDate, val rows: List<CenterLogRow>)

/** Groups rows (already newest first) by their local day. Pure, so it is unit-tested. */
public fun groupByDay(rows: List<CenterLogRow>, zone: ZoneId): List<CenterDay> =
    rows.groupBy { Instant.ofEpochMilli(it.log.firedAt).atZone(zone).toLocalDate() }
        .map { (date, list) -> CenterDay(date, list) }

/**
 * The Reminder Center (Prompt 11): the reminder log of the last 60 days with filters. Times are
 * real phone time, like the log itself (the Time Machine never moves it).
 */
public class ReminderCenterViewModel(logs: ReminderLogRepository, private val realClock: Clock) : ViewModel() {

    public val filter: MutableStateFlow<CenterFilter> = MutableStateFlow(CenterFilter.ALL)

    private val since = ReminderLogRules.purgeCutoff(realClock.now()).toEpochMilli()

    public val days: StateFlow<List<CenterDay>?> = combine(logs.observeCenter(since, MAX_ROWS), filter) { rows, f ->
        groupByDay(rows.filter { f.matches(it.log.status) }, realClock.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    public fun today(): LocalDate = realClock.now().atZone(realClock.zone).toLocalDate()

    public val zone: ZoneId get() = realClock.zone

    public companion object {
        /** Enough for 60 days of a busy schedule; the list stays fast. */
        private const val MAX_ROWS = 2_000
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                ReminderCenterViewModel(c.reminderLogRepository, c.realClock)
            }
        }
    }
}
