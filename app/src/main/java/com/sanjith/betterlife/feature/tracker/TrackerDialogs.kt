package com.sanjith.betterlife.feature.tracker

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.tracker.TrackerKind
import java.time.LocalTime

/** The amount boxes in a dialog (an amount, or the target). Save is off until [parse] accepts them. */
@Composable
internal fun AmountDialog(
    kind: TrackerKind,
    title: String,
    initial: Int?,
    parse: (TrackerKind, String, String) -> Int?,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (startFirst, startSecond) = splitAmount(kind, initial)
    var first by rememberSaveable { mutableStateOf(startFirst) }
    var second by rememberSaveable { mutableStateOf(startSecond) }
    val value = parse(kind, first, second)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AmountFields(kind, first, { first = it }, second, { second = it }, stringResource(R.string.protein_amount_hint), onDone = { value?.let(onSave) })
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.tracker_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The reminder time picker, in the phone's 12 or 24 hour format (as the nudge times on Check-ins). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = picker) },
    )
}
