package com.sanjith.betterlife.feature.calm

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.delivery.PauseOption
import com.sanjith.betterlife.domain.delivery.PauseRules
import com.sanjith.betterlife.domain.time.zonedNow
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.util.Date

/**
 * The "I'm busy" sheet (context.md 5.1): 30 min, 1 hour, 3 hours or until tonight, and whether to hold
 * calendar items and ALARM level too (off by default unless the owner changed the setting). Uses the
 * real clock: a pause holds real alarms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PauseSheet(tonight: LocalTime, holdsByDefault: Boolean, onDismiss: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val now = container.realClock.zonedNow()
    val options = PauseOption.entries.filter { PauseRules.endFor(it, now, tonight) != null }
    var option by remember { mutableStateOf(PauseOption.HOUR_1) }
    var holdsCalendar by remember { mutableStateOf(holdsByDefault) }
    var holdsAlarm by remember { mutableStateOf(holdsByDefault) }
    val end = PauseRules.endFor(option, now, tonight)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(Dimens.iconTile).background(BetterLifeColors.CherryTint, MaterialTheme.shapes.small),
                ) { Icon(painterResource(R.drawable.ic_pause_circle), null, tint = MaterialTheme.colorScheme.primary) }
                Column {
                    Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.pause_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.forEach { o ->
                    FilterChip(selected = o == option, onClick = { option = o }, label = { Text(optionLabel(o, tonight)) })
                }
            }
            SwitchRow(stringResource(R.string.pause_hold_calendar), holdsCalendar) { holdsCalendar = it }
            SwitchRow(stringResource(R.string.pause_hold_alarm), holdsAlarm) { holdsAlarm = it }
            Button(
                onClick = {
                    end?.let { until -> container.appScope.launch { container.reminderEngine.startPause(until, holdsCalendar, holdsAlarm) } }
                    onDismiss()
                },
                enabled = end != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.pause_start, end?.let { timeText(it) }.orEmpty())) }
        }
    }
}

@Composable
private fun optionLabel(option: PauseOption, tonight: LocalTime): String = when (option) {
    PauseOption.MINUTES_30 -> stringResource(R.string.pause_30_min)
    PauseOption.HOUR_1 -> stringResource(R.string.pause_1_hour)
    PauseOption.HOURS_3 -> stringResource(R.string.pause_3_hours)
    PauseOption.TONIGHT -> stringResource(R.string.pause_tonight, localTimeText(tonight))
}

/** "Paused until 6:00 PM" with Resume (context.md 5.1). Resume delivers anything held straight away. */
@Composable
public fun PauseBanner(until: Instant, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(painterResource(R.drawable.ic_pause_circle), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.iconSize))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pause_banner, timeText(until)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.pause_banner_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = { container.appScope.launch { container.reminderEngine.resume() } },
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.pause_resume)) }
        }
    }
}

@Composable
internal fun timeText(at: Instant): String = DateFormat.getTimeFormat(LocalContext.current).format(Date.from(at))

@Composable
internal fun localTimeText(time: LocalTime): String {
    // Only the time of day is formatted, so any fixed date works (no system clock read here).
    val zone = java.time.ZoneId.systemDefault()
    return DateFormat.getTimeFormat(LocalContext.current).format(Date.from(time.atDate(java.time.LocalDate.EPOCH).atZone(zone).toInstant()))
}
