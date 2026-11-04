package com.sanjith.betterlife.feature.tasks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.settings.TopTasksStore
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalTime

/** Morning check-in, evening wrap-up and their nudge settings on one screen. */
@Composable
public fun CheckInScreen(onBack: () -> Unit, viewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(stringResource(R.string.check_ins_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            item { SectionLabel(stringResource(R.string.morning_title, state.top.size, TopTasksStore.MAX_TOP)) }
            if (state.open.isEmpty()) item { Body(stringResource(R.string.morning_empty)) }
            items(state.open, key = { "top-${it.id}" }) { row ->
                val picked = row.id in state.top
                Row(
                    modifier = Modifier.heightIn(min = Dimens.minTouchTarget)
                        .toggleable(picked, role = Role.Checkbox, enabled = picked || state.top.size < TopTasksStore.MAX_TOP) { viewModel.toggleTop(row.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = picked, onCheckedChange = null)
                    Text(row.title, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
            item { SectionLabel(stringResource(R.string.evening_title)) }
            item { Body(stringResource(R.string.evening_done_today, state.doneToday)) }
            items(state.open, key = { "move-${it.id}" }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Body(row.title + (dueText.format(row.due, row.displayDate, container.clock.today())?.let { "  ·  $it" } ?: ""), Modifier.weight(1f))
                    SmallTextButton(stringResource(R.string.evening_tomorrow)) { viewModel.moveToTomorrow(row.id) }
                }
            }
            if (state.open.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = viewModel::moveAllToTomorrow, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                        Text(stringResource(R.string.evening_move_all))
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.nudges_title)) }
            item {
                NudgeRow(stringResource(R.string.nudge_morning_setting), state.settings.morningCheckInEnabled, state.settings.morningCheckInTime, dueText::time) { on, time ->
                    viewModel.setMorning(on, time)
                }
            }
            item {
                NudgeRow(stringResource(R.string.nudge_evening_setting), state.settings.eveningWrapUpEnabled, state.settings.eveningWrapUpTime, dueText::time) { on, time ->
                    viewModel.setEvening(on, time)
                }
            }
        }
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NudgeRow(label: String, enabled: Boolean, time: LocalTime, format: (LocalTime) -> String, onChange: (Boolean, LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
        TextButton(onClick = { picking = true }) { Text(format(time)) }
        Switch(checked = enabled, onCheckedChange = { onChange(it, time) })
    }
    if (picking) {
        val picker = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { onChange(enabled, LocalTime.of(picker.hour, picker.minute)); picking = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = picker) },
        )
    }
}
