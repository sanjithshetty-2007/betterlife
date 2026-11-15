package com.sanjith.betterlife.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.domain.calendar.CalendarFilter
import com.sanjith.betterlife.domain.calendar.CalendarMarks
import com.sanjith.betterlife.domain.calendar.DayMarks
import com.sanjith.betterlife.domain.calendar.CalendarRanges
import com.sanjith.betterlife.domain.calendar.CalendarView
import com.sanjith.betterlife.domain.calendar.DateRange
import com.sanjith.betterlife.domain.calendar.GridDay
import com.sanjith.betterlife.domain.calendar.MonthGrid
import com.sanjith.betterlife.domain.calendar.TimeGridLayout
import com.sanjith.betterlife.domain.event.Agenda
import com.sanjith.betterlife.domain.event.AgendaDay
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.feature.events.colorIndexOf
import com.sanjith.betterlife.feature.events.toAgendaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Everything the Calendar tab draws (Prompt 18). Only the visible range is loaded and laid out. */
public data class CalendarUi(
    val view: CalendarView,
    val anchor: LocalDate,
    val today: LocalDate,
    val weekStart: DayOfWeek,
    val range: DateRange,
    /** Filtered occurrences by day inside [range] (empty days left out): agenda, sheet and month list. */
    val days: List<AgendaDay> = emptyList(),
    /** Week and day views: one column per date. */
    val grid: List<GridDay> = emptyList(),
    val monthGrid: MonthGrid? = null,
    val marks: Map<LocalDate, DayMarks> = emptyMap(),
    val categories: List<EventCategory> = emptyList(),
    val filter: CalendarFilter = CalendarFilter(),
    val sheetDay: LocalDate? = null,
    val loading: Boolean = true,
) {
    public fun day(date: LocalDate): AgendaDay? = days.firstOrNull { it.date == date }

    public fun colorIndexOf(category: String?): Int? = categories.colorIndexOf(category)
}

private data class Controls(
    val view: CalendarView,
    val anchor: LocalDate,
    val filter: CalendarFilter = CalendarFilter(),
    val sheetDay: LocalDate? = null,
)

/** The rows for one view, anchor and week start: re-queried only when one of those changes. */
private data class Loaded(
    val view: CalendarView,
    val anchor: LocalDate,
    val weekStart: DayOfWeek,
    val range: DateRange,
    val events: List<EventEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<StandaloneReminderEntity>,
    val categories: List<EventCategory>,
)

/** The Calendar tab: day, week, month and agenda views, the day sheet and the category filter. */
@OptIn(ExperimentalCoroutinesApi::class)
public class CalendarViewModel(
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val standalone: StandaloneReminderRepository,
    private val categories: Flow<List<EventCategory>>,
    weekStart: Flow<DayOfWeek>,
    private val clock: Clock,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls(CalendarView.MONTH, clock.today()))

    // The filter and the sheet only change what is shown: they never re-run the queries.
    private val loaded: Flow<Loaded> = combine(
        controls.map { it.view to it.anchor }.distinctUntilChanged(),
        weekStart.distinctUntilChanged(),
    ) { (view, anchor), start -> Triple(view, anchor, start) }
        .flatMapLatest { (view, anchor, start) -> load(view, anchor, start) }

    public val ui: StateFlow<CalendarUi> = combine(loaded, controls, ::build)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial())

    private fun initial(): CalendarUi {
        val today = clock.today()
        return CalendarUi(CalendarView.MONTH, today, today, DayOfWeek.MONDAY, CalendarRanges.range(CalendarView.MONTH, today, DayOfWeek.MONDAY))
    }

    // One day either side, so an event running past midnight into the range is still found.
    private fun load(view: CalendarView, anchor: LocalDate, weekStart: DayOfWeek): Flow<Loaded> {
        val range = CalendarRanges.range(view, anchor, weekStart)
        val zone = clock.zone
        val fromMillis = range.from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = range.to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return combine(
            events.observeInRange(fromMillis, toMillis, range.from.minusDays(1), range.to),
            tasks.observeOpenInRange(fromMillis, toMillis, range.from, range.to),
            standalone.observeInRange(fromMillis, toMillis),
            categories,
        ) { e, t, r, cats -> Loaded(view, anchor, weekStart, range, e, t, r, cats) }
    }

    // The view and anchor come from the loaded rows, so a view switch never draws old rows in a new layout.
    private fun build(data: Loaded, c: Controls): CalendarUi {
        val zone = clock.zone
        val range = data.range
        val view = data.view
        val items = data.events.map { it.toAgendaItem() } + data.tasks.mapNotNull { it.toAgendaItem() } + data.reminders.map { it.toAgendaItem() }
        val built = Agenda.build(items, range.from.minusDays(1), range.days + 1, zone)
            .map { day -> day.copy(entries = day.entries.filter(c.filter::keeps)) }
            .filter { it.entries.isNotEmpty() }
        val inRange = built.filter { it.date in range }
        val gridViews = view == CalendarView.WEEK || view == CalendarView.DAY
        return CalendarUi(
            view = view,
            anchor = data.anchor,
            today = clock.today(),
            weekStart = data.weekStart,
            range = range,
            days = inRange,
            grid = if (gridViews) TimeGridLayout.layout(built.flatMap { it.entries }, range.dates(), zone) else emptyList(),
            monthGrid = if (view == CalendarView.MONTH) CalendarRanges.monthGrid(YearMonth.from(data.anchor), data.weekStart) else null,
            marks = if (view == CalendarView.MONTH) CalendarMarks.of(inRange) { data.categories.colorIndexOf(it) } else emptyMap(),
            categories = data.categories,
            filter = c.filter,
            sheetDay = c.sheetDay,
            loading = false,
        )
    }

    public fun select(view: CalendarView) {
        controls.update { it.copy(view = view, sheetDay = null) }
    }

    public fun previous(): Unit = step(-1)

    public fun next(): Unit = step(1)

    private fun step(steps: Int) {
        controls.update { it.copy(anchor = CalendarRanges.shift(it.view, it.anchor, steps), sheetDay = null) }
    }

    public fun goToToday() {
        controls.update { it.copy(anchor = clock.today(), sheetDay = null) }
    }

    public fun toggleCategory(name: String) {
        controls.update { it.copy(filter = it.filter.toggleCategory(name)) }
    }

    public fun toggleBirthdays() {
        controls.update { it.copy(filter = it.filter.toggleBirthdays()) }
    }

    public fun openDay(date: LocalDate) {
        controls.update { it.copy(sheetDay = date) }
    }

    public fun closeSheet() {
        controls.update { it.copy(sheetDay = null) }
    }

    public val zone: ZoneId get() = clock.zone

    /** The current wall-clock time for the now line (through the app's clock, so the Time Machine moves it too). */
    public fun now(): java.time.ZonedDateTime = clock.now().atZone(clock.zone)

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                CalendarViewModel(
                    c.eventRepository, c.taskRepository, c.standaloneReminderRepository, c.eventCategoryStore.categories,
                    c.settingsRepository.settings.map { it.weekStart }, c.clock,
                )
            }
        }
    }
}
