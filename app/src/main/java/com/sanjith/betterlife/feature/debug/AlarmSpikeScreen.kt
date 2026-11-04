package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.alarm.AlarmKind
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PENDING_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val ALARM_MINUTES = listOf(1 to R.string.alarm_spike_in_1, 5 to R.string.alarm_spike_in_5, 15 to R.string.alarm_spike_in_15)

/** Debug-only screen for the Prompt 4 gate: schedule a test alarm of either kind and read the event log. */
@Composable
public fun AlarmSpikeScreen(
    onBack: () -> Unit,
    viewModel: AlarmSpikeViewModel = viewModel(factory = AlarmSpikeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { AlarmControls(state, viewModel, onBack) }
            // Newest first, so the latest outcome is visible without scrolling.
            items(state.log.asReversed()) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AlarmControls(state: AlarmSpikeUiState, viewModel: AlarmSpikeViewModel, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.setup_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.alarm_spike_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.alarm_spike_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val useAlarmClock = state.kind == AlarmKind.ALARM_CLOCK
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .toggleable(value = useAlarmClock, role = Role.Switch, onValueChange = viewModel::setUseAlarmClock),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.alarm_spike_use_alarm_clock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.sm),
            )
            Switch(checked = useAlarmClock, onCheckedChange = null)
        }
        ALARM_MINUTES.forEach { (minutes, label) ->
            Button(
                onClick = { viewModel.scheduleIn(minutes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(label)) }
        }
        PendingLine(state)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = viewModel::cancel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.alarm_spike_cancel)) }
            OutlinedButton(
                onClick = viewModel::clearLog,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.spike_clear_log)) }
        }
        Text(
            text = stringResource(R.string.spike_log_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PendingLine(state: AlarmSpikeUiState) {
    val text = when {
        state.exactAlarmsDenied -> stringResource(R.string.alarm_spike_exact_denied)
        state.pending != null -> stringResource(
            R.string.alarm_spike_pending,
            state.pending.kind.name,
            PENDING_TIME_FORMAT.format(Instant.ofEpochMilli(state.pending.triggerAtMillis)),
        )
        else -> stringResource(R.string.alarm_spike_none_pending)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (state.exactAlarmsDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}
