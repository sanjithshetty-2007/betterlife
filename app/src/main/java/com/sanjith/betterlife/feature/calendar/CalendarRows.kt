package com.sanjith.betterlife.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.ZoneId

/** Where a calendar row or block opens. */
public sealed interface AgendaTarget {
    public data class Event(val id: String) : AgendaTarget
    public data class Task(val id: String) : AgendaTarget
    public data class Reminder(val id: String) : AgendaTarget
}

internal fun targetOf(entry: AgendaEntry): AgendaTarget = when (entry.item.kind) {
    AgendaKind.TASK -> AgendaTarget.Task(entry.item.id)
    AgendaKind.REMINDER -> AgendaTarget.Reminder(entry.item.id)
    AgendaKind.EVENT, AgendaKind.MEETING, AgendaKind.BIRTHDAY -> AgendaTarget.Event(entry.item.id)
}

/** The category's colour, or none when the item has no (known) category. */
internal fun categoryColor(colorIndex: Int?): Color =
    colorIndex?.let { BetterLifeColors.CategoryPalette[it % BetterLifeColors.CategoryPalette.size] } ?: Color.Transparent

/**
 * One occurrence (agenda, day sheet, Month's today list): time, title, what it is, and a "Clash"
 * badge (icon AND word) when events overlap. Moved here from the Prompt 10 agenda unchanged.
 */
@Composable
internal fun CalendarRow(entry: AgendaEntry, colorIndex: Int?, dueText: DueText, zone: ZoneId, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, if (entry.conflict) BetterLifeColors.StatusWarn else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(Dimens.categoryStripe).fillMaxHeight().background(categoryColor(colorIndex)))
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
                    text = titleText(entry),
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

/** "Clash" with its icon, never colour alone; [withWord] false keeps only the icon (narrow week blocks). */
@Composable
internal fun ConflictBadge(withWord: Boolean = true) {
    val description = stringResource(R.string.agenda_conflict_description)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = BetterLifeColors.StatusWarn, modifier = Modifier.size(Dimens.smallIconSize))
        if (withWord) Text(stringResource(R.string.agenda_conflict), style = MaterialTheme.typography.labelMedium, color = BetterLifeColors.StatusWarn)
    }
}

internal fun titleText(entry: AgendaEntry): String =
    if (entry.item.kind == AgendaKind.BIRTHDAY) "🎂 ${entry.item.title}" else entry.item.title

@Composable
internal fun timeText(entry: AgendaEntry, dueText: DueText, zone: ZoneId): String {
    val start = entry.start ?: return stringResource(R.string.agenda_all_day)
    val from = dueText.time(start.atZone(zone).toLocalTime())
    val end = entry.end ?: return from
    return stringResource(R.string.agenda_time_range, from, dueText.time(end.atZone(zone).toLocalTime()))
}

@Composable
internal fun detailText(entry: AgendaEntry): String {
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
