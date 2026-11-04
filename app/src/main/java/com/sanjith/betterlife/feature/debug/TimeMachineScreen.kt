package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.TimeMachineClock
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.habitDay
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TICK_MILLIS = 1_000L
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm:ss")
private val JUMP_TIME: LocalTime = LocalTime.of(1, 0)

/** Debug-only: shift the app's clock to test day rollover, streaks and milestones by hand. */
@Composable
public fun TimeMachineScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val machine = container.timeMachine ?: return
    val offset by machine.offset.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    // Re-read both clocks every second so the screen shows live times.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            tick++
        }
    }
    val zone: ZoneId = machine.zone
    val appNow: Instant = remember(tick, offset) { machine.now() }
    val realNow: Instant = remember(tick, offset) { appNow.minus(offset) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Header(onBack)
            Text(
                text = stringResource(R.string.time_machine_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(
                    R.string.time_machine_status,
                    TIME_FORMAT.format(realNow.atZone(zone)),
                    TIME_FORMAT.format(appNow.atZone(zone)),
                    habitDay(appNow, zone, settings.rolloverHour).toString(),
                    settings.rolloverHour,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Controls(machine, zone)
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.setup_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.time_machine_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Controls(machine: TimeMachineClock, zone: ZoneId) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShiftButton(R.string.time_machine_minus_day, Modifier.weight(1f)) { machine.shift(Duration.ofDays(-1)) }
        ShiftButton(R.string.time_machine_plus_day, Modifier.weight(1f)) { machine.shift(Duration.ofDays(1)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShiftButton(R.string.time_machine_minus_hour, Modifier.weight(1f)) { machine.shift(Duration.ofHours(-1)) }
        ShiftButton(R.string.time_machine_plus_hour, Modifier.weight(1f)) { machine.shift(Duration.ofHours(1)) }
    }
    Button(
        onClick = {
            // 01:00 after the app's current day: lands before the 03:00 rollover, so it is still "today".
            val tomorrow = machine.now().atZone(zone).toLocalDate().plusDays(1)
            machine.setTo(tomorrow.atTime(JUMP_TIME).atZone(zone).toInstant())
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget),
    ) { Text(stringResource(R.string.time_machine_jump_0100)) }
    Button(
        onClick = machine::reset,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget),
    ) { Text(stringResource(R.string.time_machine_reset)) }
}

@Composable
private fun ShiftButton(label: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = Dimens.minTouchTarget)) {
        Text(stringResource(label))
    }
}
