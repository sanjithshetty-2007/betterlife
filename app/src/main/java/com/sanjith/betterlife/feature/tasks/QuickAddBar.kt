package com.sanjith.betterlife.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.parser.AmPm
import com.sanjith.betterlife.domain.parser.QuickAddResult
import com.sanjith.betterlife.domain.parser.QuickAddWarning
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

/** Test tag of the quick-add text field. */
public const val QUICK_ADD_FIELD_TAG: String = "quick-add-field"

/**
 * One-line task entry with the always-visible preview (context.md 5.3): what the parser understood as
 * chips, its warnings, and an inline AM/PM question when the time is unclear. Add is enabled only when
 * the text is ready to save.
 */
@Composable
public fun QuickAddBar(
    text: String,
    preview: QuickAddResult?,
    formatDate: (LocalDate) -> String,
    formatTime: (LocalTime) -> String,
    onText: (String) -> Unit,
    onAmPm: (AmPm) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.weight(1f).testTag(QUICK_ADD_FIELD_TAG),
            )
            Button(
                onClick = onSubmit,
                enabled = preview?.isReadyToSave == true,
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.quick_add_button)) }
        }
        preview?.let { PreviewDetails(it, formatDate, formatTime, onAmPm) }
    }
}

@Composable
private fun PreviewDetails(
    preview: QuickAddResult,
    formatDate: (LocalDate) -> String,
    formatTime: (LocalTime) -> String,
    onAmPm: (AmPm) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        preview.date?.let { PreviewChip(stringResource(R.string.chip_date, formatDate(it))) }
            ?: PreviewChip(stringResource(R.string.chip_inbox))
        preview.time?.let { PreviewChip(stringResource(R.string.chip_time, formatTime(it))) }
        if (preview.priority != Priority.NONE) PreviewChip(stringResource(priorityLabel(preview.priority)))
        preview.tags.forEach { PreviewChip("#$it") }
        preview.project?.let { PreviewChip("@$it") }
    }
    preview.warnings.forEach { warning ->
        Text(
            text = stringResource(warningText(warning)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    preview.question?.let { question ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(R.string.quick_add_am_pm, question.hour, question.minute),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onAmPm(AmPm.AM) }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.am))
            }
            OutlinedButton(onClick = { onAmPm(AmPm.PM) }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.pm))
            }
        }
    }
}

@Composable
private fun PreviewChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

private fun warningText(warning: QuickAddWarning): Int = when (warning) {
    QuickAddWarning.MOVED_TO_TOMORROW -> R.string.warning_moved_to_tomorrow
    QuickAddWarning.MOVED_TO_NEXT_YEAR -> R.string.warning_moved_to_next_year
    QuickAddWarning.TIME_ALREADY_PASSED -> R.string.warning_time_passed
    QuickAddWarning.EMPTY_TITLE -> R.string.warning_empty_title
}

internal fun priorityLabel(priority: Priority): Int = when (priority) {
    Priority.HIGH -> R.string.priority_high
    Priority.MEDIUM -> R.string.priority_medium
    Priority.LOW -> R.string.priority_low
    Priority.NONE -> R.string.priority_none
}
