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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.feature.tasks.LevelSection
import com.sanjith.betterlife.feature.tasks.RemindersSection
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.feature.tasks.SmallTextButton
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The event, meeting and birthday editor (context.md 5.2). Same layout as the task editor. */
@Composable
public fun EventEditorScreen(
    onClose: () -> Unit,
    viewModel: EventEditorViewModel = viewModel(factory = EventEditorViewModel.Factory),
) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    LaunchedEffect(finished) { if (finished) onClose() }
    val today = viewModel.today()
    val errors = state.errors(today)
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val title = when {
                state.isBirthday -> if (state.id == null) R.string.birthday_new else R.string.birthday_edit
                else -> if (state.id == null) R.string.event_new else R.string.event_edit
            }
            EditorHeader(stringResource(title), canSave = errors.isEmpty(), onClose = onClose, onSave = viewModel::save)
            TypeSection(state.type, viewModel::setType)
            OutlinedTextField(
                value = state.title,
                onValueChange = { v -> viewModel.edit { it.copy(title = v) } },
                label = { Text(stringResource(if (state.isBirthday) R.string.event_name else R.string.editor_title)) },
                isError = EventEditorError.EMPTY_TITLE in errors,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isBirthday) {
                BirthdaySection(state, viewModel, settings?.birthdayFireTime?.let(dueText::time), errors)
            } else {
                WhenSection(state, viewModel, dueText, today, errors)
                OutlinedTextField(
                    value = state.location,
                    onValueChange = { v -> viewModel.edit { it.copy(location = v) } },
                    label = { Text(stringResource(R.string.event_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.edit { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.editor_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            CategorySection(state.colorCategory, categories, onPick = { name -> viewModel.edit { it.copy(colorCategory = name) } }, onCreate = viewModel::addCategory)
            if (!state.isBirthday) EventRepeatSection(state) { change -> viewModel.edit(change) }
            LevelSection(state.importance) { level -> viewModel.edit { it.copy(importance = level) } }
            RemindersSection(state.reminders, defaultLevel = state.importance, onChange = viewModel::setReminders)
            if (state.id != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteDialog(state.title, onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; viewModel.delete() })
    }
}

@Composable
private fun WhenSection(state: EventEditorState, viewModel: EventEditorViewModel, dueText: DueText, today: java.time.LocalDate, errors: List<EventEditorError>) {
    SectionLabel(stringResource(R.string.event_when))
    SwitchRow(stringResource(R.string.event_all_day), state.allDay) { v -> viewModel.edit { it.copy(allDay = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        DateButton(dueText.day(state.date, today), state.date) { d -> viewModel.edit { it.copy(date = d) } }
        if (!state.allDay) {
            TimeButton(dueText.time(state.startTime), state.startTime, onPick = { t -> t?.let { viewModel.edit { s -> s.withStart(it) } } })
        }
    }
    if (state.allDay) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.event_end_date), color = MaterialTheme.colorScheme.onBackground)
            DateButton(state.endDate?.let { dueText.day(it, today) } ?: stringResource(R.string.event_one_day), state.endDate ?: state.date) { d ->
                viewModel.edit { it.copy(endDate = d) }
            }
            if (state.endDate != null) SmallTextButton(stringResource(R.string.editor_clear)) { viewModel.edit { it.copy(endDate = null) } }
        }
        if (EventEditorError.END_BEFORE_START in errors) ErrorText(stringResource(R.string.event_end_before_start))
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.event_ends), color = MaterialTheme.colorScheme.onBackground)
            TimeButton(
                text = state.endTime?.let(dueText::time) ?: stringResource(R.string.event_no_end),
                initial = state.endTime ?: state.startTime.plusHours(1),
                onPick = { t -> viewModel.edit { it.copy(endTime = t) } },
                clearLabel = stringResource(R.string.event_no_end),
            )
        }
        if (state.endTime != null && state.endTime.isBefore(state.startTime)) {
            Text(stringResource(R.string.event_ends_next_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BirthdaySection(state: EventEditorState, viewModel: EventEditorViewModel, fireTime: String?, errors: List<EventEditorError>) {
    SectionLabel(stringResource(R.string.event_birthday_date))
    val pattern = if (state.yearUnknown) DateTimeFormatter.ofPattern("d MMMM") else DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    DateButton(state.date.format(pattern), state.date) { d -> viewModel.edit { it.copy(date = d) } }
    SwitchRow(stringResource(R.string.event_year_unknown), state.yearUnknown) { v -> viewModel.edit { it.copy(yearUnknown = v) } }
    if (EventEditorError.BIRTH_YEAR_INVALID in errors) ErrorText(stringResource(R.string.event_birth_year_invalid))
    Text(stringResource(R.string.event_birthday_repeat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    fireTime?.let {
        Text(stringResource(R.string.event_birthday_time, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun EditorHeader(title: String, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.editor_save))
        }
    }
}

@Composable
internal fun DeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_delete_confirm_title, title)) },
        text = { Text(stringResource(R.string.event_delete_confirm_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
