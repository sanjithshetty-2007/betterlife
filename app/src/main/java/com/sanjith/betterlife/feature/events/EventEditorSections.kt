package com.sanjith.betterlife.feature.events

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.event.EventCategory
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.feature.tasks.RepeatKind
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** A button showing [text] that opens a date picker starting at [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateButton(text: String, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(text) }
    if (!open) return
    // The date picker works in UTC midnights.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = picker) }
}

/** A button showing [text] that opens a time picker; [clearLabel] adds a button that picks "no time". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeButton(text: String, initial: LocalTime, onPick: (LocalTime?) -> Unit, clearLabel: String? = null) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(text) }
    if (!open) return
    val picker = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)); open = false }) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            TextButton(onClick = { if (clearLabel != null) onPick(null); open = false }) {
                Text(clearLabel ?: stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state = picker) },
    )
}

@Composable
internal fun TypeSection(type: EventType, onPick: (EventType) -> Unit) {
    SectionLabel(stringResource(R.string.event_type))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        EventType.entries.forEachIndexed { i, t ->
            SegmentedButton(selected = t == type, onClick = { onPick(t) }, shape = SegmentedButtonDefaults.itemShape(i, EventType.entries.size)) {
                Text(stringResource(kindLabel(t)), maxLines = 1)
            }
        }
    }
}

internal fun kindLabel(type: EventType): Int = when (type) {
    EventType.EVENT -> R.string.agenda_kind_event
    EventType.MEETING -> R.string.agenda_kind_meeting
    EventType.BIRTHDAY -> R.string.agenda_kind_birthday
}

/** A round colour swatch; the category name is always shown next to it, never colour alone. */
@Composable
internal fun CategoryDot(colorIndex: Int?, modifier: Modifier = Modifier) {
    val palette = BetterLifeColors.CategoryPalette
    val color = colorIndex?.let { palette[it % palette.size] } ?: MaterialTheme.colorScheme.outline
    Box(modifier.size(Dimens.categoryDot).background(color, CircleShape))
}

/** Pick one of the owner's colour categories, or make a new one with a name and a colour. */
@Composable
internal fun CategorySection(selected: String?, categories: List<EventCategory>, onPick: (String?) -> Unit, onCreate: (EventCategory) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.event_category))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text(stringResource(R.string.event_no_category)) })
        categories.forEach { c ->
            FilterChip(
                selected = selected.equals(c.name, ignoreCase = true),
                onClick = { onPick(c.name) },
                label = { Text(c.name) },
                leadingIcon = { CategoryDot(c.colorIndex) },
            )
        }
        FilterChip(selected = false, onClick = { creating = true }, label = { Text(stringResource(R.string.event_new_category)) })
    }
    if (creating) NewCategoryDialog(onDismiss = { creating = false }, onCreate = { onCreate(it); creating = false })
}

@Composable
private fun NewCategoryDialog(onDismiss: () -> Unit, onCreate: (EventCategory) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_new_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(value = name, onValueChange = { name = it.replace("\n", "") }, singleLine = true,
                    label = { Text(stringResource(R.string.event_category_name)) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BetterLifeColors.CategoryPalette.indices.forEach { i ->
                        val isPicked = i == colorIndex
                        val description = stringResource(R.string.event_color_n, i + 1)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(Dimens.minTouchTarget)
                                .semantics { selected = isPicked; contentDescription = description }
                                .clickable(role = Role.RadioButton) { colorIndex = i }
                                .then(if (isPicked) Modifier.border(Dimens.borderWidth * 2, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
                        ) { CategoryDot(i, Modifier.size(Dimens.iconSize)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(EventCategory(name.trim(), colorIndex)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Repeat for events: the task kinds without "repeat from completion" (events are not completed). */
@Composable
internal fun EventRepeatSection(state: EventEditorState, onChange: ((EventEditorState) -> EventEditorState) -> Unit) {
    SectionLabel(stringResource(R.string.editor_repeat))
    Text(stringResource(R.string.event_repeat_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        RepeatKind.entries.forEach { kind ->
            FilterChip(selected = state.repeat == kind, onClick = { onChange { it.copy(repeat = kind) } }, label = { Text(stringResource(repeatLabel(kind))) })
        }
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

/** A switch row with the whole row as the touch target, like the task editor's. */
@Composable
internal fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
        Switch(checked = checked, onCheckedChange = null)
    }
}
