package com.sanjith.betterlife.feature.protein

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.protein.ProteinSettings
import com.sanjith.betterlife.domain.protein.DayBar
import com.sanjith.betterlife.domain.protein.PeriodStats
import com.sanjith.betterlife.domain.protein.ProteinRules
import com.sanjith.betterlife.feature.habits.SectionCard
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** The first visit: target in grams and the reminder time; Start turns the daily reminder on. */
@Composable
internal fun GoalSetupCard(defaultTime: LocalTime, onStart: (Int, LocalTime) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf(defaultTime) }
    var picking by remember { mutableStateOf(false) }
    val target = ProteinRules.parseTarget(text)
    SectionCard {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.protein_setup_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.protein_setup_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GramsField(text, { text = it }, stringResource(R.string.protein_target_hint), onDone = { target?.let { onStart(it, time) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.protein_reminder_time), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(formatTime(time)) }
            }
            Button(
                onClick = { target?.let { onStart(it, time) } },
                enabled = target != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.protein_start)) }
        }
    }
    if (picking) ReminderTimeDialog(time, onPick = { time = it; picking = false }, onDismiss = { picking = false })
}

/** Today: grams of target, a progress bar, what is left, and the two log buttons. */
@Composable
internal fun TodayCard(state: ProteinUi, onLogToday: () -> Unit, onYesterday: () -> Unit) {
    val target = state.todayTarget ?: return
    val grams = state.todayGrams
    val percent = ((grams ?: 0) * PERCENT / target).coerceAtMost(PERCENT)
    SectionCard {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.protein_today), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.protein_today_amount, grams ?: 0, target),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val progressText = stringResource(R.string.protein_progress_description, percent)
            LinearProgressIndicator(
                progress = { percent / PERCENT.toFloat() },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressText },
            )
            Text(
                text = when {
                    grams == null -> stringResource(R.string.protein_not_logged)
                    grams >= target -> stringResource(R.string.protein_goal_reached)
                    else -> stringResource(R.string.protein_left, target - grams)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onLogToday, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(if (grams == null) R.string.protein_log_today else R.string.protein_edit_today))
            }
            OutlinedButton(onClick = onYesterday, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                Text(state.yesterdayGrams?.let { stringResource(R.string.protein_edit_yesterday, it) } ?: stringResource(R.string.protein_add_yesterday))
            }
        }
    }
}

/** A week or month: the average (empty days count as 0 g), goal days, then the period's overview. */
@Composable
internal fun PeriodCard(stats: PeriodStats, overview: @Composable () -> Unit) {
    SectionCard {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stats.averageGrams?.let { stringResource(R.string.protein_average, it) } ?: stringResource(R.string.protein_average_none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.protein_goal_days, stats.countedDays, stats.goalDays, stats.countedDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            overview()
        }
    }
}

/** Seven bars, one per day: filled up to the day's share of its target, full colour when the goal was met. */
@Composable
internal fun WeekBars(days: List<DayBar>, today: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        days.forEach { day ->
            val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val description = day.grams?.let { stringResource(R.string.protein_day_description, name, it) }
                ?: stringResource(R.string.protein_day_not_logged, name)
            Column(
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val share = day.grams?.let { grams -> day.targetGrams?.let { (grams.toFloat() / it).coerceIn(0f, 1f) } } ?: 0f
                Box(
                    modifier = Modifier.fillMaxWidth().height(Dimens.proteinBarHeight).clip(MaterialTheme.shapes.small).background(BetterLifeColors.Greyed),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (share > 0f) {
                        val color = MaterialTheme.colorScheme.primary
                        Box(Modifier.fillMaxWidth().fillMaxHeight(share).background(if (day.met) color else color.copy(alpha = BetterLifeColors.HEATMAP_SOFT_ALPHA)))
                    }
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The goal (Edit opens the grams dialog) and the daily reminder (time and on/off). */
@Composable
internal fun GoalAndReminder(settings: ProteinSettings, onEditTarget: () -> Unit, onReminder: (Boolean, LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.protein_daily_target), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onEditTarget, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.protein_target_value, settings.targetGrams ?: 0))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.protein_daily_reminder), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(formatTime(settings.reminderTime)) }
            Switch(checked = settings.reminderOn, onCheckedChange = { onReminder(it, settings.reminderTime) })
        }
    }
    if (picking) {
        ReminderTimeDialog(settings.reminderTime, onPick = { onReminder(settings.reminderOn, it); picking = false }, onDismiss = { picking = false })
    }
}

/** The phone's own 12 or 24 hour format (the task screens' formatter). */
@Composable
internal fun formatTime(time: LocalTime): String {
    val context = LocalContext.current
    val text = remember(context) { DueText(context) { java.time.ZoneOffset.UTC } }
    return text.time(time)
}

private const val PERCENT = 100
