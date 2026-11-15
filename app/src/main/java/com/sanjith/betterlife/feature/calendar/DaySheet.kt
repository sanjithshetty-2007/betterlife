package com.sanjith.betterlife.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A tapped day: its events AND tasks together (context.md 5.2), with Add event and Add task for that day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySheet(
    day: LocalDate,
    entries: List<AgendaEntry>,
    ui: CalendarUi,
    dueText: DueText,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onOpen: (AgendaTarget) -> Unit,
    onAddEvent: () -> Unit,
    onAddTask: () -> Unit,
) {
    // Fully open: on a busy day the list scrolls and Add event / Add task stay in view at the bottom.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        day.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (entries.isNotEmpty()) Text(countsText(entries), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), modifier = Modifier.size(Dimens.iconSize))
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.calendar_day_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                entries.forEach { entry -> CalendarRow(entry, ui.colorIndexOf(entry.item.colorCategory), dueText, zone) { onOpen(targetOf(entry)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.md)) {
                Button(onClick = onAddEvent, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                    Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(Dimens.iconSize))
                    Text(stringResource(R.string.calendar_add_event), modifier = Modifier.padding(start = Spacing.sm))
                }
                OutlinedButton(onClick = onAddTask, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(Dimens.iconSize))
                    Text(stringResource(R.string.calendar_add_task), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}

/** "2 events · 1 task": events and birthdays count as events; tasks and "remind me" items as tasks. */
@Composable
private fun countsText(entries: List<AgendaEntry>): String {
    val tasks = entries.count { it.item.kind == AgendaKind.TASK || it.item.kind == AgendaKind.REMINDER }
    val events = entries.size - tasks
    return listOfNotNull(
        events.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.calendar_sheet_events, it, it) },
        tasks.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.calendar_sheet_tasks, it, it) },
    ).joinToString(" · ")
}
