package com.sanjith.betterlife.feature.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sanjith.betterlife.domain.calendar.CalendarView
import com.sanjith.betterlife.domain.calendar.TimeBlock
import com.sanjith.betterlife.domain.event.AgendaEntry
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.ui.nav.tabBarClearance
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val HOURS = 24
private const val MINUTES_PER_HOUR = 60f
private const val NOW_TICK_MS = 60_000L

/** The block colour: its category's, yellow for tasks (as in docs/design), muted for anything else. */
@Composable
internal fun blockColor(entry: AgendaEntry, colorIndex: Int?): Color = when {
    colorIndex != null -> categoryColor(colorIndex)
    entry.item.kind == AgendaKind.TASK -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 24 hours at [Dimens.hourHeight] each: hour lines and labels, the blocks side by side, the red now line. */
@Composable
internal fun HourGrid(ui: CalendarUi, scroll: ScrollState, viewModel: CalendarViewModel, onOpen: (AgendaTarget) -> Unit, modifier: Modifier) {
    val gridHeight = Dimens.hourHeight * HOURS
    val lineColor = MaterialTheme.colorScheme.outline
    Column(modifier.verticalScroll(scroll).padding(bottom = Spacing.md + tabBarClearance())) {
        Row(Modifier.fillMaxWidth().padding(end = Spacing.sm)) {
            HourLabels(gridHeight)
            BoxWithConstraints(
                Modifier.weight(1f).height(gridHeight).drawBehind {
                    val hour = size.height / HOURS
                    for (h in 0..HOURS) drawLine(lineColor, Offset(0f, h * hour), Offset(size.width, h * hour), Dimens.borderWidth.toPx())
                    val columns = ui.grid.size
                    for (c in 1 until columns) drawLine(lineColor, Offset(c * size.width / columns, 0f), Offset(c * size.width / columns, size.height), Dimens.borderWidth.toPx())
                },
            ) {
                val columnWidth = maxWidth / ui.grid.size.coerceAtLeast(1)
                ui.grid.forEachIndexed { index, day ->
                    day.blocks.forEach { block -> Block(block, ui, columnWidth, columnWidth * index, ui.view == CalendarView.DAY) { onOpen(targetOf(block.entry)) } }
                }
                val todayIndex = ui.grid.indexOfFirst { it.date == ui.today }
                if (todayIndex >= 0) NowLine(viewModel, columnWidth, columnWidth * todayIndex)
            }
        }
    }
}

@Composable
private fun HourLabels(gridHeight: Dp) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val format = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a") }
    Box(Modifier.width(Dimens.timeLabelWidth).height(gridHeight)) {
        // Midnight is left out: it sits on the top edge.
        for (h in 1 until HOURS) {
            Text(
                LocalTime.of(h, 0).format(format),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = Dimens.hourHeight * h - Spacing.sm).padding(start = Spacing.xs),
            )
        }
    }
}

/** One tinted block with its category stripe; [roomy] (day view) adds the detail line and the word "Clash". */
@Composable
private fun Block(block: TimeBlock, ui: CalendarUi, columnWidth: Dp, columnX: Dp, roomy: Boolean, onClick: () -> Unit) {
    val entry = block.entry
    val color = blockColor(entry, ui.colorIndexOf(entry.item.colorCategory))
    val width = columnWidth / block.columns
    val top = Dimens.hourHeight * (block.startMinute / MINUTES_PER_HOUR)
    val height = Dimens.hourHeight * ((block.endMinute - block.startMinute) / MINUTES_PER_HOUR)
    Row(
        modifier = Modifier
            .offset(x = columnX + width * block.column, y = top)
            .size(width, height)
            .padding(Dimens.gridBlockGap)
            // The smallest corner: a half-hour block is only 30 dp tall.
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = BetterLifeColors.EVENT_TINT_ALPHA))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.width(Dimens.categoryStripe).fillMaxHeight().background(color))
        Column(Modifier.padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                titleText(entry),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (roomy) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (roomy) Text(detailText(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.conflict) ConflictBadge(withWord = roomy)
        }
    }
}

private fun minuteOfDay(now: java.time.ZonedDateTime): Int = now.toLocalTime().toSecondOfDay() / SECONDS_PER_MINUTE

private const val SECONDS_PER_MINUTE = 60

/** The red line at the current minute in today's column, moved once a minute. */
@Composable
private fun NowLine(viewModel: CalendarViewModel, columnWidth: Dp, columnX: Dp) {
    var minute by remember { mutableIntStateOf(minuteOfDay(viewModel.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NOW_TICK_MS)
            minute = minuteOfDay(viewModel.now())
        }
    }
    val y = Dimens.hourHeight * (minute / MINUTES_PER_HOUR)
    Box(Modifier.offset(x = columnX, y = y - Dimens.nowLineWidth / 2).width(columnWidth).height(Dimens.nowLineWidth).background(BetterLifeColors.StatusBad))
    Box(Modifier.offset(x = columnX - Dimens.nowDot / 2, y = y - Dimens.nowDot / 2).size(Dimens.nowDot).background(BetterLifeColors.StatusBad, CircleShape))
}
