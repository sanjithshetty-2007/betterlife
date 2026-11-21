package com.sanjith.betterlife.feature.silent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.silent.SilentRules
import com.sanjith.betterlife.domain.silent.VibrationPattern
import com.sanjith.betterlife.feature.events.DeleteDialog
import com.sanjith.betterlife.feature.events.EditorHeader
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.feature.events.TimeButton
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.ui.theme.pillColors
import java.time.LocalTime

/** One silent reminder: what to remember, its daily times, the vibration and its length (same layout as the other editors). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun SilentEditorScreen(onClose: () -> Unit, viewModel: SilentEditorViewModel = viewModel(factory = SilentEditorViewModel.Factory)) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember(context) { DueText(context) { container.clock.zone } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onClose() }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            EditorHeader(
                stringResource(if (state.id == null) R.string.silent_new else R.string.silent_edit),
                canSave = state.canSave,
                onClose = onClose,
                onSave = viewModel::save,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.edit { it.copy(name = v) } },
                label = { Text(stringResource(R.string.silent_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.silent_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionLabel(stringResource(R.string.silent_times))
            if (state.times.isEmpty()) {
                Text(stringResource(R.string.silent_times_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.times.forEach { time ->
                    val text = dueText.time(time)
                    val remove = stringResource(R.string.silent_remove_time, text)
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeTime(time) },
                        label = { Text(text) },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(Dimens.smallIconSize)) },
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget).semantics { contentDescription = remove },
                    )
                }
            }
            TimeButton(stringResource(R.string.silent_add_time), state.times.lastOrNull()?.plusHours(1) ?: DEFAULT_TIME, onPick = { t -> t?.let(viewModel::addTime) })

            SectionLabel(stringResource(R.string.silent_pattern))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                VibrationPattern.entries.forEach { p ->
                    FilterChip(
                        selected = state.pattern == p,
                        onClick = { viewModel.edit { it.copy(pattern = p) } },
                        label = { Text(stringResource(patternLabel(p))) },
                    )
                }
            }
            OutlinedButton(onClick = viewModel::tryPattern, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(stringResource(R.string.silent_try)) }

            SectionLabel(stringResource(R.string.silent_length))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SilentRules.LENGTHS_SECONDS.forEachIndexed { i, seconds ->
                    SegmentedButton(
                        selected = state.seconds == seconds,
                        onClick = { viewModel.edit { it.copy(seconds = seconds) } },
                        shape = SegmentedButtonDefaults.itemShape(i, SilentRules.LENGTHS_SECONDS.size),
                        colors = pillColors(),
                    ) { Text(stringResource(R.string.silent_seconds, seconds)) }
                }
            }

            SwitchRow(stringResource(R.string.silent_enabled), state.enabled) { on -> viewModel.edit { it.copy(enabled = on) } }
            if (state.id != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteDialog(state.name, onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; viewModel.delete() })
    }
}

private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
