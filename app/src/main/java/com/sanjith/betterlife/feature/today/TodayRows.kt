package com.sanjith.betterlife.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.today.TodayItem
import com.sanjith.betterlife.domain.today.TodayKind
import com.sanjith.betterlife.domain.today.TodayRow
import com.sanjith.betterlife.domain.today.TodayTier
import com.sanjith.betterlife.feature.calm.localTimeText
import com.sanjith.betterlife.feature.calm.timeText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/** One Today row: a single item, or a collapsed same-time group that opens on tap. */
@Composable
internal fun TodayRowCard(row: TodayRow, onOpen: (TodayItem) -> Unit, onTick: (TodayItem) -> Unit) {
    when (row) {
        is TodayRow.Single -> ItemCard(row.item, onOpen, onTick)
        is TodayRow.Group -> GroupCard(row, onOpen, onTick)
    }
}

@Composable
private fun ItemCard(item: TodayItem, onOpen: (TodayItem) -> Unit, onTick: (TodayItem) -> Unit) {
    RowCard(urgent = item.tier == TodayTier.URGENT_REMINDER, onClick = { onOpen(item) }) {
        Leading(item, onTick)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Detail(item)
        }
    }
}

@Composable
private fun GroupCard(group: TodayRow.Group, onOpen: (TodayItem) -> Unit, onTick: (TodayItem) -> Unit) {
    var open by rememberSaveable(group.minute.toString()) { mutableStateOf(false) }
    RowCard(urgent = false, onClick = { open = !open }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.today_group, timeText(group.minute), group.items.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!open) {
                Text(group.items.joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                group.items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).clickable { onOpen(item) },
                    ) {
                        Leading(item, onTick)
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(urgent: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, if (urgent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

/** A round tick for tasks and habits; an icon tile for events and remind-me items. */
@Composable
private fun Leading(item: TodayItem, onTick: (TodayItem) -> Unit) {
    if (item.tier.tickable || item.kind == TodayKind.TASK) {
        val label = stringResource(if (item.kind == TodayKind.HABIT) R.string.today_tick_habit else R.string.today_tick_task, item.title)
        IconButton(onClick = { onTick(item) }, modifier = Modifier.semantics { contentDescription = label }) {
            Box(Modifier.size(Dimens.iconSize).border(Dimens.borderWidth * 2, BetterLifeColors.BoxOpenBorder, CircleShape))
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(Dimens.iconTile).background(BetterLifeColors.CherryTint, MaterialTheme.shapes.small),
        ) {
            Icon(
                painterResource(if (item.kind == TodayKind.EVENT) R.drawable.ic_calendar else R.drawable.ic_alarm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Detail(item: TodayItem) {
    val parts = buildList {
        when (item.tier) {
            TodayTier.URGENT_REMINDER -> add(stringResource(R.string.today_pop_up))
            TodayTier.OVERDUE_HIGH -> add(stringResource(R.string.today_overdue))
            else -> Unit
        }
        if (item.priority == Priority.HIGH && item.tier != TodayTier.OVERDUE_HIGH) add(stringResource(R.string.priority_high))
        when (item.kind) {
            TodayKind.EVENT -> item.time?.let { add(stringResource(R.string.today_event_starts, timeText(it))) }
            TodayKind.HABIT -> add(item.habitTime?.let { stringResource(R.string.today_habit_at, localTimeText(it)) } ?: stringResource(R.string.today_kind_habit))
            TodayKind.REMINDER -> add(stringResource(R.string.today_kind_reminder))
            TodayKind.TASK -> item.time?.let { add(stringResource(R.string.today_due_at, timeText(it))) }
        }
    }
    if (parts.isEmpty()) return
    val warn = item.tier == TodayTier.OVERDUE_HIGH
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (warn) BetterLifeColors.StatusBad else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
