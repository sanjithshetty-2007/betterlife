package com.sanjith.betterlife.reminders.popup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.text.format.DateFormat
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.OffsetDescription
import com.sanjith.betterlife.domain.popup.OffsetUnit
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.popup.SnoozeChoice
import com.sanjith.betterlife.domain.popup.SnoozeOption
import com.sanjith.betterlife.domain.popup.describeOffset
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.Instant
import java.time.LocalTime

private const val SNOOZE_COLUMNS = 2

/** The six snooze choices; ones that make no sense right now are disabled, never hidden. */
@Composable
internal fun SnoozePanel(
    options: List<SnoozeOption>,
    formatTime: (Instant) -> String,
    onPick: (SnoozeOption) -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.chunked(SNOOZE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { option ->
                    OutlinedButton(
                        onClick = { onPick(option) },
                        enabled = option.allowed,
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
                    ) { Text(snoozeLabel(option, formatTime)) }
                }
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.popup_back))
        }
    }
}

@Composable
private fun snoozeLabel(option: SnoozeOption, formatTime: (Instant) -> String): String = when (option.choice) {
    SnoozeChoice.MINUTES_10 -> stringResource(R.string.snooze_10_min)
    SnoozeChoice.MINUTES_30 -> stringResource(R.string.snooze_30_min)
    SnoozeChoice.HOUR_1 -> stringResource(R.string.snooze_1_hour)
    SnoozeChoice.TONIGHT -> option.at?.let { stringResource(R.string.snooze_tonight_at, formatTime(it)) } ?: stringResource(R.string.snooze_tonight)
    SnoozeChoice.TOMORROW -> option.at?.let { stringResource(R.string.snooze_tomorrow_at, formatTime(it)) } ?: stringResource(R.string.snooze_tomorrow)
    SnoozeChoice.PICK_TIME -> stringResource(R.string.snooze_pick_time)
}

/** Pick a time of day. [onConfirm] returns false when that time is not allowed (after the event ends). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickTimePanel(onConfirm: (LocalTime) -> Boolean, onBack: () -> Unit) {
    val state = rememberTimePickerState(is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    var rejected by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TimeInput(state = state)
        if (rejected) {
            Text(stringResource(R.string.snooze_after_event_end), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.popup_back))
            }
            Button(
                onClick = { rejected = !onConfirm(LocalTime.of(state.hour, state.minute)) },
                modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.snooze_confirm)) }
        }
    }
}

/** "Why am I seeing this?": the item, its time, which rule fired, the level, and a way to edit. */
@Composable
internal fun WhyPanel(card: PopupCard, ui: PopupUi, onEdit: () -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        WhyRow(R.string.why_item, if (ui.hideTitles) stringResource(R.string.popup_hidden_title) else card.title)
        card.itemTime?.let { WhyRow(R.string.why_due, if (card.itemAllDay) ui.formatDate(it) else "${ui.formatDate(it)}, ${ui.formatTime(it)}") }
        WhyRow(R.string.why_rule, ruleText(card.offsetMinutes))
        WhyRow(R.string.why_level, stringResource(importanceLabel(card.importance)))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.popup_back))
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.why_edit))
            }
        }
    }
}

@Composable
private fun WhyRow(label: Int, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ruleText(offsetMinutes: Int): String = when (val d = describeOffset(offsetMinutes)) {
    OffsetDescription.AtTime -> stringResource(R.string.why_rule_at_time)
    is OffsetDescription.Before -> {
        val plural = when (d.unit) {
            OffsetUnit.MINUTES -> R.plurals.why_minutes_before
            OffsetUnit.HOURS -> R.plurals.why_hours_before
            OffsetUnit.DAYS -> R.plurals.why_days_before
            OffsetUnit.WEEKS -> R.plurals.why_weeks_before
        }
        pluralStringResource(plural, d.amount, d.amount)
    }
}

/** Level chip with a word, never color alone. */
@Composable
internal fun ImportanceBadge(importance: Importance, modifier: Modifier = Modifier) {
    val color = when (importance) {
        Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        Importance.FULL_SCREEN -> MaterialTheme.colorScheme.primary
        Importance.ALARM -> BetterLifeColors.StatusBad
    }
    Row(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = color.copy(alpha = BADGE_FILL_ALPHA),
            border = BorderStroke(Dimens.borderWidth, color),
        ) {
            Text(
                text = stringResource(importanceLabel(importance)),
                color = color,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

private const val BADGE_FILL_ALPHA = 0.16f

private fun importanceLabel(importance: Importance): Int = when (importance) {
    Importance.NORMAL -> R.string.level_normal
    Importance.FULL_SCREEN -> R.string.level_full_screen
    Importance.ALARM -> R.string.level_alarm
}
