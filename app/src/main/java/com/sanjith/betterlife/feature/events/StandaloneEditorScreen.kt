package com.sanjith.betterlife.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.feature.tasks.RemindersSection
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/** The standalone "remind me" editor: what, when, and early reminders. Same layout as the event editor. */
@Composable
public fun StandaloneEditorScreen(
    onClose: () -> Unit,
    viewModel: StandaloneEditorViewModel = viewModel(factory = StandaloneEditorViewModel.Factory),
) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onClose() }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = container.clock.today()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            EditorHeader(
                stringResource(if (state.id == null) R.string.remind_new else R.string.remind_edit),
                canSave = state.canSave,
                onClose = onClose,
                onSave = viewModel::save,
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = { v -> viewModel.edit { it.copy(title = v) } },
                label = { Text(stringResource(R.string.remind_what)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.remind_when))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                DateButton(dueText.day(state.date, today), state.date) { d -> viewModel.edit { it.copy(date = d) } }
                TimeButton(dueText.time(state.time), state.time, onPick = { t -> t?.let { viewModel.edit { s -> s.copy(time = it) } } })
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.edit { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.editor_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            RemindersSection(state.reminders, defaultLevel = Importance.FULL_SCREEN, onChange = viewModel::setReminders)
            if (state.done) {
                Text(stringResource(R.string.remind_is_done), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.id != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (!state.done) {
                        OutlinedButton(onClick = viewModel::markDone, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                            Text(stringResource(R.string.remind_done))
                        }
                    }
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                        Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteDialog(state.title, onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; viewModel.delete() })
    }
}
