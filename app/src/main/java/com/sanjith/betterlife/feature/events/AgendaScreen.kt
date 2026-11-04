package com.sanjith.betterlife.feature.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Where an agenda row opens. */
public sealed interface AgendaTarget {
    public data class Event(val id: String) : AgendaTarget
    public data class Task(val id: String) : AgendaTarget
    public data class Reminder(val id: String) : AgendaTarget
}

/** The agenda list (Prompt 10): the next 60 days by day. Calendar grids come in Prompt 18. */
@Composable
public fun AgendaScreen(
    onBack: () -> Unit,
    onOpen: (AgendaTarget) -> Unit,
    onNewEvent: (EventType) -> Unit,
    onNewReminder: () -> Unit,
    onOpenDefaults: () -> Unit,
    viewModel: AgendaViewModel = viewModel(factory = AgendaViewModel.Factory),
) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val today = viewModel.today()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onBack, onNewEvent, onNewReminder, onOpenDefaults)
            if (ui.days.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.agenda_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ui.days.forEach { day ->
                    item(key = day.date.toString()) { DayHeader(day.date, today, dueText) }
                    items(day.entries, key = { it.key }) { entry ->
                        AgendaRow(entry, ui.colorIndexOf(entry.item.colorCategory), dueText, onClick = { onOpen(targetOf(entry)) })
                    }
                }
            }
        }
    }
}

private fun targetOf(entry: AgendaEntry): AgendaTarget = when (entry.item.kind) {
    AgendaKind.TASK -> AgendaTarget.Task(entry.item.id)
    AgendaKind.REMINDER -> AgendaTarget.Reminder(entry.item.id)
    AgendaKind.EVENT, AgendaKind.MEETING, AgendaKind.BIRTHDAY -> AgendaTarget.Event(entry.item.id)
}

@Composable
private fun Header(onBack: () -> Unit, onNewEvent: (EventType) -> Unit, onNewReminder: () -> Unit, onOpenDefaults: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = stringResource(R.string.agenda_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenDefaults) { Text(stringResource(R.string.agenda_defaults)) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.agenda_new_event), tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.agenda_new_event)) }, onClick = { menu = false; onNewEvent(EventType.EVENT) })
                DropdownMenuItem(text = { Text(stringResource(R.string.agenda_new_birthday)) }, onClick = { menu = false; onNewEvent(EventType.BIRTHDAY) })
                DropdownMenuItem(text = { Text(stringResource(R.string.agenda_new_reminder)) }, onClick = { menu = false; onNewReminder() })
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, dueText: DueText) {
    val relative = dueText.day(date, today)
    val full = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    Text(
        text = if (relative == full) full else "$relative · $full",
        style = MaterialTheme.typography.titleSmall,
        color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/** One occurrence: time, title, what it is, and a "Clash" badge (icon AND word) when events overlap. */
@Composable
private fun AgendaRow(entry: AgendaEntry, colorIndex: Int?, dueText: DueText, onClick: () -> Unit) {
    val zone = LocalContext.current.let { (it.applicationContext as BetterLifeApp).container.clock.zone }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, if (entry.conflict) BetterLifeColors.StatusWarn else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            val palette = BetterLifeColors.CategoryPalette
            val stripe = colorIndex?.let { palette[it % palette.size] } ?: Color.Transparent
            Box(Modifier.width(Dimens.categoryStripe).fillMaxHeight().background(stripe))
            Column(modifier = Modifier.weight(1f).padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = timeText(entry, dueText, zone),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.conflict) ConflictBadge()
                }
                Text(
                    text = if (entry.item.kind == AgendaKind.BIRTHDAY) "🎂 ${entry.item.title}" else entry.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detailText(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConflictBadge() {
    val description = stringResource(R.string.agenda_conflict_description)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = BetterLifeColors.StatusWarn, modifier = Modifier.size(Dimens.smallIconSize))
        Text(stringResource(R.string.agenda_conflict), style = MaterialTheme.typography.labelMedium, color = BetterLifeColors.StatusWarn)
    }
}

@Composable
private fun timeText(entry: AgendaEntry, dueText: DueText, zone: java.time.ZoneId): String {
    val start = entry.start ?: return stringResource(R.string.agenda_all_day)
    val from = dueText.time(start.atZone(zone).toLocalTime())
    val end = entry.end ?: return from
    return stringResource(R.string.agenda_time_range, from, dueText.time(end.atZone(zone).toLocalTime()))
}

@Composable
private fun detailText(entry: AgendaEntry): String {
    val kind = stringResource(
        when (entry.item.kind) {
            AgendaKind.EVENT -> R.string.agenda_kind_event
            AgendaKind.MEETING -> R.string.agenda_kind_meeting
            AgendaKind.BIRTHDAY -> R.string.agenda_kind_birthday
            AgendaKind.TASK -> R.string.agenda_kind_task
            AgendaKind.REMINDER -> R.string.agenda_kind_reminder
        },
    )
    val extra = listOfNotNull(
        entry.age?.let { stringResource(R.string.agenda_turns, it) },
        entry.item.location,
        entry.item.colorCategory,
    )
    return (listOf(kind) + extra).joinToString(" · ")
}
