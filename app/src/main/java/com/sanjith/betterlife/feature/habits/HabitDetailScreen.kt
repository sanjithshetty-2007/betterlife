package com.sanjith.betterlife.feature.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.feedback.rememberDoneHaptics
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.habit.DayState
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/** One habit (Prompt 15): consistency, streak and best, the last 12 months as a heatmap, and this month. */
@Composable
public fun HabitDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: HabitDetailViewModel = viewModel(factory = HabitDetailViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val haptics = rememberDoneHaptics()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val state = ui
            Header(state?.habit?.name.orEmpty(), onBack, onEdit = { state?.let { onEdit(it.habit.id) } })
            if (state == null) return@Column
            val color = habitColor(state.habit.colorHex)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    HabitAvatar(state.habit.avatar, color)
                    Text(
                        text = stringResource(categoryLabel(state.habit.category)) +
                            if (state.habit.essential) " · " + stringResource(R.string.habit_essential) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val consistency = when (val c = state.consistency) {
                        Consistency.New -> stringResource(R.string.habit_consistency_new)
                        is Consistency.Percent -> stringResource(R.string.habit_percent, c.percent)
                    }
                    StatTile(consistency, stringResource(R.string.habit_consistency), Modifier.weight(1f))
                    StatTile(state.streak.current.toString(), stringResource(R.string.habit_stat_streak), Modifier.weight(1f))
                    StatTile(state.streak.best.toString(), stringResource(R.string.habit_stat_best), Modifier.weight(1f))
                }
                SectionLabel(stringResource(R.string.habit_last_12_months))
                HeatmapCard(state.heatmap, color)
                SectionLabel(stringResource(R.string.habit_this_month))
                SectionCard {
                    HabitMonthGrid(state.boxes, color, onTap = { box ->
                        if (box.state == BoxState.OPEN) haptics.done() else haptics.undone()
                        viewModel.toggle(box)
                    })
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.habit_edit), tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(Spacing.sm)) { content() } }
}

/**
 * The last 12 months on ONE Canvas: a column per week, a row per weekday (Monday on top). Done days
 * in the habit colour, kept and paused days lighter, missed days faint. Month names underneath.
 */
@Composable
private fun HeatmapCard(heatmap: Heatmap, color: Color) {
    val summary = stringResource(R.string.habit_heatmap_description, heatmap.doneDays)
    val soft = color.copy(alpha = BetterLifeColors.HEATMAP_SOFT_ALPHA)
    val missed = BetterLifeColors.Greyed
    val months = heatmap.weeks.indices.mapNotNull { w ->
        val monday = heatmap.firstMonday.plusWeeks(w.toLong())
        val first = (0..6).map { monday.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == 1 }
        first?.let { w to it.format(DateTimeFormatter.ofPattern("MMM")) }
    }
    SectionCard {
        val gap: Dp = Dimens.heatmapGap
        Canvas(
            modifier = Modifier.fillMaxWidth().aspectRatio(heatmap.weeks.size / DAYS.toFloat()).semantics { contentDescription = summary },
        ) {
            val step = size.width / heatmap.weeks.size
            val side = step - gap.toPx()
            val radius = CornerRadius(side / CORNER_DIVISOR)
            heatmap.weeks.forEachIndexed { w, week ->
                week.forEachIndexed { d, state ->
                    val fill = when (state) {
                        DayState.DONE -> color
                        DayState.KEPT, DayState.PAUSED -> soft
                        DayState.MISSED -> missed
                        DayState.BEFORE_START, DayState.FUTURE -> null
                    } ?: return@forEachIndexed
                    drawRoundRect(fill, topLeft = Offset(w * step, d * (side + gap.toPx())), size = Size(side, side), cornerRadius = radius)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            // Month labels at their week's share of the width, each starting at its column.
            var used = 0
            months.forEach { (week, name) ->
                if (week > used) androidx.compose.foundation.layout.Spacer(Modifier.weight((week - used).toFloat()))
                val span = (months.firstOrNull { it.first > week }?.first ?: heatmap.weeks.size) - week
                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(span.toFloat()))
                used = week + span
            }
        }
    }
}

private const val DAYS = 7
private const val CORNER_DIVISOR = 4f
