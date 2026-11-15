package com.sanjith.betterlife.feature.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.calendar.CalendarView
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.feature.events.CategoryDot
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.ui.theme.pillColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/** What the Calendar tab can open: an item, a new item (optionally on a day), or the reminder defaults. */
public data class CalendarActions(
    val onOpen: (AgendaTarget) -> Unit,
    val onNewEvent: (EventType, LocalDate?) -> Unit,
    val onNewTask: (LocalDate) -> Unit,
    val onNewReminder: () -> Unit,
    val onOpenDefaults: () -> Unit,
)

/** The Calendar tab (Prompt 18): Day / Week / Month / Agenda, the category filter and the day sheet. */
@Composable
public fun CalendarScreen(actions: CalendarActions, viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)) {
    val context = LocalContext.current
    val dueText = remember { DueText(context) { viewModel.zone } }
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(ui, viewModel::goToToday, actions)
            ViewSwitcher(ui.view, viewModel::select)
            FilterRow(ui, viewModel::toggleCategory, viewModel::toggleBirthdays)
            Box(Modifier.weight(1f)) {
                when (ui.view) {
                    CalendarView.MONTH -> MonthView(ui, dueText, viewModel, actions.onOpen)
                    CalendarView.WEEK, CalendarView.DAY -> TimeGridView(ui, viewModel, actions.onOpen)
                    CalendarView.AGENDA -> AgendaView(ui, dueText, viewModel.zone, actions.onOpen)
                }
            }
        }
    }
    ui.sheetDay?.let { day ->
        DaySheet(
            day = day,
            entries = ui.day(day)?.entries.orEmpty(),
            ui = ui,
            dueText = dueText,
            zone = viewModel.zone,
            onDismiss = viewModel::closeSheet,
            onOpen = { target -> viewModel.closeSheet(); actions.onOpen(target) },
            onAddEvent = { viewModel.closeSheet(); actions.onNewEvent(EventType.EVENT, day) },
            onAddTask = { viewModel.closeSheet(); actions.onNewTask(day) },
        )
    }
}

/** The title per view ("November", "Week 45", "Fri 6 Nov", "Agenda"), Today when away from it, Defaults and +. */
@Composable
private fun Header(ui: CalendarUi, onToday: () -> Unit, actions: CalendarActions) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = titleOf(ui),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (ui.today !in ui.range) TextButton(onClick = onToday) { Text(stringResource(R.string.calendar_today)) }
        TextButton(onClick = actions.onOpenDefaults) { Text(stringResource(R.string.agenda_defaults)) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.agenda_new_event), tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.agenda_new_event)) }, onClick = { menu = false; actions.onNewEvent(EventType.EVENT, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.agenda_new_birthday)) }, onClick = { menu = false; actions.onNewEvent(EventType.BIRTHDAY, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.agenda_new_reminder)) }, onClick = { menu = false; actions.onNewReminder() })
            }
        }
    }
}

@Composable
private fun titleOf(ui: CalendarUi): String = when (ui.view) {
    CalendarView.MONTH -> ui.anchor.format(DateTimeFormatter.ofPattern(if (ui.anchor.year == ui.today.year) "MMMM" else "MMMM yyyy"))
    CalendarView.WEEK -> stringResource(R.string.calendar_week_title, ui.range.from.get(WeekFields.of(ui.weekStart, MIN_DAYS_IN_FIRST_WEEK).weekOfWeekBasedYear()))
    CalendarView.DAY -> ui.anchor.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    CalendarView.AGENDA -> stringResource(R.string.agenda_title)
}

// ISO weeks: the first week of the year is the one with at least four days in it.
private const val MIN_DAYS_IN_FIRST_WEEK = 4

/** Day / Week / Month / Agenda as one pill row (docs/design); the selected view is filled. */
@Composable
private fun ViewSwitcher(selected: CalendarView, onSelect: (CalendarView) -> Unit) {
    val views = listOf(CalendarView.DAY, CalendarView.WEEK, CalendarView.MONTH, CalendarView.AGENDA)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        views.forEachIndexed { i, view ->
            SegmentedButton(selected = view == selected, onClick = { onSelect(view) }, shape = SegmentedButtonDefaults.itemShape(i, views.size), colors = pillColors()) {
                Text(stringResource(labelOf(view)), maxLines = 1)
            }
        }
    }
}

private fun labelOf(view: CalendarView): Int = when (view) {
    CalendarView.DAY -> R.string.calendar_view_day
    CalendarView.WEEK -> R.string.calendar_view_week
    CalendarView.MONTH -> R.string.calendar_view_month
    CalendarView.AGENDA -> R.string.calendar_view_agenda
}

/** The colour category chips plus Birthdays; nothing selected shows everything. */
@Composable
private fun FilterRow(ui: CalendarUi, onCategory: (String) -> Unit, onBirthdays: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ui.categories.forEach { category ->
            FilterChip(
                selected = category.name.lowercase() in ui.filter.categories,
                onClick = { onCategory(category.name) },
                label = { Text(category.name) },
                leadingIcon = { CategoryDot(category.colorIndex) },
            )
        }
        FilterChip(selected = ui.filter.birthdays, onClick = onBirthdays, label = { Text(stringResource(R.string.calendar_filter_birthdays)) })
    }
}
