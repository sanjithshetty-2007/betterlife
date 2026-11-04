package com.sanjith.betterlife.feature.tasks

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/** The full task editor (context.md 5.3). Saving updates the task, its reminders and the alarms. */
@Composable
public fun TaskEditorScreen(
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = viewModel(factory = TaskEditorViewModel.Factory),
) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) { if (saved) onClose() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            EditorHeader(isNew = state.id == null, canSave = state.errors().isEmpty(), onClose = onClose, onSave = viewModel::save)
            OutlinedTextField(
                value = state.title,
                onValueChange = { v -> viewModel.edit { it.copy(title = v) } },
                label = { Text(stringResource(R.string.editor_title)) },
                isError = EditorError.EMPTY_TITLE in state.errors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.edit { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.editor_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            DueSection(state, dueText, container.clock.today(), viewModel::setDate, viewModel::setTime)
            PrioritySection(state.priority) { p -> viewModel.edit { it.copy(priority = p) } }
            ProjectSection(state.projectId, projectList, onPick = { id -> viewModel.edit { it.copy(projectId = id) } }, onCreate = viewModel::createProject)
            OutlinedTextField(
                value = state.category,
                onValueChange = { v -> viewModel.edit { it.copy(category = v) } },
                label = { Text(stringResource(R.string.editor_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RepeatSection(state) { change -> viewModel.edit(change) }
            LevelSection(state.importance) { level -> viewModel.edit { it.copy(importance = level) } }
            RemindersSection(state.reminders, defaultLevel = state.importance) { list -> viewModel.edit { it.copy(reminders = list) } }
            SubtasksSection(state, onAdd = viewModel::addSubtask, onComplete = viewModel::completeSubtask, onDelete = viewModel::deleteSubtask)
        }
    }
}

@Composable
private fun EditorHeader(isNew: Boolean, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = stringResource(if (isNew) R.string.task_new else R.string.editor_edit_title),
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
internal fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun PrioritySection(priority: Priority, onPick: (Priority) -> Unit) {
    SectionLabel(stringResource(R.string.editor_priority))
    val options = listOf(Priority.NONE, Priority.LOW, Priority.MEDIUM, Priority.HIGH)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, p ->
            SegmentedButton(selected = p == priority, onClick = { onPick(p) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(stringResource(priorityLabel(p)), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun LevelSection(level: Importance, onPick: (Importance) -> Unit) {
    SectionLabel(stringResource(R.string.editor_level))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Importance.entries.forEachIndexed { i, l ->
            SegmentedButton(selected = l == level, onClick = { onPick(l) }, shape = SegmentedButtonDefaults.itemShape(i, Importance.entries.size)) {
                Text(stringResource(levelLabel(l)), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SubtasksSection(state: TaskEditorState, onAdd: (String) -> Unit, onComplete: (String) -> Unit, onDelete: (String) -> Unit) {
    SectionLabel(stringResource(R.string.editor_subtasks))
    if (state.id == null) {
        Text(stringResource(R.string.editor_subtasks_save_first), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    state.subtasks.forEach { sub ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = sub.done, onCheckedChange = { if (it && !sub.done) onComplete(sub.id) })
            Text(sub.title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = { onDelete(sub.id) }) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.task_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    var newSub by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(value = newSub, onValueChange = { newSub = it }, singleLine = true, modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.editor_subtask_hint)) })
        OutlinedButton(onClick = { onAdd(newSub); newSub = "" }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.editor_add))
        }
    }
}

internal fun levelLabel(level: Importance): Int = when (level) {
    Importance.NORMAL -> R.string.level_normal
    Importance.FULL_SCREEN -> R.string.level_full_screen
    Importance.ALARM -> R.string.level_alarm
}

@Composable
internal fun SmallTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(text) }
}
