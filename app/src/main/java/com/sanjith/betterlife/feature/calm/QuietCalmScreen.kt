package com.sanjith.betterlife.feature.calm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.feature.events.TimeButton
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Quiet Hours, the in-call rule, Pause defaults and fatigue protection (Prompt 12, design
 * "quiet-hours"). Every change is saved at once; the defaults come from context.md section 13.
 */
@Composable
public fun QuietCalmScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val calm by container.calmStore.state.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val update: ((AppSettings) -> AppSettings) -> Unit = { change -> scope.launch { container.settingsRepository.update(change) } }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(stringResource(R.string.quiet_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            val s = settings ?: return@Column
            QuietHoursCard(s, update)
            GroupCard {
                PaddedSwitch(stringResource(R.string.quiet_alarm_bypass), s.alarmBypassesQuietHours) { v -> update { it.copy(alarmBypassesQuietHours = v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                InfoRow(stringResource(R.string.quiet_during_calls), stringResource(R.string.quiet_during_calls_detail))
            }
            SectionLabel(stringResource(R.string.quiet_pause_section))
            GroupCard {
                PaddedSwitch(stringResource(R.string.quiet_pause_exempt), s.pauseExemptsEventsAndAlarms) { v -> update { it.copy(pauseExemptsEventsAndAlarms = v) } }
                Detail(stringResource(R.string.quiet_pause_exempt_detail))
            }
            SectionLabel(stringResource(R.string.quiet_fatigue_section))
            GroupCard {
                Stepper(
                    title = stringResource(R.string.quiet_max_per_hour),
                    detail = stringResource(R.string.quiet_max_per_hour_detail),
                    value = s.fatigueMaxFullScreenPerHour.toString(),
                    canDecrease = s.fatigueMaxFullScreenPerHour > 1,
                    canIncrease = s.fatigueMaxFullScreenPerHour < AppSettings.MAX_POPUPS_PER_HOUR,
                    onChange = { delta -> update { it.copy(fatigueMaxFullScreenPerHour = it.fatigueMaxFullScreenPerHour + delta) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Stepper(
                    title = stringResource(R.string.quiet_group_within),
                    detail = stringResource(R.string.quiet_group_detail),
                    value = stringResource(R.string.quiet_minutes, s.fatigueGroupWindowMinutes),
                    canDecrease = s.fatigueGroupWindowMinutes > 0,
                    canIncrease = s.fatigueGroupWindowMinutes < AppSettings.MAX_GROUP_WINDOW_MINUTES,
                    onChange = { delta ->
                        update { it.copy(fatigueGroupWindowMinutes = (it.fatigueGroupWindowMinutes + delta * GROUP_STEP).coerceIn(0, AppSettings.MAX_GROUP_WINDOW_MINUTES)) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PaddedSwitch(stringResource(R.string.quiet_ignore_downgrade, s.fatigueIgnoredLimit), s.fatigueDowngradeEnabled) { v -> update { it.copy(fatigueDowngradeEnabled = v) } }
                Detail(stringResource(R.string.quiet_ignore_downgrade_detail))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PaddedSwitch(stringResource(R.string.quiet_alarm_exempt), s.alarmExemptFromFatigue) { v -> update { it.copy(alarmExemptFromFatigue = v) } }
            }
            calm?.calmUntil?.takeIf { it.isAfter(container.realClock.now()) }?.let { until ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.quiet_calm_active, timeText(until)), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                    OutlinedButton(
                        onClick = { scope.launch { container.calmStore.update { it.copy(calmUntil = null) } } },
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                    ) { Text(stringResource(R.string.quiet_calm_end_now)) }
                }
            }
        }
    }
}

private const val GROUP_STEP = 5

@Composable
private fun QuietHoursCard(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    GroupCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SwitchRow(stringResource(R.string.quiet_hours), s.quietHoursEnabled) { v -> update { it.copy(quietHoursEnabled = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                TimeColumn(stringResource(R.string.quiet_from)) {
                    TimeButton(localTimeText(s.quietHoursStart), s.quietHoursStart, onPick = { t -> t?.let { time -> update { it.copy(quietHoursStart = time) } } })
                }
                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimeColumn(stringResource(R.string.quiet_to)) {
                    TimeButton(localTimeText(s.quietHoursEnd), s.quietHoursEnd, onPick = { t -> t?.let { time -> update { it.copy(quietHoursEnd = time) } } })
                }
            }
            Text(stringResource(R.string.quiet_days_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    DayCircle(day, day in s.quietHoursDays, Modifier.weight(1f)) { on ->
                        update { it.copy(quietHoursDays = if (on) it.quietHoursDays + day else it.quietHoursDays - day) }
                    }
                }
            }
            Text(
                if (s.quietHoursStart == s.quietHoursEnd) stringResource(R.string.quiet_same_time)
                else stringResource(R.string.quiet_digest_note, localTimeText(s.quietHoursEnd)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeColumn(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        button()
    }
}

/** A weekday toggle: filled cherry when on, outlined when off; the letter is always shown. */
@Composable
private fun DayCircle(day: DayOfWeek, on: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The whole cell is the touch target; the circle inside is drawn a little smaller so seven fit.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget)
            .toggleable(on, role = Role.Checkbox, onValueChange = onChange)
            .semantics { contentDescription = name },
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(Dimens.dayCircle)
            .background(if (on) MaterialTheme.colorScheme.secondary else Color.Transparent, CircleShape)
            .border(Dimens.borderWidth, if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, CircleShape),
      ) {
        Text(
            day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            color = if (on) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
}

@Composable
private fun PaddedSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.padding(horizontal = Spacing.md)) { SwitchRow(text, checked, onChange) }
}

@Composable
private fun Stepper(title: String, detail: String, value: String, canDecrease: Boolean, canIncrease: Boolean, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val decrease = stringResource(R.string.quiet_decrease)
        IconButton(onClick = { onChange(-1) }, enabled = canDecrease, modifier = Modifier.semantics { contentDescription = decrease }) {
            Text("−", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = { onChange(1) }, enabled = canIncrease) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.quiet_increase), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InfoRow(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm))
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(modifier = Modifier.padding(vertical = Spacing.xs)) { content() } }
}
