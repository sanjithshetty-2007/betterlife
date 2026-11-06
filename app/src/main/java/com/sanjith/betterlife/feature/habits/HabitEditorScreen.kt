package com.sanjith.betterlife.feature.habits

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.habit.HabitCategory
import com.sanjith.betterlife.feature.events.CategoryDot
import com.sanjith.betterlife.feature.events.EditorHeader
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.feature.events.TimeButton
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalTime

/** Create or edit a habit (Prompt 15). */
@Composable
public fun HabitEditorScreen(onClose: () -> Unit, viewModel: HabitEditorViewModel = viewModel(factory = HabitEditorViewModel.Factory)) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
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
                stringResource(if (state.id == null) R.string.habit_new else R.string.habit_edit),
                canSave = state.canSave,
                onClose = onClose,
                onSave = viewModel::save,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HabitAvatar(state.avatar, habitColor(state.colorHex), large = true) }
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.edit { it.copy(name = v.replace("\n", "")) } },
                label = { Text(stringResource(R.string.habit_name)) },
                singleLine = true,
                isError = !state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
            ColorSection(state.colorHex) { hex -> viewModel.edit { it.copy(colorHex = hex) } }
            AvatarSection(state.avatar) { avatar -> viewModel.edit { it.copy(avatar = avatar) } }
            CategorySection(state.category) { category -> viewModel.edit { it.copy(category = category) } }
            SwitchRow(stringResource(R.string.habit_essential), state.essential) { v -> viewModel.edit { it.copy(essential = v) } }
            Text(stringResource(R.string.habit_essential_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel(stringResource(R.string.habit_daily_reminder))
            TimeButton(
                text = state.reminderTime?.let(dueText::time) ?: stringResource(R.string.habit_no_reminder),
                initial = state.reminderTime ?: DEFAULT_REMINDER,
                onPick = { t -> viewModel.edit { it.copy(reminderTime = t) } },
                clearLabel = stringResource(R.string.habit_no_reminder),
            )
            if (state.id != null) {
                TextButton(onClick = viewModel::toggleArchived, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(if (state.archived) R.string.habit_unarchive else R.string.habit_archive))
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.habit_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.habit_delete_confirm_title, state.name)) },
            text = { Text(stringResource(R.string.habit_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.habit_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val DEFAULT_REMINDER: LocalTime = LocalTime.of(21, 0)

/** The palette as round swatches; one 48 dp radio cell each, written like the event category dialog's. */
@Composable
private fun ColorSection(selected: String, onPick: (String) -> Unit) {
    SectionLabel(stringResource(R.string.habit_color))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        BetterLifeColors.CategoryPalette.indices.forEach { i ->
            val hex = paletteHex(i)
            val isPicked = hex.equals(selected, ignoreCase = true)
            val description = stringResource(R.string.event_color_n, i + 1)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(Dimens.minTouchTarget)
                    .semantics { this.selected = isPicked; contentDescription = description }
                    .clickable(role = Role.RadioButton) { onPick(hex) }
                    .then(if (isPicked) Modifier.border(Dimens.borderWidth * 2, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
            ) {
                CategoryDot(i, Modifier.size(Dimens.iconSize))
            }
        }
    }
}

@Composable
private fun AvatarSection(selected: String, onPick: (String) -> Unit) {
    SectionLabel(stringResource(R.string.habit_icon))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HabitAvatars.ALL.forEach { avatar ->
            val isPicked = avatar == selected
            val description = stringResource(R.string.habit_icon_n, avatar)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(Dimens.minTouchTarget)
                    .semantics { this.selected = isPicked; contentDescription = description }
                    .clickable(role = Role.RadioButton) { onPick(avatar) }
                    .then(if (isPicked) Modifier.border(Dimens.borderWidth * 2, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
            ) { Text(avatar, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun CategorySection(selected: HabitCategory, onPick: (HabitCategory) -> Unit) {
    SectionLabel(stringResource(R.string.habit_category))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HabitCategory.entries.forEach { category ->
            FilterChip(selected = category == selected, onClick = { onPick(category) }, label = { Text(stringResource(categoryLabel(category))) })
        }
    }
}

internal fun categoryLabel(category: HabitCategory): Int = when (category) {
    HabitCategory.HEALTH -> R.string.habit_category_health
    HabitCategory.SELF_CARE -> R.string.habit_category_self_care
    HabitCategory.OTHER -> R.string.habit_category_other
}
