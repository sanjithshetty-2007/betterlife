package com.sanjith.betterlife.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.event.AgendaDay
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.nav.tabContentPadding
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The agenda list (Prompt 10, improved in 18): 60 days from today, each day's header sticks while its rows scroll. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AgendaView(ui: CalendarUi, dueText: DueText, zone: ZoneId, onOpen: (AgendaTarget) -> Unit) {
    if (ui.days.isEmpty() && !ui.loading) {
        Box(Modifier.fillMaxSize().padding(tabContentPadding()).padding(Spacing.sm), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.agenda_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(contentPadding = tabContentPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ui.days.forEach { day ->
            stickyHeader(key = day.date.toString()) { DayHeader(day, ui.today, dueText) }
            items(day.entries, key = { it.key }) { entry ->
                CalendarRow(entry, ui.colorIndexOf(entry.item.colorCategory), dueText, zone) { onOpen(targetOf(entry)) }
            }
        }
    }
}

/** "Today · 6 Nov 2026" (yellow for today) with the day's count; opaque so rows slide under it. */
@Composable
private fun DayHeader(day: AgendaDay, today: LocalDate, dueText: DueText) {
    val relative = dueText.day(day.date, today)
    val full = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val color = if (day.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = Spacing.sm),
    ) {
        Text(
            text = if (relative == full) full else "$relative · $full",
            style = MaterialTheme.typography.titleSmall,
            color = color,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(day.entries.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
