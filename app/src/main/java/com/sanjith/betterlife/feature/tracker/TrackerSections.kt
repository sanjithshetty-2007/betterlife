package com.sanjith.betterlife.feature.tracker

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
import com.sanjith.betterlife.data.tracker.TrackerSettings
import com.sanjith.betterlife.domain.tracker.DayBar
import com.sanjith.betterlife.domain.tracker.PeriodStats
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.feature.habits.SectionCard
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** The first visit: the target and the reminder time; Start turns the daily reminder on. */
@Composable
internal fun GoalSetupCard(kind: TrackerKind, defaultTime: LocalTime, onStart: (Int, LocalTime) -> Unit) {
    val texts = textsFor(kind)
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf(defaultTime) }
    var picking by remember { mutableStateOf(false) }
    val target = parseTarget(kind, first, second)
    SectionCard {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(texts.setupTitle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(texts.setupText), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountFields(kind, first, { first = it }, second, { second = it }, stringResource(R.string.protein_target_hint), onDone = { target?.let { onStart(it, time) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tracker_reminder_time), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(formatTime(time)) }
            }
            Button(
                onClick = { target?.let { onStart(it, time) } },
                enabled = target != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.tracker_start)) }
        }
    }
    if (picking) ReminderTimeDialog(time, onPick = { time = it; picking = false }, onDismiss = { picking = false })
}

/** The latest day (today, or last night): amount of target, a progress bar, what is left, and the two log buttons. */
@Composable
internal fun LatestCard(state: TrackerUi, onLog: () -> Unit, onEarlier: () -> Unit) {
    val kind = state.kind
    val texts = textsFor(kind)
    val target = state.latestTarget ?: return
    val amount = state.latestAmount
    val percent = ((amount ?: 0) * PERCENT / target).coerceAtMost(PERCENT)
    SectionCard {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(texts.latestHeading), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.tracker_amount_of, amountText(kind, amount ?: 0), amountText(kind, target)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val progressText = stringResource(texts.progress, percent)
            LinearProgressIndicator(
                progress = { percent / PERCENT.toFloat() },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressText },
            )
            Text(
                text = when {
                    amount == null -> stringResource(R.string.tracker_not_logged)
                    amount >= target -> stringResource(texts.reached)
                    else -> stringResource(texts.left, amountText(kind, target - amount))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onLog, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(if (amount == null) texts.logLatest else texts.editLatest))
            }
            OutlinedButton(onClick = onEarlier, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                Text(state.earlierAmount?.let { stringResource(texts.editEarlier, amountText(kind, it)) } ?: stringResource(texts.addEarlier))
            }
        }
    }
}

/** A week or month: the average (empty days count as 0), goal days, then the period's overview. */
@Composable
internal fun PeriodCard(kind: TrackerKind, stats: PeriodStats, overview: @Composable () -> Unit) {
    val texts = textsFor(kind)
    SectionCard {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stats.average?.let { stringResource(texts.average, amountText(kind, it)) } ?: stringResource(R.string.tracker_average_none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(texts.goalDays, stats.countedDays, stats.goalDays, stats.countedDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            overview()
        }
    }
}

/** Seven bars, one per day: filled up to the day's share of its target, full colour when the goal was met. */
@Composable
internal fun WeekBars(kind: TrackerKind, days: List<DayBar>, latest: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        days.forEach { day ->
            val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val description = day.amount?.let { stringResource(R.string.tracker_day_description, name, amountText(kind, it)) }
                ?: stringResource(R.string.tracker_day_not_logged, name)
            Column(
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val share = day.amount?.let { amount -> day.target?.let { (amount.toFloat() / it).coerceIn(0f, 1f) } } ?: 0f
                Box(
                    modifier = Modifier.fillMaxWidth().height(Dimens.trackerBarHeight).clip(MaterialTheme.shapes.small).background(BetterLifeColors.Greyed),
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
                    color = if (day.date == latest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The goal (tapping it opens the amount dialog) and the daily reminder (time and on/off). */
@Composable
internal fun GoalAndReminder(kind: TrackerKind, settings: TrackerSettings, onEditTarget: () -> Unit, onReminder: (Boolean, LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(textsFor(kind).targetLabel), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onEditTarget, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                Text(amountText(kind, settings.target ?: 0))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.tracker_daily_reminder), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
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
