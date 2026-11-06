package com.sanjith.betterlife.feature.celebration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.db.entity.CelebrationMessageEntity
import com.sanjith.betterlife.domain.habit.MilestoneDetector
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The owner's celebration lines (Prompt 16): add, edit, delete; each for any milestone or one
 * milestone. The built-in lines are shown below and used while there are none. Blip's name lives here
 * until Settings exists (Prompt 27).
 */
@Composable
public fun CelebrationMessagesScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val messages by remember { container.celebrationMessages.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CelebrationMessageEntity?>(null) }

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
                    Text(stringResource(R.string.celebrations_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            settings?.let { s -> item { MascotNameRow(s.mascotName) { name -> scope.launch { container.settingsRepository.update { it.copy(mascotName = name) } } } } }
            item { SectionLabel(stringResource(R.string.celebrations_yours)) }
            item { AddRow { text, milestone -> scope.launch { container.celebrationMessages.add(text, milestone) } } }
            if (messages.isEmpty()) {
                item { Text(stringResource(R.string.celebrations_none_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(messages, key = { it.id }) { message ->
                MessageCard(
                    text = message.text,
                    milestone = message.milestone,
                    onEdit = { editing = message },
                    onDelete = { scope.launch { container.celebrationMessages.delete(message.id) } },
                )
            }
            item { SectionLabel(stringResource(R.string.celebrations_built_in)) }
            item { Text(stringResource(R.string.celebrations_built_in_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    stringArrayResource(R.array.celebration_default_messages).forEach { line ->
                        Text(stringResource(R.string.celebration_quote, line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
    editing?.let { message ->
        EditDialog(message, onDismiss = { editing = null }) { text, milestone ->
            editing = null
            scope.launch { container.celebrationMessages.update(message.id, text, milestone) }
        }
    }
}

@Composable
private fun MascotNameRow(current: String, onSave: (String) -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.replace("\n", "") },
            singleLine = true,
            label = { Text(stringResource(R.string.mascot_name)) },
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onSave(name.trim()) },
            enabled = name.isNotBlank() && name.trim() != current,
            modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.editor_save)) }
    }
}

@Composable
private fun AddRow(onAdd: (String, Int?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var milestone by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace("\n", "") },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.celebrations_new_hint)) },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAdd(text, milestone)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.editor_add)) }
        }
        MilestoneChips(milestone) { milestone = it }
    }
}

/** "Any", 7, 10, 15, 30. */
@Composable
private fun MilestoneChips(selected: Int?, onPick: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text(stringResource(R.string.celebrations_any_milestone)) })
        MilestoneDetector.MILESTONES.forEach { m ->
            FilterChip(selected = selected == m, onClick = { onPick(m) }, label = { Text(stringResource(R.string.celebrations_milestone_days, m)) })
        }
    }
}

@Composable
private fun MessageCard(text: String, milestone: Int?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = milestone?.let { stringResource(R.string.celebrations_milestone_days, it) } ?: stringResource(R.string.celebrations_any_milestone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.celebrations_edit), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.celebrations_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditDialog(message: CelebrationMessageEntity, onDismiss: () -> Unit, onSave: (String, Int?) -> Unit) {
    var text by remember { mutableStateOf(message.text) }
    var milestone by remember { mutableStateOf(message.milestone) }
    LaunchedEffect(message.id) { text = message.text }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.celebrations_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(value = text, onValueChange = { text = it.replace("\n", "") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MilestoneChips(milestone) { milestone = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, milestone) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
