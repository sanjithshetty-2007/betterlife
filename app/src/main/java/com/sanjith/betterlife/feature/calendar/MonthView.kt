package com.sanjith.betterlife.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.rememberReducedMotion
import com.sanjith.betterlife.domain.calendar.CalendarRanges
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.nav.tabContentPadding
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Motion
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Month view: arrows and swipe change the month; a day opens its sheet; today's list underneath. */
@Composable
internal fun MonthView(ui: CalendarUi, dueText: DueText, viewModel: CalendarViewModel, onOpen: (AgendaTarget) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tabContentPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MonthSwitcher(ui, viewModel::previous, viewModel::next)
        WeekdayLetters(ui)
        SwipeableMonth(ui, viewModel::previous, viewModel::next, viewModel::openDay)
        if (ui.today in ui.range && ui.monthGrid?.inMonth(ui.today) == true) {
            Text(
                text = stringResource(R.string.calendar_today_list, ui.today.format(DateTimeFormatter.ofPattern("EEE d MMM"))),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
            )
            val entries = ui.day(ui.today)?.entries.orEmpty()
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            entries.forEach { entry -> CalendarRow(entry, ui.colorIndexOf(entry.item.colorCategory), dueText, viewModel.zone) { onOpen(targetOf(entry)) } }
        }
    }
}

/** Previous / next month around the month's name, written like the Habits month row. */
@Composable
private fun MonthSwitcher(ui: CalendarUi, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.calendar_previous_month)) }
        Text(
            text = ui.anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.calendar_next_month)) }
    }
}

@Composable
private fun WeekdayLetters(ui: CalendarUi) {
    Row(Modifier.fillMaxWidth()) {
        CalendarRanges.weekdays(ui.weekStart).forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The grid slides to the next or previous month; a horizontal swipe longer than [SWIPE_DISTANCE] changes it. */
@Composable
private fun SwipeableMonth(ui: CalendarUi, onPrevious: () -> Unit, onNext: () -> Unit, onDay: (LocalDate) -> Unit) {
    val reduced = rememberReducedMotion()
    val threshold = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    AnimatedContent(
        targetState = ui,
        contentKey = { it.monthGrid?.month },
        transitionSpec = {
            val from = initialState.monthGrid?.month
            val to = targetState.monthGrid?.month
            if (reduced || from == null || to == null) snapIn() else {
                val sign = if (to > from) 1 else -1
                slideInHorizontally(tween(Motion.PUSH_MS)) { it * sign } togetherWith slideOutHorizontally(tween(Motion.PUSH_MS)) { -it * sign }
            }
        },
        modifier = Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (total > threshold) onPrevious() else if (total < -threshold) onNext()
                    total = 0f
                },
                onDragCancel = { total = 0f },
            ) { _, dx -> total += dx }
        },
        label = "month",
    ) { shown -> MonthGridCells(shown, onDay) }
}

private fun snapIn() = androidx.compose.animation.fadeIn(snap()) togetherWith androidx.compose.animation.fadeOut(snap())

private val SWIPE_DISTANCE = Spacing.xl * 2

@Composable
private fun MonthGridCells(ui: CalendarUi, onDay: (LocalDate) -> Unit) {
    val grid = ui.monthGrid ?: return
    Column(Modifier.fillMaxWidth()) {
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date -> DayCell(date, ui, grid.inMonth(date), Modifier.weight(1f)) { onDay(date) } }
            }
        }
    }
}

/** A number (today in a filled yellow circle), up to three category dots and a cake on birthdays. */
@Composable
private fun DayCell(date: LocalDate, ui: CalendarUi, inMonth: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val marks = ui.marks[date]
    val count = ui.day(date)?.entries?.size ?: 0
    val isToday = date == ui.today
    val label = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    val description = buildString {
        append(if (count == 0) stringResource(R.string.calendar_cell_empty, label) else pluralStringResource(R.plurals.calendar_cell_items, count, label, count))
        if (marks?.birthday == true) append(", ").append(stringResource(R.string.agenda_kind_birthday))
        if (isToday) append(", ").append(stringResource(R.string.calendar_cell_today))
    }
    val selected = date == ui.sheetDay
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        modifier = modifier
            .height(Dimens.monthCellHeight)
            .then(if (selected) Modifier.border(BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.small) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(Dimens.dayCircle).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onBackground
                    else -> BetterLifeColors.OutsideMonthDay
                },
            )
        }
        // A fixed height, so a cake does not push this day's number above its neighbours'.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(Dimens.smallIconSize),
        ) {
            marks?.colors?.forEach { index ->
                Box(Modifier.size(Dimens.calendarDot).background(index?.let(::categoryColor) ?: MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            }
            if (marks?.birthday == true) Text("🎂", style = MaterialTheme.typography.labelSmall)
        }
    }
}
