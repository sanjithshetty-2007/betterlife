package com.sanjith.betterlife.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.calendar.CalendarView
import com.sanjith.betterlife.domain.calendar.GridDay
import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Week and day views: arrows, the day headers (week), the all-day strip, then the scrolling hour grid. */
@Composable
internal fun TimeGridView(ui: CalendarUi, viewModel: CalendarViewModel, onOpen: (AgendaTarget) -> Unit) {
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { Dimens.hourHeight.toPx() }
    // Open near the present on today's page, else at the start of a normal day.
    LaunchedEffect(ui.view, ui.range.from) {
        val hour = if (ui.today in ui.range) (viewModel.now().hour - 1).coerceAtLeast(0) else DEFAULT_START_HOUR
        scroll.scrollTo((hour * hourPx).toInt())
    }
    Column(Modifier.fillMaxSize()) {
        RangeSwitcher(ui, viewModel::previous, viewModel::next)
        if (ui.view == CalendarView.WEEK) DayHeaders(ui, viewModel::openDay)
        AllDayStrip(ui.grid, ui, onOpen)
        HourGrid(ui, scroll, viewModel, onOpen, Modifier.weight(1f))
    }
}

private const val DEFAULT_START_HOUR = 7

/** "2 – 8 Nov" or "Friday 6 November" between previous and next, like the month switcher. */
@Composable
private fun RangeSwitcher(ui: CalendarUi, onPrevious: () -> Unit, onNext: () -> Unit) {
    val week = ui.view == CalendarView.WEEK
    val label = if (week) {
        stringResource(R.string.agenda_time_range, ui.range.from.format(DateTimeFormatter.ofPattern("d MMM")), ui.range.to.format(DateTimeFormatter.ofPattern("d MMM")))
    } else {
        ui.anchor.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    }
    // Same inset as Month's switcher, which sits inside the tab content padding.
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(if (week) R.string.calendar_previous_week else R.string.calendar_previous_day))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(if (week) R.string.calendar_next_week else R.string.calendar_next_day))
        }
    }
}

/** Weekday and date over each column; today in a filled yellow circle; a tap opens that day's sheet. */
@Composable
private fun DayHeaders(ui: CalendarUi, onDay: (java.time.LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = Spacing.sm)) {
        Spacer(Modifier.width(Dimens.timeLabelWidth))
        ui.range.dates().forEach { date ->
            val today = date == ui.today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget).clickable(role = Role.Button) { onDay(date) },
            ) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(Dimens.dayCircle).background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/** All-day items (birthdays first) above the hours, in their day's column. */
@Composable
private fun AllDayStrip(days: List<GridDay>, ui: CalendarUi, onOpen: (AgendaTarget) -> Unit) {
    if (days.none { it.allDay.isNotEmpty() }) return
    Row(Modifier.fillMaxWidth().padding(end = Spacing.sm, bottom = Spacing.xs)) {
        Spacer(Modifier.width(Dimens.timeLabelWidth))
        days.forEach { day ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                day.allDay.forEach { entry -> AllDayChip(entry, ui.colorIndexOf(entry.item.colorCategory)) { onOpen(targetOf(entry)) } }
            }
        }
    }
}

@Composable
private fun AllDayChip(entry: AgendaEntry, colorIndex: Int?, onClick: () -> Unit) {
    val color = blockColor(entry, colorIndex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gridBlockGap)
            .heightIn(min = Dimens.minTouchTarget)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = BetterLifeColors.EVENT_TINT_ALPHA))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.width(Dimens.categoryStripe).heightIn(min = Dimens.minTouchTarget).background(color))
        Text(
            titleText(entry),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}
