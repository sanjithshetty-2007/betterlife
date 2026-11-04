package com.sanjith.betterlife.feature.tasks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.OffsetDescription
import com.sanjith.betterlife.domain.popup.OffsetUnit
import com.sanjith.betterlife.domain.popup.describeOffset
import com.sanjith.betterlife.domain.reminder.ReminderOffsets
import com.sanjith.betterlife.domain.task.ReminderPlan
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueSection(state: TaskEditorState, dueText: DueText, today: LocalDate, onDate: (LocalDate?) -> Unit, onTime: (LocalTime?) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.editor_due))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(state.dueDate?.let { dueText.day(it, today) } ?: stringResource(R.string.editor_no_date))
        }
        if (state.dueDate != null) {
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                Text(state.dueTime?.let(dueText::time) ?: stringResource(R.string.editor_all_day))
            }
            SmallTextButton(stringResource(R.string.editor_clear)) { onDate(null) }
        }
    }
    if (pickDate) {
        // The date picker works in UTC midnights.
        val picker = rememberDatePickerState(initialSelectedDateMillis = (state.dueDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = picker) }
    }
    if (pickTime) {
        val initial = state.dueTime ?: LocalTime.of(DEFAULT_HOUR, 0)
        val picker = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { onTime(LocalTime.of(picker.hour, picker.minute)); pickTime = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { onTime(null); pickTime = false }) { Text(stringResource(R.string.editor_all_day)) } },
            text = { TimePicker(state = picker) },
        )
    }
}

private const val DEFAULT_HOUR = 9

@Composable
internal fun ProjectSection(projectId: String?, projects: List<ProjectEntity>, onPick: (String?) -> Unit, onCreate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.editor_project))
    OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
        Text(projects.firstOrNull { it.id == projectId }?.name ?: stringResource(R.string.editor_no_project))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.editor_no_project)) }, onClick = { onPick(null); open = false })
        projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { onPick(p.id); open = false }) }
        DropdownMenuItem(text = { Text(stringResource(R.string.editor_new_project)) }, onClick = { creating = true; open = false })
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.editor_new_project)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onCreate(name); creating = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun RepeatSection(state: TaskEditorState, onChange: ((TaskEditorState) -> TaskEditorState) -> Unit) {
    SectionLabel(stringResource(R.string.editor_repeat))
    Text(stringResource(R.string.editor_repeat_vs_habit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        RepeatKind.entries.forEach { kind ->
            FilterChip(selected = state.repeat == kind, onClick = { onChange { it.copy(repeat = kind) } }, label = { Text(stringResource(repeatLabel(kind))) })
        }
    }
    if (EditorError.REPEAT_NEEDS_DATE in state.errors()) {
        Text(stringResource(R.string.editor_repeat_needs_date), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (state.repeat == RepeatKind.NONE || state.repeat == RepeatKind.WEEKDAYS) return
    OutlinedTextField(
        value = state.interval.toString(),
        onValueChange = { v -> v.toIntOrNull()?.takeIf { it in 1..MAX_INTERVAL }?.let { n -> onChange { it.copy(interval = n) } } },
        label = { Text(stringResource(R.string.editor_every)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(Dimens.numberFieldWidth),
    )
    if (state.repeat == RepeatKind.WEEKLY) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in state.weekdays,
                    onClick = { onChange { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day) } },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }
    }
    Row(
        modifier = Modifier.heightIn(min = Dimens.minTouchTarget)
            .toggleable(state.repeatFromCompletion, role = Role.Switch) { v -> onChange { it.copy(repeatFromCompletion = v) } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.editor_repeat_from_completion), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
        Switch(checked = state.repeatFromCompletion, onCheckedChange = null)
    }
}

private const val MAX_INTERVAL = 99

private fun repeatLabel(kind: RepeatKind): Int = when (kind) {
    RepeatKind.NONE -> R.string.repeat_none
    RepeatKind.DAILY -> R.string.repeat_daily
    RepeatKind.WEEKDAYS -> R.string.repeat_weekdays
    RepeatKind.WEEKLY -> R.string.repeat_weekly
    RepeatKind.MONTHLY -> R.string.repeat_monthly
    RepeatKind.YEARLY -> R.string.repeat_yearly
}

/** Early reminders: several at once, presets or custom minutes, each with its own level. */
@Composable
internal fun RemindersSection(reminders: List<ReminderPlan>, defaultLevel: Importance, onChange: (List<ReminderPlan>) -> Unit) {
    SectionLabel(stringResource(R.string.editor_reminders))
    reminders.sortedByDescending { it.offsetMinutes }.forEach { plan ->
        var levelMenu by remember(plan.offsetMinutes) { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(offsetText(plan.offsetMinutes), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = { levelMenu = true }) { Text(stringResource(levelLabel(plan.importance))) }
            DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                Importance.entries.forEach { level ->
                    DropdownMenuItem(text = { Text(stringResource(levelLabel(level))) }, onClick = {
                        onChange(reminders.map { if (it == plan) it.copy(importance = level) else it })
                        levelMenu = false
                    })
                }
            }
            SmallTextButton(stringResource(R.string.editor_remove)) { onChange(reminders - plan) }
        }
    }
    val used = reminders.map { it.offsetMinutes }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ReminderOffsets.PRESETS.filter { it !in used }.forEach { offset ->
            FilterChip(selected = false, onClick = { onChange(reminders + ReminderPlan(offset, defaultLevel)) }, label = { Text(offsetText(offset)) })
        }
    }
    var custom by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.filter(Char::isDigit).take(MAX_CUSTOM_DIGITS) },
            label = { Text(stringResource(R.string.editor_custom_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = {
                custom.toIntOrNull()?.takeIf { it !in used }?.let { onChange(reminders + ReminderPlan(it, defaultLevel)) }
                custom = ""
            },
            enabled = custom.isNotEmpty(),
            modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.editor_add)) }
    }
}

private const val MAX_CUSTOM_DIGITS = 5

@Composable
private fun offsetText(minutes: Int): String = when (val d = describeOffset(minutes)) {
    OffsetDescription.AtTime -> stringResource(R.string.why_rule_at_time)
    is OffsetDescription.Before -> pluralStringResource(
        when (d.unit) {
            OffsetUnit.MINUTES -> R.plurals.why_minutes_before
            OffsetUnit.HOURS -> R.plurals.why_hours_before
            OffsetUnit.DAYS -> R.plurals.why_days_before
            OffsetUnit.WEEKS -> R.plurals.why_weeks_before
        },
        d.amount,
        d.amount,
    )
}
